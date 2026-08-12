package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.entity.Lead;
import com.aicustomer.service.LeadService;
import com.aicustomer.service.profile.LeadProfileScoringService;
import com.aicustomer.util.CsvUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 潜客接口（M2-1 客户管理 CRM）：CRUD + 状态流转 + 分页搜索 + CSV 导入导出
 * 需登录（JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadProfileScoringService profileScoringService;

    public LeadController(LeadService leadService, LeadProfileScoringService profileScoringService) {
        this.leadService = leadService;
        this.profileScoringService = profileScoringService;
    }

    /** 分页 + 搜索 + 筛选 */
    @GetMapping
    public ApiResponse<Page<Lead>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.ok(leadService.list(keyword, status, industry, sourceType, page, size));
    }

    /** 新增（company_name 必填，自动去重） */
    @PostMapping
    public ApiResponse<Lead> create(@Valid @RequestBody Lead lead) {
        return ApiResponse.ok(leadService.create(lead));
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public ApiResponse<Lead> update(@PathVariable Long id, @RequestBody Lead patch) {
        return ApiResponse.ok(leadService.update(id, patch));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        leadService.delete(id);
        return ApiResponse.ok();
    }

    /** 状态流转：new → contacted → interested → converted / invalid */
    @PutMapping("/{id}/status")
    public ApiResponse<Lead> changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApiResponse.ok(leadService.changeStatus(id, request.status()));
    }

    /** 重算单条画像分（M2-3：与自有客户画像库相似度） */
    @PutMapping("/{id}/score")
    public ApiResponse<Lead> rescore(@PathVariable Long id) {
        return ApiResponse.ok(profileScoringService.scoreById(id));
    }

    /** 全量重算画像分（返回 {total, scored, updated}） */
    @PostMapping("/score-all")
    public ApiResponse<Map<String, Object>> scoreAll() {
        return ApiResponse.ok(profileScoringService.scoreAll());
    }

    /** 状态分布统计（看板用） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Long> byStatus = leadService.stats();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return ApiResponse.ok(Map.of("total", total, "byStatus", byStatus));
    }

    /** CSV 导出（按当前筛选条件；UTF-8 BOM 兼容 Excel） */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String sourceType) {
        List<Lead> leads = leadService.listAllForExport(keyword, status, industry, sourceType);
        byte[] body = CsvUtil.toCsv(leads).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"leads_" + System.currentTimeMillis() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /** CSV 导入（模板与导出列一致；返回成功/失败/重复统计） */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要导入的 CSV 文件");
        }
        String csv;
        try {
            csv = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw BizException.badRequest("文件读取失败：" + e.getMessage());
        }
        List<Map<String, String>> rows = CsvUtil.parse(csv);
        int success = 0;
        int duplicate = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                Lead lead = LeadService.fromCsvRow(rows.get(i));
                if (lead.getCompanyName() == null || lead.getCompanyName().isBlank()) {
                    throw BizException.badRequest("第" + (i + 2) + "行：公司名称不能为空");
                }
                leadService.create(lead);
                success++;
            } catch (BizException e) {
                String msg = e.getMessage();
                if (msg.contains("已存在")) {
                    duplicate++;
                } else {
                    errors.add(msg);
                }
            } catch (Exception e) {
                errors.add("第" + (i + 2) + "行：" + e.getMessage());
            }
        }
        return ApiResponse.ok(Map.of(
                "total", rows.size(),
                "success", success,
                "duplicate", duplicate,
                "errors", errors
        ));
    }

    public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }
}
