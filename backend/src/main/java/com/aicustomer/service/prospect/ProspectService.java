package com.aicustomer.service.prospect;

import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProspectCompany;
import com.aicustomer.dto.ProspectQuery;
import com.aicustomer.entity.DataSource;
import com.aicustomer.entity.Lead;
import com.aicustomer.repository.DataSourceRepository;
import com.aicustomer.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 潜客挖掘服务（M2-2）：
 * 1) search —— 按条件调数据源 Provider 拉取候选，标记已在客户库的条目（防重复入库）
 * 2) import  —— 勾选候选批量入库（source_type=api，source_id 按数据源去重）
 * Provider 选择：优先已启用且注册了 Provider 的数据源，无则回退内置演示数据源（mock）。
 */
@Service
public class ProspectService {

    private static final Logger log = LoggerFactory.getLogger(ProspectService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final List<CompanyDataProvider> providers;
    private final DataSourceRepository dataSourceRepository;
    private final LeadRepository leadRepository;

    public ProspectService(List<CompanyDataProvider> providers,
                           DataSourceRepository dataSourceRepository,
                           LeadRepository leadRepository) {
        this.providers = providers;
        this.dataSourceRepository = dataSourceRepository;
        this.leadRepository = leadRepository;
    }

    /**
     * 挖掘候选：调数据源拉取企业列表，并标记已入库条目（inLibrary=true）
     */
    public List<ProspectCompany> search(ProspectQuery query, int limit) {
        int capped = limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        CompanyDataProvider provider = pickProvider();
        log.info("潜客挖掘: provider={}, query={}", provider.type(), query);
        List<ProspectCompany> candidates = provider.search(query, capped);
        List<ProspectCompany> result = new ArrayList<>();
        for (ProspectCompany c : candidates) {
            boolean inLibrary = StringUtils.hasText(c.sourceId())
                    ? leadRepository.existsBySourceTypeAndSourceId("api", c.sourceId())
                    : leadRepository.existsByCompanyNameIgnoreCase(c.companyName());
            result.add(new ProspectCompany(c.companyName(), c.contactName(), c.contactEmail(),
                    c.contactPhone(), c.industry(), c.region(), c.scale(), c.website(),
                    c.address(), c.sourceType(), c.sourceId(), inLibrary));
        }
        return result;
    }

    /**
     * 批量入库：source_type=api + source_id（数据源:原始ID）去重；
     * 返回 {success, duplicate, errors:[{companyName, reason}]}
     */
    @Transactional
    public Map<String, Object> importCompanies(List<ProspectCompany> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw BizException.badRequest("请先选择要入库的潜客");
        }
        int success = 0;
        int duplicate = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (ProspectCompany c : candidates) {
            try {
                if (!StringUtils.hasText(c.companyName())) {
                    throw BizException.badRequest("公司名称不能为空");
                }
                String sourceId = StringUtils.hasText(c.sourceId())
                        ? c.sourceId().trim()
                        : "api:" + c.companyName().trim().toLowerCase(Locale.ROOT);
                if (leadRepository.existsBySourceTypeAndSourceId("api", sourceId)
                        || leadRepository.existsByCompanyNameIgnoreCase(c.companyName().trim())) {
                    duplicate++;
                    continue;
                }
                Lead lead = new Lead();
                lead.setCompanyName(c.companyName().trim());
                lead.setContactName(trimToNull(c.contactName()));
                lead.setContactEmail(trimToNull(c.contactEmail()));
                lead.setContactPhone(trimToNull(c.contactPhone()));
                lead.setIndustry(trimToNull(c.industry()));
                lead.setRegion(trimToNull(c.region()));
                lead.setScale(trimToNull(c.scale()));
                lead.setWebsite(trimToNull(c.website()));
                lead.setAddress(trimToNull(c.address()));
                lead.setSourceType("api");
                lead.setSourceId(sourceId);
                lead.setStatus("new");
                lead.setProfileScore(0);
                lead.setTenantId(com.aicustomer.common.TenantContext.require());
                lead.setNotes("由「" + (StringUtils.hasText(c.sourceType()) ? c.sourceType() : "api")
                        + "」数据源挖掘入库");
                leadRepository.save(lead);
                success++;
            } catch (Exception e) {
                errors.add(Map.of("companyName", String.valueOf(c.companyName()),
                        "reason", e.getMessage() == null ? "入库失败" : e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("duplicate", duplicate);
        result.put("errors", errors);
        return result;
    }

    /** 选择 Provider：优先已启用且注册了实现的数据源，无则 mock 兜底 */
    private CompanyDataProvider pickProvider() {
        List<DataSource> enabled = dataSourceRepository.findByEnabledTrueOrderByIdAsc();
        for (DataSource ds : enabled) {
            for (CompanyDataProvider p : providers) {
                if (p.type().equalsIgnoreCase(ds.getType())) {
                    return p;
                }
            }
        }
        for (CompanyDataProvider p : providers) {
            if ("mock".equalsIgnoreCase(p.type())) {
                return p;
            }
        }
        if (!providers.isEmpty()) {
            return providers.get(0);
        }
        throw BizException.badRequest("未注册任何企业数据源 Provider");
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
