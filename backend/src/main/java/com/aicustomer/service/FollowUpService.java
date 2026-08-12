package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.repository.FollowUpRepository;
import com.aicustomer.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 跟进记录服务：客户跟踪留痕（电话/邮件/微信/拜访/其他）
 */
@Service
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final LeadRepository leadRepository;

    public FollowUpService(FollowUpRepository followUpRepository, LeadRepository leadRepository) {
        this.followUpRepository = followUpRepository;
        this.leadRepository = leadRepository;
    }

    private void requireLead(Long leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw BizException.notFound("客户不存在");
        }
    }

    /** 某客户的全部跟进记录（按跟进时间倒序） */
    public List<FollowUp> listByLead(Long leadId) {
        requireLead(leadId);
        return followUpRepository.findByLeadIdOrderByHappenedAtDesc(leadId);
    }

    /** 新增跟进记录 */
    public FollowUp create(Long leadId, FollowUp followUp) {
        requireLead(leadId);
        if (!StringUtils.hasText(followUp.getContent())) {
            throw BizException.badRequest("跟进内容不能为空");
        }
        followUp.setId(null);
        followUp.setLeadId(leadId);
        followUp.setTenantId(TenantContext.require());
        if (!StringUtils.hasText(followUp.getMethod())) {
            followUp.setMethod("other");
        }
        if (followUp.getHappenedAt() == null) {
            followUp.setHappenedAt(java.time.LocalDateTime.now());
        }
        return followUpRepository.save(followUp);
    }

    /** 编辑跟进记录 */
    public FollowUp update(Long leadId, Long id, FollowUp patch) {
        requireLead(leadId);
        FollowUp exist = followUpRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("跟进记录不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("跟进记录不存在");
        }
        if (!StringUtils.hasText(patch.getContent())) {
            throw BizException.badRequest("跟进内容不能为空");
        }
        exist.setContent(patch.getContent());
        if (StringUtils.hasText(patch.getMethod())) {
            exist.setMethod(patch.getMethod());
        }
        if (patch.getHappenedAt() != null) {
            exist.setHappenedAt(patch.getHappenedAt());
        }
        return followUpRepository.save(exist);
    }

    /** 删除跟进记录 */
    @Transactional
    public void delete(Long leadId, Long id) {
        FollowUp exist = followUpRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("跟进记录不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("跟进记录不存在");
        }
        followUpRepository.delete(exist);
    }
}
