package com.scutmmq.ai.rag.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 向量数学计算工具类。
 * 提供多维向量点积、L2 范数（模长）、余弦相似度计算以及向量 JSON 序列化/反序列化功能。
 *
 * <p><b>数学原理与几何意义：</b></p>
 * <p>
 * 1. <b>什么是稠密向量（Dense Vector）？</b><br>
 * 大语言模型（LLM）将自然语言文本通过嵌入模型（Embedding Model）投影到高维几何空间（如 1024 维）中。
 * 语义相近的句子（如“如何退换商品”和“退货流程是什么”）在该空间中的几何方向非常接近。
 * </p>
 * <p>
 * 2. <b>为什么选择余弦相似度（Cosine Similarity）？</b><br>
 * 欧氏距离（Euclidean Distance）易受文本长度与向量绝对大小的影响；而余弦相似度度量的是两个高维向量之间的<b>夹角大小</b>，
 * 关注语义方向而非长度：
 * <pre>
 *   cosine_similarity(A, B) = (A · B) / (||A|| * ||B||)
 *                           = (∑ A_i * B_i) / (sqrt(∑ A_i^2) * sqrt(∑ B_i^2))
 * </pre>
 * - 当夹角为 0° 时，cos(0) = 1.0（语义完全相同）；<br>
 * - 当夹角为 90° 时，cos(90°) = 0.0（语义完全无关/正交）；<br>
 * - 当夹角为 180° 时，cos(180°) = -1.0（语义完全相反）。
 * </p>
 */
public final class VectorMathUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VectorMathUtils() {
        // 工具类私有构造器
    }

    /**
     * 计算两个等长浮点向量之间的余弦相似度（Cosine Similarity）。
     *
     * @param v1 向量 A（float 数组）
     * @param v2 向量 B（float 数组）
     * @return 余弦相似度分值，范围 [-1.0, 1.0]；若任一向量全为 0 或输入不合法，则返回 0.0
     * @throws IllegalArgumentException 当两个向量维度不一致时抛出
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0) {
            return 0.0;
        }
        if (v1.length != v2.length) {
            throw new IllegalArgumentException(
                    String.format("Vector dimension mismatch: length of v1 is %d, but v2 is %d", v1.length, v2.length)
            );
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            double a = v1[i];
            double b = v2[i];
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        // 浮点数精度截断保护，防止微小误差超出 [-1.0, 1.0]
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    /**
     * 将 float[] 浮点向量序列化为 JSON 数组字符串。
     * 例如：{@code [0.123, -0.456, 0.789]}
     *
     * @param vector 浮点数向量
     * @return JSON 字符串
     */
    public static String toJson(float[] vector) {
        if (vector == null) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize vector to JSON", e);
        }
    }

    /**
     * 将 JSON 数组字符串反序列化为 float[] 浮点向量。
     *
     * @param json JSON 格式的浮点数数组字符串
     * @return float 数组
     */
    public static float[] fromJson(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new float[0];
        }
        try {
            return OBJECT_MAPPER.readValue(json, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize vector from JSON: " + json, e);
        }
    }
}
