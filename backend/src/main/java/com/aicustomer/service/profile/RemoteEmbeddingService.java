package com.aicustomer.service.profile;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.AiCache;
import com.aicustomer.entity.AiUsageLog;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.AiCacheRepository;
import com.aicustomer.repository.AiUsageLogRepository;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.util.AesUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 远程 OpenAI 兼容 embedding（配置 ai.embedding_model 后启用）：
 * <ul>
 *   <li>复用与 AiService 一致的动态配置模式：api_key（AES 解密）+ base_url + embedding_model；</li>
 *   <li>OpenAI 兼容端点（如 SiliconFlow text-embedding-v3、DashScope、Qwen 等）可直接接入；</li>
 *   <li>未配置 ai.embedding_model 时，由 EmbeddingRouter 回退本地 TF-IDF，不调用本类。</li>
 * </ul>
 * 注意：DeepSeek 官方无 embedding API，需配置兼容供应商的 base_url + model 才能使用。
 * 调用会按实际 token 用量记入 ai_usage_log（scene=embedding，按租户隔离）。
 */
@Component
public class RemoteEmbeddingService implements ProfileEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingService.class);

    private static final String SCENE_EMBEDDING = "embedding";
    private static final BigDecimal DEFAULT_INPUT_PRICE = BigDecimal.valueOf(2.0);

    private final SystemConfigRepository configRepository;
    private final AesUtil aesUtil;
    private final AiUsageLogRepository usageLogRepository;
    private final AiCacheRepository aiCacheRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RemoteEmbeddingService(SystemConfigRepository configRepository,
                                  AesUtil aesUtil,
                                  AiUsageLogRepository usageLogRepository,
                                  AiCacheRepository aiCacheRepository) {
        this.configRepository = configRepository;
        this.aesUtil = aesUtil;
        this.usageLogRepository = usageLogRepository;
        this.aiCacheRepository = aiCacheRepository;
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

    /** SHA-256 hex（embedding 缓存键） */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    @Override
    public String name() {
        return "remote-openai";
    }

    @Override
    public List<Float> embed(String text) {
        // M4-6 embedding 缓存（确定性场景，默认随 ai.cache_enabled 总开关开启）：相同文本命中直接返回向量
        if (boolConfig("ai.cache_enabled", true)) {
            String cacheKey = sha256Hex(text == null ? "" : text);
            Optional<AiCache> cached = aiCacheRepository
                    .findByKindAndCacheKeyAndCreatedAtGreaterThanEqual(
                            "embedding", cacheKey, LocalDateTime.now().minusHours(24));
            if (cached.isPresent()) {
                AiCache hit = cached.get();
                hit.setHitCount(hit.getHitCount() + 1);
                try {
                    aiCacheRepository.save(hit);
                } catch (Exception e) {
                    log.warn("embedding 缓存命中计数失败: {}", e.getMessage());
                }
                log.info("embedding 缓存命中: key={}", cacheKey);
                return parseVectorJson(hit.getResponse());
            }
        }

        String apiKeyEnc = readConfig("ai.api_key");
        if (apiKeyEnc == null || apiKeyEnc.isBlank()) {
            throw new IllegalStateException("请先在系统设置中配置 AI API Key");
        }
        String apiKey = aesUtil.decrypt(apiKeyEnc);
        String baseUrl = readConfig("ai.base_url");
        String model = readConfig("ai.embedding_model");
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("未配置 ai.embedding_model，无法使用远程向量化");
        }

        // 每次调用实时构建（与 AiService 一致：系统设置保存后立即生效）
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build();
        OpenAiEmbeddingModel modelClient = OpenAiEmbeddingModel.builder()
                .options(options)
                .build();

        // SaaS 注册即用，无激活码配额闸门

        List<Float> out;
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), options);
            EmbeddingResponse response = modelClient.call(request);
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int promptTokens = usage == null || usage.getPromptTokens() == null
                    ? 0 : usage.getPromptTokens();
            int completionTokens = usage == null || usage.getCompletionTokens() == null
                    ? 0 : usage.getCompletionTokens();
            int totalTokens = usage == null || usage.getTotalTokens() == null
                    ? promptTokens + completionTokens : usage.getTotalTokens();
            // 个别端点不返回 usage 时回退文本长度估算
            if (totalTokens <= 0 && text != null) {
                totalTokens = text.length() / 2;
            }
            recordUsage(totalTokens, promptTokens + completionTokens, model);

            float[] vec = response.getResults().stream()
                    .findFirst()
                    .map(r -> r.getOutput())
                    .orElse(null);
            if (vec == null || vec.length == 0) {
                throw new IllegalStateException("远程向量化返回空向量");
            }
            out = new ArrayList<>(vec.length);
            for (float v : vec) {
                out.add(v);
            }

            // M4-6 缓存写入（embedding）：JSON 向量 {"dim":N,"data":[...]}
            try {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("dim", out.size());
                json.put("data", out);
                AiCache cache = new AiCache();
                cache.setKind("embedding");
                cache.setCacheKey(sha256Hex(text == null ? "" : text));
                cache.setResponse(objectMapper.writeValueAsString(json));
                cache.setTotalTokens(Math.max(totalTokens, 0));
                cache.setTenantId(TenantContext.require());
                aiCacheRepository.save(cache);
            } catch (Exception e) {
                log.warn("embedding 缓存写入失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("远程 embedding 调用失败: model={}, 异常={}: {}",
                    model, e.getClass().getName(), e.getMessage());
            throw new IllegalStateException("远程向量化失败：" + e.getMessage(), e);
        }
        return out;
    }

    /** 解析缓存向量 JSON {"dim":N,"data":[...]} → List<Float> */
    private List<Float> parseVectorJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode data = node.get("data");
            List<Float> result = new ArrayList<>(data.size());
            for (JsonNode v : data) {
                result.add((float) v.asDouble());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("缓存向量解析失败：" + e.getMessage(), e);
        }
    }

    /** 按实际用量记入 ai_usage_log（cost 按最新 input_price 实时估算） */
    private void recordUsage(int totalTokens, int promptTokens, String model) {
        try {
            AiUsageLog logEntity = new AiUsageLog();
            logEntity.setScene(SCENE_EMBEDDING);
            logEntity.setUserId(null);
            logEntity.setModel(model == null || model.isBlank() ? "embedding" : model);
            logEntity.setTenantId(TenantContext.require());
            logEntity.setPromptTokens(Math.max(promptTokens, totalTokens));
            logEntity.setCompletionTokens(0);
            logEntity.setTotalTokens(Math.max(totalTokens, 0));
            BigDecimal inputPrice = pricePerMillion("ai.input_price", DEFAULT_INPUT_PRICE);
            BigDecimal cost = BigDecimal.valueOf(logEntity.getPromptTokens())
                    .movePointLeft(6).multiply(inputPrice);
            logEntity.setCost(cost.setScale(6, RoundingMode.HALF_UP));
            logEntity.setStatus("success");
            usageLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("embedding 用量记录失败: {}", e.getMessage());
        }
    }

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

    @Override
    public String toJson(List<Float> vec) {
        StringBuilder sb = new StringBuilder("{\"dim\":").append(vec.size()).append(",\"data\":[");
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Math.round(vec.get(i) * 1_000_000.0) / 1_000_000.0);
        }
        return sb.append("]}").toString();
    }

    @Override
    public List<Float> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            // 校验维度后解析（JSON 格式与本地一致：{"dim":n,"data":[...]})
            int dimStart = json.indexOf("\"dim\":");
            if (dimStart < 0) {
                return null;
            }
            int dimEnd = json.indexOf(',', dimStart);
            int dim = Integer.parseInt(json.substring(dimStart + 6, dimEnd));
            int dataStart = json.indexOf('[', dimEnd);
            int dataEnd = json.lastIndexOf(']');
            if (dataStart < 0 || dataEnd <= dataStart) {
                return null;
            }
            String[] parts = json.substring(dataStart + 1, dataEnd).split(",");
            List<Float> out = new ArrayList<>(dim);
            for (String p : parts) {
                String s = p.trim();
                if (s.isEmpty()) {
                    continue;
                }
                out.add(Float.parseFloat(s));
            }
            return out.size() == dim ? out : null;
        } catch (Exception e) {
            log.warn("解析 embedding JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
