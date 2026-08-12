package com.aicustomer.service.profile;

import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.SystemConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量化路由（M2-3）：
 * 读取系统配置 ai.embedding_model —— 非空 → 远程 OpenAI 兼容 embedding；为空 → 本地 TF-IDF。
 * 保证系统设置页保存后即时切换，且无外部依赖时系统仍可完整跑通（本地兜底）。
 */
@Component
public class EmbeddingRouter {

    private final SystemConfigRepository configRepository;
    private final LocalTfidfEmbeddingService local;
    private final RemoteEmbeddingService remote;

    public EmbeddingRouter(SystemConfigRepository configRepository,
                           LocalTfidfEmbeddingService local,
                           RemoteEmbeddingService remote) {
        this.configRepository = configRepository;
        this.local = local;
        this.remote = remote;
    }

    /** 当前生效的向量化实现 */
    public ProfileEmbeddingService active() {
        String model = configRepository.findByConfigKey("ai.embedding_model")
                .map(SystemConfig::getConfigValue)
                .orElse("");
        return (model == null || model.isBlank()) ? local : remote;
    }

    public List<Float> embed(String text) {
        return active().embed(text);
    }

    public String toJson(List<Float> vec) {
        return active().toJson(vec);
    }

    public List<Float> fromJson(String json) {
        return active().fromJson(json);
    }

    public double cosine(List<Float> a, List<Float> b) {
        return active().cosine(a, b);
    }
}
