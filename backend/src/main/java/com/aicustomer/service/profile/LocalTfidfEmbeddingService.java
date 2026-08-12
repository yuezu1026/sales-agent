package com.aicustomer.service.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地 TF-IDF 向量化（默认实现，零外部依赖）：
 * <ul>
 *   <li>分词：英文按 [a-z0-9]+ 切词，中文连续串按字符 + 双字 ngram；</li>
 *   <li>特征哈希（feature hashing）映射到固定 768 维，词频加权；</li>
 *   <li>L2 归一化后输出稠密向量，余弦相似度可直接使用。</li>
 * </ul>
 * 优点：不依赖任何 API / 分词库，离线可用；对中文公司名/行业/标签的 MVP 检索足够。
 */
@Component
public class LocalTfidfEmbeddingService implements ProfileEmbeddingService {

    /** 特征哈希维度（固定值，向量 JSON 中记录） */
    public static final int DIM = 768;

    /** 英文/数字词 */
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    /** 连续中文串（含常见标点忽略） */
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fa5]+");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "local-tfidf";
    }

    @Override
    public List<Float> embed(String text) {
        double[] vec = new double[DIM];
        if (text != null) {
            String t = text.toLowerCase(Locale.ROOT);
            // 英文词
            Matcher m = WORD.matcher(t);
            while (m.find()) {
                String term = m.group();
                if (term.length() < 2) {
                    continue; // 过滤单字母
                }
                vec[hash(term)] += 1.0;
            }
            // 中文：字符 + 双字 ngram
            Matcher cm = CHINESE.matcher(t);
            while (cm.find()) {
                String seg = cm.group();
                if (seg.length() == 1) {
                    vec[hash(seg)] += 1.0;
                } else {
                    for (int i = 0; i < seg.length(); i++) {
                        vec[hash(String.valueOf(seg.charAt(i)))] += 0.5;
                    }
                    for (int i = 0; i < seg.length() - 1; i++) {
                        vec[hash(seg.substring(i, i + 2))] += 1.0;
                    }
                }
            }
        }
        // L2 归一化
        double norm = 0;
        for (double v : vec) {
            norm += v * v;
        }
        if (norm == 0) {
            return new ArrayList<>(DIM); // 空文本 → 全零向量（相似度恒 0）
        }
        norm = Math.sqrt(norm);
        List<Float> out = new ArrayList<>(DIM);
        for (double v : vec) {
            out.add((float) (v / norm));
        }
        return out;
    }

    /** 词条 → [0, DIM) 哈希桶（FNV-1a 变体） */
    private int hash(String term) {
        long h = 0x811c9dc5L;
        for (int i = 0; i < term.length(); i++) {
            h ^= term.charAt(i);
            h *= 0x01000193L;
        }
        return Math.floorMod(h, DIM);
    }

    @Override
    public String toJson(List<Float> vec) {
        ObjectNode node = mapper.createObjectNode();
        node.put("dim", vec == null ? 0 : vec.size());
        ArrayNode data = node.putArray("data");
        if (vec != null) {
            for (Float v : vec) {
                data.add(Math.round(v * 1_000_000.0) / 1_000_000.0);
            }
        }
        return node.toString();
    }

    @Override
    public List<Float> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            ObjectNode node = (ObjectNode) mapper.readTree(json);
            if (node.path("dim").asInt() != DIM) {
                return null; // 维度不匹配（可能由远程向量生成），视为不可比较
            }
            ArrayNode data = (ArrayNode) node.get("data");
            List<Float> out = new ArrayList<>(data.size());
            data.forEach(v -> out.add((float) v.asDouble()));
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
