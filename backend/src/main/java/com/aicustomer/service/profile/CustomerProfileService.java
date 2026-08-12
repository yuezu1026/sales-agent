package com.aicustomer.service.profile;

import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProfileSearchResult;
import com.aicustomer.entity.CustomerProfile;
import com.aicustomer.repository.CustomerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户画像服务（M2-3 RAG 客户画像）：
 * <ul>
 *   <li>CSV 导入画像（公司名唯一去重）→ 拼装特征文本 → 向量化存 embedding；</li>
 *   <li>列表 / 删除；</li>
 *   <li>语义检索：查询文本向量与画像库余弦相似度排序。</li>
 * </ul>
 */
@Service
public class CustomerProfileService {

    private static final Logger log = LoggerFactory.getLogger(CustomerProfileService.class);

    /** 特征文本长度上限（超长截断，控制向量计算成本） */
    private static final int MAX_FEATURE_TEXT = 500;

    private final CustomerProfileRepository repository;
    private final EmbeddingRouter router;

    public CustomerProfileService(CustomerProfileRepository repository, EmbeddingRouter router) {
        this.repository = repository;
        this.router = router;
    }

    /**
     * CSV 导入画像。
     * 列（首行表头，可含 UTF-8 BOM）：公司名称*、行业、联系人、邮箱、成交金额、标签、描述
     *
     * @return {success, duplicate, errors:[{companyName, reason}]}
     */
    @Transactional
    public Map<String, Object> importCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw BizException.badRequest("CSV 内容为空");
        }
        List<Map<String, String>> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            throw BizException.badRequest("未解析到有效数据行（首行应为表头：公司名称*、行业、...）");
        }
        int success = 0;
        int duplicate = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = row.getOrDefault("companyName", "").trim();
            if (name.isEmpty()) {
                errors.add(Map.of("companyName", "(空)", "reason", "公司名称不能为空"));
                continue;
            }
            if (repository.existsByCompanyNameIgnoreCase(name)) {
                duplicate++;
                continue; // 重复不视为错误，只计数量
            }
            try {
                CustomerProfile p = new CustomerProfile();
                p.setTenantId(com.aicustomer.common.TenantContext.require());
                p.setCompanyName(name);
                p.setIndustry(trimToNull(row.get("industry")));
                p.setContactName(trimToNull(row.get("contactName")));
                p.setContactEmail(trimToNull(row.get("contactEmail")));
                String dealValue = trimToNull(row.get("dealValue"));
                if (dealValue != null) {
                    try {
                        p.setDealValue(new BigDecimal(dealValue));
                    } catch (NumberFormatException e) {
                        errors.add(Map.of("companyName", name, "reason", "成交金额格式非法：" + dealValue));
                        continue;
                    }
                }
                p.setTags(trimToNull(row.get("tags")));
                p.setDescription(trimToNull(row.get("description")));
                p.setCreatedAt(LocalDateTime.now());

                // 特征文本 → 向量
                String featureText = buildFeatureText(p);
                p.setEmbedding(router.toJson(router.embed(featureText)));

                repository.save(p);
                success++;
            } catch (Exception e) {
                log.warn("画像导入失败: company={}, 异常={}", name, e.getMessage());
                errors.add(Map.of("companyName", name, "reason", e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("duplicate", duplicate);
        result.put("errors", errors);
        return result;
    }

    /** 全量画像（id 倒序） */
    public List<CustomerProfile> list() {
        return repository.findAllByOrderByIdDesc();
    }

    @Transactional
    public void delete(Long id) {
        CustomerProfile p = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("画像不存在"));
        repository.delete(p);
    }

    /**
     * 语义检索：查询文本 → 向量 → 与全部画像余弦相似度，取 topN（默认 10，上限 20）
     */
    public List<ProfileSearchResult> search(String query, int topN) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int capped = topN > 0 ? Math.min(topN, 20) : 10;
        List<CustomerProfile> profiles = repository.findAll();
        if (profiles.isEmpty()) {
            return List.of();
        }
        List<Float> queryVec = router.embed(query);
        List<ProfileSearchResult> scored = new ArrayList<>();
        for (CustomerProfile p : profiles) {
            List<Float> pVec = router.fromJson(p.getEmbedding());
            double sim = router.cosine(queryVec, pVec);
            if (sim > 0) {
                scored.add(new ProfileSearchResult(p, sim));
            }
        }
        scored.sort(Comparator.comparingDouble(ProfileSearchResult::score).reversed());
        return scored.size() <= capped ? scored : scored.subList(0, capped);
    }

    /** 画像库中与给定向量最相似的画像（供 lead 打分复用） */
    public ProfileSearchResult topMatch(List<Float> queryVec, double minScore) {
        List<CustomerProfile> profiles = repository.findAll();
        ProfileSearchResult best = null;
        for (CustomerProfile p : profiles) {
            List<Float> pVec = router.fromJson(p.getEmbedding());
            double sim = router.cosine(queryVec, pVec);
            if (sim >= minScore && (best == null || sim > best.score())) {
                best = new ProfileSearchResult(p, sim);
            }
        }
        return best;
    }

    /** 特征文本：公司名 + 行业 + 标签 + 描述（截断） */
    public String buildFeatureText(CustomerProfile p) {
        StringBuilder sb = new StringBuilder();
        append(sb, p.getCompanyName());
        append(sb, p.getIndustry());
        append(sb, p.getTags());
        append(sb, p.getDescription());
        return sb.length() > MAX_FEATURE_TEXT ? sb.substring(0, MAX_FEATURE_TEXT) : sb.toString();
    }

    private void append(StringBuilder sb, String s) {
        if (StringUtils.hasText(s)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s.trim());
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 简单 CSV 解析（支持引号包裹与 UTF-8 BOM）。
     * 列顺序：公司名称*、行业、联系人、邮箱、成交金额、标签、描述
     */
    private List<Map<String, String>> parseCsv(String csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = csv.split("\r\n|\r|\n");
        boolean headerSkipped = false;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            if (l.startsWith("\uFEFF")) {
                l = l.substring(1);
            }
            if (!headerSkipped) {
                headerSkipped = true; // 跳过首行表头
                continue;
            }
            List<String> fields = splitLine(l);
            Map<String, String> row = new LinkedHashMap<>();
            String[] keys = {"companyName", "industry", "contactName", "contactEmail",
                    "dealValue", "tags", "description"};
            for (int i = 0; i < keys.length; i++) {
                row.put(keys[i], i < fields.size() ? fields.get(i).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    /** 单行拆分：支持 "..." 引号包裹（内含逗号/引号转义） */
    private List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
