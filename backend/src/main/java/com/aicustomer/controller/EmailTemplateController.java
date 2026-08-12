package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.EmailTemplate;
import com.aicustomer.service.EmailTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件模板管理（M3-2 补充）：
 * - GET    /api/email-templates          模板列表（按更新时间倒序）
 * - POST   /api/email-templates          新建模板
 * - PUT    /api/email-templates/{id}     更新模板
 * - DELETE /api/email-templates/{id}     删除模板
 * 模板 subject/body 支持占位符变量，保存草稿/发送时按客户字段替换。
 */
@RestController
@RequestMapping("/api/email-templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    @GetMapping
    public ApiResponse<List<EmailTemplate>> list() {
        return ApiResponse.ok(emailTemplateService.list());
    }

    @PostMapping
    public ApiResponse<EmailTemplate> create(@RequestBody EmailTemplate template) {
        return ApiResponse.ok(emailTemplateService.create(template));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmailTemplate> update(@PathVariable Long id, @RequestBody EmailTemplate template) {
        return ApiResponse.ok(emailTemplateService.update(id, template));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable Long id) {
        emailTemplateService.delete(id);
        return ApiResponse.ok(Map.of("message", "已删除"));
    }
}
