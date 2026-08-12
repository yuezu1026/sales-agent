package com.aicustomer.common;

import java.util.List;
import java.util.Map;

/**
 * 租户默认初始化数据（注册新租户时批量写入）
 * 与旧版全局种子数据保持一致，保证新租户开箱即用
 */
public final class TenantDefaults {

    private TenantDefaults() {
    }

    /** 默认系统配置项（与旧版 ConfigController DEFAULT_CONFIGS 一致） */
    public static final List<Map<String, String>> DEFAULT_CONFIGS = List.of(
            Map.of("key", "ai.api_key", "description", "模型 API Key"),
            Map.of("key", "ai.model_name", "description", "模型名称，如 deepseek-chat"),
            Map.of("key", "ai.base_url", "description", "API 地址，DeepSeek 默认 https://api.deepseek.com"),
            Map.of("key", "ai.embedding_model", "description", "向量模型（画像打分用），如 text-embedding-v3；留空使用本地向量"),
            Map.of("key", "ai.input_price", "description", "输入单价（元/百万 token），DeepSeek 默认 2"),
            Map.of("key", "ai.output_price", "description", "输出单价（元/百万 token），DeepSeek 默认 8"),
            Map.of("key", "ai.trial_budget_cny", "description", "试用版金额兜底上限（元），token 上限之外的第二道保险，默认 1"),
            Map.of("key", "ai.cache_enabled", "description", "AI 缓存总开关（true=开），相同请求命中后直接返回，不重复调用模型、不扣 token（默认 true）"),
            Map.of("key", "ai.cache_ttl_hours", "description", "AI 缓存有效期（小时），超过视为过期重新调用，默认 24"),
            Map.of("key", "ai.cache_chat_enabled", "description", "大模型生成缓存开关（true=开）。生成类场景重复请求会返回相同内容，默认 false（仅向量化默认开启）"),
            Map.of("key", "smtp.host", "description", "SMTP 服务器"),
            Map.of("key", "smtp.port", "description", "SMTP 端口"),
            Map.of("key", "smtp.username", "description", "发件邮箱"),
            Map.of("key", "smtp.password", "description", "SMTP 授权码"),
            Map.of("key", "imap.host", "description", "收件箱 IMAP 服务器（如 imap.gmail.com / imap.qq.com），配置后收件箱同步真实邮件；留空为演示模式"),
            Map.of("key", "imap.port", "description", "收件箱 IMAP 端口（默认 993）"),
            Map.of("key", "imap.ssl", "description", "收件箱 IMAP SSL（true=开启，默认 true）"),
            Map.of("key", "imap.username", "description", "收件箱账号"),
            Map.of("key", "imap.password", "description", "收件箱密码（Gmail 需应用专用密码）"),
            Map.of("key", "mail.daily_limit", "description", "每日发送上限"),
            Map.of("key", "mail.track_url", "description", "打开/点击追踪域名前缀（如 https://www.example.com），邮件正文自动注入追踪像素与链接包装；留空则不追踪"),
            Map.of("key", "mail.unsubscribe_url", "description", "网站域名前缀（如 https://www.example.com），系统自动拼 /unsubscribe?email=xxx 生成退订链接；留空则不追加退订块。内置落地页点击即生效不再发送")
    );

    /** 默认 Prompt 模板（与旧版 V2 种子一致） */
    public record DefaultPrompt(String scene, String name, String content) {
    }

    public static final List<DefaultPrompt> DEFAULT_PROMPTS = List.of(
            new DefaultPrompt("email_gen", "B2B 销售邮件生成",
                    "你是一名专业的 B2B 销售邮件撰写助手。请根据客户信息生成一封简洁、专业、有人情味的中文销售邮件。要求：1. 主题行不超过 20 字；2. 正文 3-4 句话，突出客户价值而非产品推销；3. 结尾给出一个低门槛的行动邀请；4. 不要使用夸张营销用语。")
    );

    /** 默认数据源（与旧版 V8 种子一致） */
    public record DefaultDataSource(String name, String type, String apiBaseUrl, boolean enabled) {
    }

    public static final List<DefaultDataSource> DEFAULT_DATA_SOURCES = List.of(
            new DefaultDataSource("内置演示数据源", "mock", null, true),
            new DefaultDataSource("企查查（预留）", "qichacha", "https://api.qcc.com", false)
    );
}
