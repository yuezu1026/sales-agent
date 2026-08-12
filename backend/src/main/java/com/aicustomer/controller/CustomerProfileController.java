package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProfileSearchResult;
import com.aicustomer.entity.CustomerProfile;
import com.aicustomer.service.profile.CustomerProfileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 客户画像接口（M2-3 RAG 客户画像）：
 * CSV 导入向量化 / 列表 / 删除 / 语义检索。
 * 需登录（JWT 拦截器统一校验）。
 */
@RestController
@RequestMapping("/api/profiles")
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    public CustomerProfileController(CustomerProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * CSV 导入画像（列：公司名称*、行业、联系人、邮箱、成交金额、标签、描述）
     * 返回 {success, duplicate, errors:[{companyName, reason}]}
     */
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
        return ApiResponse.ok(profileService.importCsv(csv));
    }

    /** 全量画像列表（id 倒序） */
    @GetMapping
    public ApiResponse<List<CustomerProfile>> list() {
        return ApiResponse.ok(profileService.list());
    }

    /** 删除画像 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        profileService.delete(id);
        return ApiResponse.ok();
    }

    /** 语义检索：按画像库相似度返回 topN */
    @GetMapping("/search")
    public ApiResponse<List<ProfileSearchResult>> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int top) {
        return ApiResponse.ok(profileService.search(query, top));
    }
}
