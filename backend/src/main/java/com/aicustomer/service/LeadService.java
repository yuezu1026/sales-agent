package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.Lead;
import com.aicustomer.repository.LeadRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 潜客服务：CRUD + 状态流转 + 去重 + 统计
 * M2-1 客户管理 CRM
 */
@Service
public class LeadService {

    /** 状态流转合法路径：new → contacted → interested → converted / invalid */
    private static final Map<String, List<String>> STATUS_TRANSITIONS = Map.of(
            "new", List.of("contacted", "invalid"),
            "contacted", List.of("interested", "invalid"),
            "interested", List.of("converted", "invalid"),
            "converted", List.of(),
            "invalid", List.of()
    );

    private final LeadRepository leadRepository;

    public LeadService(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    /**
     * 分页 + 搜索 + 筛选
     */
    public Page<Lead> list(String keyword, String status, String industry, String sourceType, int page, int size) {
        Specification<Lead> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), TenantContext.require()));
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("companyName"), like),
                        cb.like(root.get("contactName"), like),
                        cb.like(root.get("contactEmail"), like),
                        cb.like(root.get("contactPhone"), like),
                        cb.like(root.get("wechatName"), like),
                        cb.like(root.get("wechatId"), like),
                        cb.like(root.get("wechatName"), like),
                        cb.like(root.get("wechatId"), like),
                        cb.like(root.get("industry"), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (StringUtils.hasText(industry)) {
                predicates.add(cb.equal(root.get("industry"), industry.trim()));
            }
            if (StringUtils.hasText(sourceType)) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return leadRepository.findAll(spec, pageable);
    }

    /**
     * 新增：company_name 必填；去重
     * - 带 source_id（api/csv 来源）→ source_type+source_id 唯一
     * - manual 来源 → company_name 忽略大小写判重
     */
    @Transactional
    public Lead create(Lead lead) {
        if (!StringUtils.hasText(lead.getCompanyName())) {
            throw BizException.badRequest("公司名称不能为空");
        }
        if (!StringUtils.hasText(lead.getContactName())) {
            throw BizException.badRequest("联系人不能为空");
        }
        if (!StringUtils.hasText(lead.getContactEmail())) {
            throw BizException.badRequest("邮箱不能为空");
        }
        lead.setCompanyName(lead.getCompanyName().trim());
        lead.setContactName(lead.getContactName().trim());
        lead.setContactEmail(lead.getContactEmail().trim());
        if (lead.getWechatId() != null) {
            lead.setWechatId(lead.getWechatId().trim());
        }
        if (lead.getWechatName() != null) {
            lead.setWechatName(lead.getWechatName().trim());
        }
        checkDuplicate(lead, null);
        if (lead.getSourceType() == null || lead.getSourceType().isBlank()) {
            lead.setSourceType("manual");
        }
        if (lead.getStatus() == null || lead.getStatus().isBlank()) {
            lead.setStatus("new");
        }
        lead.setTenantId(TenantContext.require());
        return leadRepository.save(lead);
    }

    /**
     * 编辑：仅更新可编辑字段，不改变状态来源
     */
    @Transactional
    public Lead update(Long id, Lead patch) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        if (StringUtils.hasText(patch.getCompanyName())) {
            lead.setCompanyName(patch.getCompanyName().trim());
        }
        lead.setContactName(blankToNull(patch.getContactName()));
        lead.setContactEmail(blankToNull(patch.getContactEmail()));
        lead.setContactPhone(blankToNull(patch.getContactPhone()));
        lead.setWechatId(blankToNull(patch.getWechatId()));
        lead.setWechatName(blankToNull(patch.getWechatName()));
        lead.setGender(blankToNull(patch.getGender()));
        lead.setIndustry(blankToNull(patch.getIndustry()));
        lead.setRegion(blankToNull(patch.getRegion()));
        lead.setScale(blankToNull(patch.getScale()));
        lead.setWebsite(blankToNull(patch.getWebsite()));
        lead.setAddress(blankToNull(patch.getAddress()));
        lead.setStockCode(blankToNull(patch.getStockCode()));
        lead.setNotes(blankToNull(patch.getNotes()));
        lead.setSourceType(StringUtils.hasText(patch.getSourceType()) ? patch.getSourceType().trim() : "manual");
        checkDuplicate(lead, id);
        return leadRepository.save(lead);
    }

    @Transactional
    public void delete(Long id) {
        if (!leadRepository.existsById(id)) {
            throw BizException.notFound("客户不存在");
        }
        leadRepository.deleteById(id);
    }

    /**
     * 状态流转：按 STATUS_TRANSITIONS 限制，非法路径拒绝
     */
    @Transactional
    public Lead changeStatus(Long id, String status) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        if (status == null || status.isBlank()) {
            throw BizException.badRequest("状态不能为空");
        }
        String target = status.trim();
        List<String> allowed = STATUS_TRANSITIONS.getOrDefault(lead.getStatus(), List.of());
        if (!allowed.contains(target)) {
            throw BizException.badRequest(
                    "状态流转非法：" + lead.getStatus() + " → " + target
                            + "（允许：" + (allowed.isEmpty() ? "无" : String.join("、", allowed)) + "）");
        }
        lead.setStatus(target);
        return leadRepository.save(lead);
    }

    /**
     * 状态分布统计（看板用）
     */
    public Map<String, Long> stats() {
        return leadRepository.statsByStatus().stream()
                .collect(Collectors.toMap(LeadRepository.StatusStat::getStatus,
                        LeadRepository.StatusStat::getCount));
    }

    public long count() {
        return leadRepository.count();
    }

    /** 按当前筛选导出（供 CSV 导出） */
    public List<Lead> listAllForExport(String keyword, String status, String industry, String sourceType) {
        Specification<Lead> spec = buildSpec(keyword, status, industry, sourceType);
        return leadRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private Specification<Lead> buildSpec(String keyword, String status, String industry, String sourceType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), TenantContext.require()));
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("companyName"), like),
                        cb.like(root.get("contactName"), like),
                        cb.like(root.get("contactEmail"), like),
                        cb.like(root.get("contactPhone"), like),
                        cb.like(root.get("industry"), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (StringUtils.hasText(industry)) {
                predicates.add(cb.equal(root.get("industry"), industry.trim()));
            }
            if (StringUtils.hasText(sourceType)) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 去重校验：source_id 非空按 source+id；否则按 company_name（manual） */
    private void checkDuplicate(Lead lead, Long excludeId) {
        String sourceType = StringUtils.hasText(lead.getSourceType()) ? lead.getSourceType().trim() : "manual";
        String sourceId = lead.getSourceId();
        if (StringUtils.hasText(sourceId)) {
            boolean dup = excludeId == null
                    ? leadRepository.existsBySourceTypeAndSourceId(sourceType, sourceId.trim())
                    : leadRepository.existsBySourceTypeAndSourceIdAndIdNot(sourceType, sourceId.trim(), excludeId);
            if (dup) {
                throw BizException.badRequest("该数据源客户已存在（重复导入）");
            }
        } else {
            boolean dup = excludeId == null
                    ? leadRepository.existsByCompanyNameIgnoreCase(lead.getCompanyName())
                    : leadRepository.existsByCompanyNameIgnoreCaseAndIdNot(lead.getCompanyName(), excludeId);
            if (dup) {
                throw BizException.badRequest("客户「" + lead.getCompanyName() + "」已存在");
            }
        }
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** CSV 导入行 → Lead（供导入服务复用） */
    public static Lead fromCsvRow(Map<String, String> row) {
        Lead lead = new Lead();
        Function<String, String> get = key -> row.getOrDefault(key, "");
        lead.setCompanyName(get.apply("company_name").trim());
        lead.setContactName(trimToNull(get.apply("contact_name")));
        lead.setContactEmail(trimToNull(get.apply("contact_email")));
        lead.setContactPhone(trimToNull(get.apply("contact_phone")));
        lead.setGender(trimToNull(get.apply("gender")));
        lead.setIndustry(trimToNull(get.apply("industry")));
        lead.setRegion(trimToNull(get.apply("region")));
        lead.setScale(trimToNull(get.apply("scale")));
        lead.setWebsite(trimToNull(get.apply("website")));
        lead.setAddress(trimToNull(get.apply("address")));
        lead.setStockCode(trimToNull(get.apply("stock_code")));
        lead.setNotes(trimToNull(get.apply("notes")));
        String sourceType = trimToNull(get.apply("source_type"));
        lead.setSourceType(sourceType == null ? "csv" : sourceType);
        lead.setSourceId(trimToNull(get.apply("source_id")));
        return lead;
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
