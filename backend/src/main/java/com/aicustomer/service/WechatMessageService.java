package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.entity.Lead;
import com.aicustomer.entity.WechatMessage;
import com.aicustomer.repository.FollowUpRepository;
import com.aicustomer.repository.LeadRepository;
import com.aicustomer.repository.WechatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 微信沟通服务（M2-1.8 记录式工作台）：
 * 记录与客户的微信往来消息（in/out），AI 根据客户画像 + 沟通时间线生成回复建议；
 * 人工确认后记录为 out 消息（人机协同红线：AI 只建议，人确认发送）。
 * 二期对接企业微信 API 真实收发时，仅替换收发通道，消息结构不变。
 */
@Service
public class WechatMessageService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WechatMessageRepository wechatMessageRepository;
    private final LeadRepository leadRepository;
    private final FollowUpRepository followUpRepository;
    private final AiService aiService;

    public WechatMessageService(WechatMessageRepository wechatMessageRepository,
                                LeadRepository leadRepository,
                                FollowUpRepository followUpRepository,
                                AiService aiService) {
        this.wechatMessageRepository = wechatMessageRepository;
        this.leadRepository = leadRepository;
        this.followUpRepository = followUpRepository;
        this.aiService = aiService;
    }

    private Lead requireLead(Long leadId) {
        return leadRepository.findById(leadId)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
    }

    /** 某客户微信会话消息（按发生时间正序，会话阅读顺序） */
    public List<WechatMessage> listByLead(Long leadId) {
        requireLead(leadId);
        return wechatMessageRepository.findByLeadIdOrderBySentAtAsc(leadId);
    }

    /** 记录一条微信消息（direction/content 必填） */
    public WechatMessage create(Long leadId, WechatMessage message) {
        requireLead(leadId);
        String direction = message.getDirection();
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw BizException.badRequest("消息方向不合法（in=客户发来 / out=我方发出）");
        }
        if (!StringUtils.hasText(message.getContent())) {
            throw BizException.badRequest("消息内容不能为空");
        }
        message.setId(null);
        message.setLeadId(leadId);
        message.setTenantId(TenantContext.require());
        message.setContent(message.getContent().trim());
        // 实体默认 status=recorded，需按规则重算：out + 带 aiReply（AI 建议确认后发出）→ ai_confirmed
        if (!StringUtils.hasText(message.getStatus()) || "recorded".equals(message.getStatus())) {
            if ("out".equals(direction) && StringUtils.hasText(message.getAiReply())) {
                message.setStatus("ai_confirmed");
            } else {
                message.setStatus("recorded");
            }
        }
        if (message.getSentAt() == null) {
            message.setSentAt(LocalDateTime.now());
        }
        return wechatMessageRepository.save(message);
    }

    /** 编辑消息（内容/时间可改） */
    public WechatMessage update(Long leadId, Long id, WechatMessage patch) {
        requireLead(leadId);
        WechatMessage exist = wechatMessageRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("消息不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("消息不存在");
        }
        if (!StringUtils.hasText(patch.getContent())) {
            throw BizException.badRequest("消息内容不能为空");
        }
        exist.setContent(patch.getContent().trim());
        if (patch.getSentAt() != null) {
            exist.setSentAt(patch.getSentAt());
        }
        return wechatMessageRepository.save(exist);
    }

    /** 删除消息 */
    @Transactional
    public void delete(Long leadId, Long id) {
        WechatMessage exist = wechatMessageRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("消息不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("消息不存在");
        }
        wechatMessageRepository.delete(exist);
    }

    /**
     * AI 生成微信回复建议：聚合客户画像 + 微信消息时间线（含微信方式跟进）注入 Prompt。
     * 只返回建议文本，不落库 —— 由前端展示供人工编辑，确认后以 out 消息落库。
     */
    public String suggestReply(Long leadId, String goal, String tone, String username) {
        Lead lead = requireLead(leadId);
        String timeline = buildTimeline(leadId);
        String goalHint = StringUtils.hasText(goal) ? goal.trim() : "继续推进与客户的沟通";
        String toneHint = switch (tone == null || tone.isBlank() ? "friendly" : tone) {
            case "formal" -> "语气正式、商务严谨";
            case "neutral" -> "语气中性、自然专业";
            default -> "语气亲切、口语化但保持专业";
        };

        String systemPrompt = """
                你是一名专业的 B2B 销售微信沟通助手，擅长根据与客户的历史沟通记录延续对话。
                请基于提供的客户信息与微信沟通时间线，撰写一条"发给客户的微信回复"。
                要求：
                1. 必须引用沟通记录中的关键事实（客户关注点、上次讨论内容），让客户感到"你记得聊过什么"
                2. 口语化、简洁自然，像真人销售发微信，长度 30-80 字，最多不超过 100 字
                3. 突出客户价值而非产品推销，不要使用夸张营销用语
                4. 自然承接上次沟通，结尾给出一个低门槛的行动邀请（如约时间、发资料）
                5. 不要重复客户已明确拒绝的内容
                6. 只输出回复内容本身，不要引号、不要前缀、不要解释
                """;
        String userPrompt = "客户信息：\n" + leadInfo(lead) +
                "\n本次沟通目标：" + goalHint +
                "\n语气要求：" + toneHint +
                "\n\n【与该客户的微信沟通时间线】\n" + timeline +
                "\n\n请生成微信回复。";

        return aiService.generate("wechat_reply", systemPrompt, userPrompt, null, username);
    }

    private String leadInfo(Lead lead) {
        return "- 公司：" + lead.getCompanyName() +
                "\n- 行业：" + nz(lead.getIndustry()) +
                "\n- 联系人：" + nz(lead.getContactName()) +
                "\n- 地区：" + nz(lead.getRegion()) +
                "\n- 规模：" + nz(lead.getScale()) +
                "\n- 当前状态：" + statusLabel(lead.getStatus());
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "新线索";
        }
        return switch (status) {
            case "contacted" -> "已触达";
            case "interested" -> "有意向";
            case "converted" -> "已转化";
            case "invalid" -> "无效";
            default -> "新线索";
        };
    }

    /** 微信沟通时间线：微信消息 + 微信方式跟进记录（按时间正序） */
    private String buildTimeline(Long leadId) {
        StringBuilder sb = new StringBuilder();
        List<WechatMessage> msgs = wechatMessageRepository.findByLeadIdOrderBySentAtAsc(leadId);
        List<FollowUp> followUps = followUpRepository.findByLeadIdOrderByHappenedAtAsc(leadId);

        if (msgs.isEmpty() && followUps.isEmpty()) {
            return "（暂无沟通记录，按首次微信触达撰写）";
        }

        msgs.forEach(m -> sb.append("[")
                .append(m.getSentAt() == null ? "-" : m.getSentAt().format(TS))
                .append("] ").append("in".equals(m.getDirection()) ? "客户" : "我方")
                .append("：").append(nz(m.getContent())).append("\n"));

        followUps.stream()
                .filter(fu -> "wechat".equals(fu.getMethod()))
                .forEach(fu -> sb.append("[")
                        .append(fu.getHappenedAt() == null ? "-" : fu.getHappenedAt().format(TS))
                        .append("] 我方跟进（微信）：").append(nz(fu.getContent())).append("\n"));

        return sb.toString();
    }

    private String nz(String s) {
        return StringUtils.hasText(s) ? s : "（未填写）";
    }
}
