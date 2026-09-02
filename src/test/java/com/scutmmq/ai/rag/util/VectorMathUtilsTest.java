package com.scutmmq.ai.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量数学计算工具单测。
 */
class VectorMathUtilsTest {

    @Test
    @DisplayName("相同方向的向量余弦相似度应为 1.0")
    void identicalVectorsReturnOne() {
        float[] v1 = new float[]{1.0f, 2.0f, 3.0f};
        float[] v2 = new float[]{2.0f, 4.0f, 6.0f}; // 成比例同向
        double sim = VectorMathUtils.cosineSimilarity(v1, v2);
        assertEquals(1.0, sim, 1e-5);
    }

    @Test
    @DisplayName("相反方向的向量余弦相似度应为 -1.0")
    void oppositeVectorsReturnNegativeOne() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{-3.0f, 0.0f, 0.0f};
        double sim = VectorMathUtils.cosineSimilarity(v1, v2);
        assertEquals(-1.0, sim, 1e-5);
    }

    @Test
    @DisplayName("相互正交（垂直）的向量余弦相似度应为 0.0")
    void orthogonalVectorsReturnZero() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{0.0f, 1.0f, 0.0f};
        double sim = VectorMathUtils.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 1e-5);
    }

    @Test
    @DisplayName("包含全零的向量应安全返回 0.0 而非 NaN 或崩溃")
    void zeroVectorsReturnZeroSafely() {
        float[] v1 = new float[]{0.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{1.0f, 2.0f, 3.0f};
        double sim = VectorMathUtils.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 1e-5);
    }

    @Test
    @DisplayName("向量维度不匹配时应抛出 IllegalArgumentException")
    void dimensionMismatchThrowsException() {
        float[] v1 = new float[]{1.0f, 2.0f};
        float[] v2 = new float[]{1.0f, 2.0f, 3.0f};
        assertThrows(IllegalArgumentException.class, () -> VectorMathUtils.cosineSimilarity(v1, v2));
    }

    @Test
    @DisplayName("向量 JSON 序列化与反序列化应精确还原")
    void vectorJsonRoundTrip() {
        float[] original = new float[]{0.125f, -0.875f, 0.5f, 10.0f};
        String json = VectorMathUtils.toJson(original);
        assertTrue(json.startsWith("[") && json.endsWith("]"));

        float[] restored = VectorMathUtils.fromJson(json);
        assertArrayEquals(original, restored, 1e-5f);
    }
}
