package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProspectCompany;
import com.aicustomer.dto.ProspectQuery;
import com.aicustomer.service.prospect.ProspectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 潜客挖掘接口（M2-2）：条件检索数据源 → 勾选入库（source_type=api）
 */
@RestController
@RequestMapping("/api/prospect")
public class ProspectController {

    private final ProspectService prospectService;

    public ProspectController(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    /**
     * 挖掘检索：按 行业/地区/规模/关键词 调数据源返回候选企业
     * 已入库的条目 inLibrary=true（前端置灰，避免重复入库）
     */
    @PostMapping("/search")
    public ApiResponse<List<ProspectCompany>> search(@RequestBody(required = false) SearchRequest req) {
        if (req == null) {
            req = new SearchRequest(null, null, null, null, null);
        }
        ProspectQuery query = new ProspectQuery(req.industry(), req.region(), req.scale(), req.keyword());
        return ApiResponse.ok(prospectService.search(query, req.limit() == null ? 20 : req.limit()));
    }

    /**
     * 勾选入库：批量写入 lead（source_type=api，source_id 去重跳过）
     * 返回 {success, duplicate, errors}
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importCompanies(@RequestBody ImportRequest req) {
        if (req == null || req.companies() == null || req.companies().isEmpty()) {
            throw BizException.badRequest("请先选择要入库的潜客");
        }
        return ApiResponse.ok(prospectService.importCompanies(req.companies()));
    }

    public record SearchRequest(String industry, String region, String scale, String keyword, Integer limit) {
    }

    public record ImportRequest(List<ProspectCompany> companies) {
    }
}
