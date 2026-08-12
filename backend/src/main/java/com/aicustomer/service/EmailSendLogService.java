package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.entity.EmailSendLog;
import com.aicustomer.entity.Lead;
import com.aicustomer.repository.EmailSendLogRepository;
import com.aicustomer.repository.LeadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 邮件发送记录全局视图服务（M6 发件箱）：
 * 跨客户分页检索 email_send_log（含关联客户信息、打开/点击追踪时间）。
 * 数据源独立于 email_draft —— 每次 SMTP 实际发送都会落一条记录（queued/sent/failed/bounced）。
 */
@Service
public class EmailSendLogService {

    private final EmailSendLogRepository emailSendLogRepository;
    private final LeadRepository leadRepository;

    public EmailSendLogService(EmailSendLogRepository emailSendLogRepository,
                               LeadRepository leadRepository) {
        this.emailSendLogRepository = emailSendLogRepository;
        this.leadRepository = leadRepository;
    }

    /** 发件箱视图行 */
    public record EmailSendLogView(Long id, Long leadId, String leadCompanyName, String leadContactName,
                                   String fromEmail, String toEmail, String subject, String body, String status,
                                   String errorMsg, LocalDateTime sentAt,
                                   LocalDateTime openedAt, LocalDateTime clickedAt, LocalDateTime createdAt) {
    }

    /**
     * 全局发送记录分页检索（关键词匹配主题/收件人/正文，状态筛选），按创建时间倒序。
     * 动态拼接条件：避免 NULL 参数进入 SQL 被推断为 bytea。
     */
    public Page<EmailSendLogView> listAll(String keyword, String status, int page, int size) {
        Specification<EmailSendLog> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), com.aicustomer.common.TenantContext.require()));
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern),
                        cb.like(cb.lower(root.get("toEmail")), pattern),
                        cb.like(cb.lower(root.get("body")), pattern)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<EmailSendLog> result = emailSendLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Set<Long> leadIds = result.getContent().stream()
                .map(EmailSendLog::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 注意：leadIds 为空时不能使用 Map.of()（不允许 null key，get(null) 会抛 NPE）
        // 当筛选结果全部 lead_id 为 NULL（如关联客户已删除的发送记录）时会触发
        Map<Long, Lead> leads = new java.util.HashMap<>();
        if (!leadIds.isEmpty()) {
            leads.putAll(leadRepository.findAllById(leadIds).stream()
                    .collect(Collectors.toMap(Lead::getId, Function.identity())));
        }
        return result.map(log -> toView(log, leads.get(log.getLeadId())));
    }

    private EmailSendLogView toView(EmailSendLog log, Lead lead) {
        return new EmailSendLogView(log.getId(), log.getLeadId(),
                lead == null ? null : lead.getCompanyName(),
                lead == null ? null : lead.getContactName(),
                log.getFromEmail(), log.getToEmail(), log.getSubject(), log.getBody(), log.getStatus(),
                log.getErrorMsg(), log.getSentAt(),
                log.getOpenedAt(), log.getClickedAt(), log.getCreatedAt());
    }

    /** 删除一条发送记录（发件箱管理） */
    public void delete(Long id) {
        if (!emailSendLogRepository.existsById(id)) {
            throw BizException.notFound("发送记录不存在");
        }
        emailSendLogRepository.deleteById(id);
    }
}
