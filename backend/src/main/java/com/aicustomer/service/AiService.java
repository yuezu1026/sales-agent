package com.aicustomer.service;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.AiCache;
import com.aicustomer.entity.AiUsageLog;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.AiCacheRepository;
import com.aicustomer.repository.AiUsageLogRepository;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.util.AesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 能力中台最小版：统一模型调用 + Token 用量落库
 * MVP 阶段固定 OpenAI 兼容协议（DeepSeek / Qwen 均可通过 base-url 切换）
 * 配置从 system_config 动态读取（按租户隔离，系统设置页保存后立即生效），敏感项解密使用
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 简单限流：窗口 60 秒内每用户每场景最多调用次数 */
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int RATE_LIMIT_PER_WINDOW = 10;

    private final SystemConfigRepository configRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiCacheRepository aiCacheRepository;
    private final AesUtil aesUtil;

    /** 限流桶：key = username:scene → 调用时间戳队列 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    public AiService(SystemConfigRepository configRepository,
                     AiUsageLogRepository usageLogRepository,
                     AiCacheRepository aiCacheRepository,
                     AesUtil aesUtil) {
        this.configRepository = configRepository;
        this.usageLogRepository = usageLogRepository;
        this.aiCacheRepository = aiCacheRepository;
        this.aesUtil = aesUtil;
    }

    private String readConfig(String key) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    /** 布尔配置（默认值回退） */
    private boolean boolConfig(String key, boolean fallback) {
        String v = readConfig(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    /** 整数配置（默认值回退） */
    private int intConfig(String key, int fallback) {
        String v = readConfig(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** SHA-256 hex（AI 缓存键） */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 通用生成：system + user 双 Prompt（含简单限流 + M4-6 AI 缓存）
     */
    public String generate(String scene, String systemPrompt, String userPrompt, Long userId, String username) {
        checkRateLimit(username, scene);

        // M4-6 AI 缓存：总开关 + chat 开关都开启时，相同请求命中直接返回（不调模型、不扣额度）
        // 默认 chat 缓存关闭——邮件/微信等生成场景用户重复生成期待不同内容
        String cacheKey = null;
        if (boolConfig("ai.cache_enabled", true) && boolConfig("ai.cache_chat_enabled", false)) {
            cacheKey = sha256Hex(scene + "\n" + systemPrompt + "\n" + userPrompt);
            int ttlHours = intConfig("ai.cache_ttl_hours", 24);
            LocalDateTime since = LocalDateTime.now().minusHours(ttlHours);
            Optional<AiCache> cached = aiCacheRepository
                    .findByKindAndCacheKeyAndCreatedAtGreaterThanEqual("chat", cacheKey, since);
            if (cached.isPresent()) {
                AiCache hit = cached.get();
                hit.setHitCount(hit.getHitCount() + 1);
                try {
                    aiCacheRepository.save(hit);
                } catch (Exception e) {
                    log.warn("AI 缓存命中计数失败: {}", e.getMessage());
                }
                log.info("AI 缓存命中: scene={}, key={}", scene, cacheKey);
                return hit.getResponse();
            }
        }

        // 缓存命中不消耗，不在此拦截；SaaS 注册即用，无激活码配额闸门

        String apiKeyEnc = readConfig("ai.api_key");
        if (apiKeyEnc == null || apiKeyEnc.isBlank()) {
            throw new IllegalStateException("请先在系统设置中配置 AI API Key");
        }
        String apiKey = aesUtil.decrypt(apiKeyEnc);
        String baseUrl = readConfig("ai.base_url");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        String model = readConfig("ai.model_name");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }

        // 从动态配置构建 ChatModel，保证系统设置页保存后立即生效
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();
        ChatClient client = ChatClient.builder(chatModel).build();

        String content;
        try {
            ChatResponse response = client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            content = response.getResults().stream()
                    .map(g -> g.getOutput() == null ? "" : g.getOutput().getText())
                    .collect(Collectors.joining());
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            int totalTokens = usage == null || usage.getTotalTokens() == null
                    ? promptTokens + completionTokens : usage.getTotalTokens();
            String respModel = response.getMetadata() == null ? null : response.getMetadata().getModel();
            recordUsage(scene, userId, respModel == null || respModel.isBlank() ? model : respModel,
                    promptTokens, completionTokens, totalTokens, content);

            // M4-6 缓存写入（chat）：记录响应与 token，供后续相同请求命中
            if (cacheKey != null) {
                try {
                    AiCache cache = new AiCache();
                    cache.setKind("chat");
                    cache.setScene(scene);
                    cache.setCacheKey(cacheKey);
                    cache.setResponse(content);
                    cache.setTotalTokens(totalTokens);
                    cache.setTenantId(TenantContext.require());
                    aiCacheRepository.save(cache);
                } catch (Exception e) {
                    log.warn("AI 缓存写入失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("AI 调用失败: scene={}, 异常={}: {}", scene, e.getClass().getName(), e.getMessage(), e);
            throw new IllegalStateException("AI 调用失败：" + e.getMessage(), e);
        }
        return content;
    }

    private void recordUsage(String scene, Long userId, String model,
                             Integer promptTokens, Integer completionTokens,
                             Integer totalTokens, String content) {
        try {
            AiUsageLog logEntity = new AiUsageLog();
            logEntity.setScene(scene);
            logEntity.setUserId(userId);
            logEntity.setModel(model == null || model.isBlank() ? "default" : model);
            logEntity.setTenantId(TenantContext.require());
            // 个别兼容端点不返回 usage 时，回退内容长度估算
            if (totalTokens == null || totalTokens <= 0) {
                int estimated = content == null ? 0 : content.length() / 2;
                logEntity.setPromptTokens(0);
                logEntity.setCompletionTokens(estimated);
                logEntity.setTotalTokens(estimated);
            } else {
                logEntity.setPromptTokens(promptTokens == null ? 0 : promptTokens);
                logEntity.setCompletionTokens(completionTokens == null ? 0 : completionTokens);
                logEntity.setTotalTokens(totalTokens);
            }
            BigDecimal inputPrice = pricePerMillion("ai.input_price", BigDecimal.valueOf(2.0));
            BigDecimal outputPrice = pricePerMillion("ai.output_price", BigDecimal.valueOf(8.0));
            BigDecimal cost = BigDecimal.valueOf(logEntity.getPromptTokens())
                    .movePointLeft(6).multiply(inputPrice)
                    .add(BigDecimal.valueOf(logEntity.getCompletionTokens())
                            .movePointLeft(6).multiply(outputPrice));
            logEntity.setCost(cost.setScale(6, RoundingMode.HALF_UP));
            logEntity.setStatus("success");
            usageLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("AI 用量记录失败: {}", e.getMessage());
        }
    }

    /** 读取"元/百万 token"单价配置，缺失/非法时回退默认值 */
    private BigDecimal pricePerMillion(String key, BigDecimal fallback) {
        String v = readConfig(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            BigDecimal p = new BigDecimal(v.trim());
            return p.signum() < 0 ? fallback : p;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 简单限流：固定窗口 60s，每用户每场景最多 RATE_LIMIT_PER_WINDOW 次
     */
    private void checkRateLimit(String username, String scene) {
        String key = (username == null ? "anon" : username) + ":" + scene;
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > RATE_WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= RATE_LIMIT_PER_WINDOW) {
                throw new IllegalStateException("AI 请求过于频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
    }

    /**
     * 用量统计（D6）：今日 + 累计 + 按场景分布
     */
    public Map<String, Object> usageSummary() {
        // M7.12：按中国时区取“今日”，避免容器 UTC 导致今日用量口径偏移
        LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay();

        Map<String, Object> today = new LinkedHashMap<>();
        today.put("calls", usageLogRepository.countByCreatedAtAfter(todayStart));
        today.put("tokens", usageLogRepository.sumTotalTokensAfter(todayStart));
        today.put("cost", usageLogRepository.sumCostAfter(todayStart));

        Map<String, Object> total = new LinkedHashMap<>();
        total.put("calls", usageLogRepository.count());
        total.put("tokens", usageLogRepository.sumTotalTokensAfter(LocalDateTime.of(1970, 1, 1, 0, 0)));
        total.put("cost", usageLogRepository.sumCostAfter(LocalDateTime.of(1970, 1, 1, 0, 0)));

        List<Map<String, Object>> byScene = usageLogRepository.statsByScene().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("scene", s.getScene());
                    m.put("calls", s.getCalls());
                    m.put("tokens", s.getTokens());
                    m.put("cost", s.getCost() == null ? BigDecimal.ZERO : s.getCost());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today);
        result.put("total", total);
        result.put("byScene", byScene);
        return result;
    }
}
