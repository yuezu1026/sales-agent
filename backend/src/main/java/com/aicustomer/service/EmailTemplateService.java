package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailTemplate;
import com.aicustomer.repository.EmailTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 邮件模板管理（M3-2 补充）：
 * 可复用的邮件主题/正文模板 CRUD，subject/body 支持占位符变量；
 * 模板保存草稿与发送时按客户字段替换（渲染逻辑见 util.TemplateRenderer）。
 */
@Service
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;

    public EmailTemplateService(EmailTemplateRepository emailTemplateRepository) {
        this.emailTemplateRepository = emailTemplateRepository;
    }

    /** 模板列表（按更新时间倒序） */
    public List<EmailTemplate> list() {
        return emailTemplateRepository.findAllByOrderByUpdatedAtDesc();
    }

    /** 新建模板（名称唯一） */
    public EmailTemplate create(EmailTemplate t) {
        validate(t);
        String name = t.getName().trim();
        if (emailTemplateRepository.existsByName(name)) {
            throw BizException.badRequest("模板名称已存在");
        }
        t.setId(null);
        t.setName(name);
        t.setTenantId(TenantContext.require());
        return emailTemplateRepository.save(t);
    }

    /** 更新模板（部分字段更新，名称重名校验） */
    public EmailTemplate update(Long id, EmailTemplate patch) {
        EmailTemplate exist = emailTemplateRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("邮件模板不存在"));
        if (StringUtils.hasText(patch.getName())) {
            String name = patch.getName().trim();
            if (!exist.getName().equals(name) && emailTemplateRepository.existsByNameAndIdNot(name, id)) {
                throw BizException.badRequest("模板名称已存在");
            }
            exist.setName(name);
        }
        if (patch.getSubject() != null) {
            if (!StringUtils.hasText(patch.getSubject())) {
                throw BizException.badRequest("邮件主题不能为空");
            }
            exist.setSubject(patch.getSubject().trim());
        }
        if (patch.getBody() != null) {
            if (!StringUtils.hasText(patch.getBody())) {
                throw BizException.badRequest("邮件正文不能为空");
            }
            exist.setBody(patch.getBody());
        }
        if (patch.getDescription() != null) {
            exist.setDescription(patch.getDescription());
        }
        return emailTemplateRepository.save(exist);
    }

    /** 删除模板 */
    public void delete(Long id) {
        if (emailTemplateRepository.findById(id).isEmpty()) {
            throw BizException.notFound("邮件模板不存在");
        }
        emailTemplateRepository.deleteById(id);
    }

    private void validate(EmailTemplate t) {
        if (!StringUtils.hasText(t.getName())) {
            throw BizException.badRequest("模板名称不能为空");
        }
        if (!StringUtils.hasText(t.getSubject())) {
            throw BizException.badRequest("邮件主题不能为空");
        }
        if (!StringUtils.hasText(t.getBody())) {
            throw BizException.badRequest("邮件正文不能为空");
        }
    }
}
