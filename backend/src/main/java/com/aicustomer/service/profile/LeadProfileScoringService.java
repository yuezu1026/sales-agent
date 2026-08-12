package com.aicustomer.service.profile;

import com.aicustomer.common.BizException;
import com.aicustomer.dto.ProfileSearchResult;
import com.aicustomer.entity.Lead;
import com.aicustomer.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 潜客画像打分（M2-3）：将潜客特征文本向量化，与自有客户画像库做余弦相似度，
 * 相似度最高者写入 lead.profile_score（0~100）+ profile_summary（命中画像摘要）。
 * <p>
 * 画像库为空时不打分（保持 0），不抛错，保证系统无画像数据时仍可用。
 */
@Service
public class LeadProfileScoringService {

    private static final Logger log = LoggerFactory.getLogger(LeadProfileScoringService.class);

    /** 相似度低于该值不写分（避免噪音命中） */
    private static final double MIN_SCORE = 0.05;

    private final LeadRepository leadRepository;
    private final CustomerProfileService profileService;
    private final EmbeddingRouter router;

    public LeadProfileScoringService(LeadRepository leadRepository,
                                     CustomerProfileService profileService,
                                     EmbeddingRouter router) {
        this.leadRepository = leadRepository;
        this.profileService = profileService;
        this.router = router;
    }

    /**
     * 单条潜客打分（返回更新后的 lead）
     */
    @Transactional
    public Lead score(Lead lead) {
        String featureText = buildFeatureText(lead);
        if (!StringUtils.hasText(featureText)) {
            lead.setProfileScore(0);
            lead.setProfileSummary(null);
            return leadRepository.save(lead);
        }
        List<Float> queryVec = router.embed(featureText);
        ProfileSearchResult top = profileService.topMatch(queryVec, MIN_SCORE);
        if (top == null) {
            lead.setProfileScore(0);
            lead.setProfileSummary(null);
            return leadRepository.save(lead);
        }
        int score = (int) Math.round(top.score() * 100);
        lead.setProfileScore(score);
        lead.setProfileSummary(buildSummary(top));
        return leadRepository.save(lead);
    }

    /** 按 id 打分 */
    @Transactional
    public Lead scoreById(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("潜客不存在"));
        return score(lead);
    }

    /**
     * 全量重算画像分
     *
     * @return {total, scored, updated}
     */
    @Transactional
    public Map<String, Object> scoreAll() {
        List<Lead> leads = leadRepository.findAll();
        int scored = 0;
        int updated = 0;
        for (Lead lead : leads) {
            Integer before = lead.getProfileScore();
            Lead after = score(lead);
            if (after.getProfileScore() != null && after.getProfileScore() > 0) {
                scored++;
            }
            if (!java.util.Objects.equals(before, after.getProfileScore())) {
                updated++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", leads.size());
        result.put("scored", scored);
        result.put("updated", updated);
        return result;
    }

    /** 潜客特征文本（与画像特征口径一致：名字 + 行业 + 区域 + 规模 + 备注） */
    public String buildFeatureText(Lead lead) {
        StringBuilder sb = new StringBuilder();
        append(sb, lead.getCompanyName());
        append(sb, lead.getIndustry());
        append(sb, lead.getRegion());
        append(sb, lead.getScale());
        append(sb, lead.getNotes());
        return sb.toString();
    }

    private void append(StringBuilder sb, String s) {
        if (StringUtils.hasText(s)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s.trim());
        }
    }

    private String buildSummary(ProfileSearchResult top) {
        var p = top.profile();
        StringBuilder sb = new StringBuilder("相似画像：").append(p.getCompanyName());
        if (StringUtils.hasText(p.getIndustry())) {
            sb.append('（').append(p.getIndustry()).append('）');
        }
        sb.append("，相似度 ").append(Math.round(top.score() * 100)).append('%');
        if (StringUtils.hasText(p.getTags())) {
            sb.append("，标签：").append(p.getTags());
        }
        return sb.toString();
    }
}
