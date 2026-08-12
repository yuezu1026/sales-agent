package com.aicustomer.service.profile;

import java.util.List;

/**
 * 画像向量化抽象（M2-3 RAG 客户画像）
 * <p>
 * 实现约定：
 * <ul>
 *   <li>本地 TF-IDF（特征哈希 768 维，零外部依赖）为默认实现；</li>
 *   <li>配置 ai.embedding_model 后路由到远程 OpenAI 兼容 embedding（OpenAiEmbeddingModel）。</li>
 * </ul>
 * 向量统一序列化为 JSON 文本（db-design：embedding TEXT），格式：
 * <pre>{"dim":768,"data":[0.1,0.2,...]}</pre>
 * 余弦相似度要求维度一致，不一致视为 0（防御中途切换 embedding 模型导致旧数据维度不同）。
 */
public interface ProfileEmbeddingService {

    /** 实现标识（写日志/排障用） */
    String name();

    /**
     * 文本 → 归一化向量。
     *
     * @param text 特征文本（公司名+行业+标签+描述等拼接，需截断）
     * @return 归一化后的稠密向量
     */
    List<Float> embed(String text);

    /** 向量 → JSON 文本（含维度信息，供反序列化校验） */
    String toJson(List<Float> vec);

    /**
     * JSON 文本 → 向量；维度与当前实现不一致或格式非法时返回 null
     * （由调用方按"无法比较 → 相似度 0"处理）
     */
    List<Float> fromJson(String json);

    /** 余弦相似度（任一为空或维度不一致 → 0） */
    default double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
