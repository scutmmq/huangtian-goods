package com.scutmmq.ai.rag.embedding;

import com.scutmmq.ai.config.AiRagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 本地确定性 Mock 向量嵌入服务。
 * 供单测、CI 持续集成与本地离线开发使用，完全无需外部网络调用与 API Key。
 *
 * <p><b>确定性语义哈希算法（Deterministic Semantic Hashing）：</b></p>
 * <p>
 * 为使单测与离线环境能够真实测试“Top-K 相似度召回”、“阈值过滤”等 RAG 核心逻辑，
 * 本组件基于文本的分词/字符 N-Gram 与确定性伪随机签名投射到高维空间：
 * <ul>
 *   <li>每个字符与词组作为语义特征单元，激活一组确定性的维度坐标；</li>
 *   <li>包含相同或重叠关键词的文本（如“退货政策”与“如何办理退货”）在向量空间中具有高度重合的特征分量，产生高余弦相似度；</li>
 *   <li>语义完全无关的文本在向量空间中几乎正交（相似度接近 0.0）；</li>
 *   <li>输出向量均经过 L2 单位范数归一化（Unit Normalization），满足 $\|\mathbf{v}\|_2 = 1.0$。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class MockEmbeddingService implements EmbeddingService {

    private final int dimension;

    @org.springframework.beans.factory.annotation.Autowired
    public MockEmbeddingService(AiRagProperties props) {
        this.dimension = (props != null && props.getEmbeddingDimension() > 0)
                ? props.getEmbeddingDimension()
                : 1024;
    }

    public MockEmbeddingService(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embedQuery(String query) {
        return generateDeterministicVector(query, dimension);
    }

    @Override
    public List<float[]> embedDocuments(List<String> documents) {
        List<float[]> result = new ArrayList<>(documents.size());
        for (String doc : documents) {
            result.add(embedQuery(doc));
        }
        return result;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 根据文本内容生成确定性且语义相关的归一化向量。
     */
    public static float[] generateDeterministicVector(String text, int dim) {
        float[] vector = new float[dim];
        if (text == null || text.trim().isEmpty()) {
            return vector;
        }

        String cleaned = text.toLowerCase().trim();

        // 1. 单字与 2-gram 抽取
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                continue;
            }
            // 1-gram
            tokens.add(String.valueOf(c));
            // 2-gram
            if (i + 1 < cleaned.length() && !isPunctuation(cleaned.charAt(i + 1))) {
                tokens.add(cleaned.substring(i, i + 2));
            }
        }

        // 2. 为每个 Token 生成固定的特征分布（激活 12 个特征维度，正向加权以保证重叠词的高余弦相似度）
        for (String token : tokens) {
            long seed = hashToken(token);
            Random random = new Random(seed);
            float tokenWeight = token.length() == 1 ? 1.0f : 2.0f;

            for (int k = 0; k < 12; k++) {
                int dimIndex = Math.abs(random.nextInt()) % dim;
                vector[dimIndex] += tokenWeight;
            }
        }

        // 3. L2 范数归一化：使 ||v||_2 = 1.0
        double sumSquare = 0.0;
        for (float v : vector) {
            sumSquare += v * v;
        }

        if (sumSquare > 0.0) {
            float norm = (float) Math.sqrt(sumSquare);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    private static boolean isPunctuation(char c) {
        return c == '?' || c == '？' || c == '!' || c == '！' || c == ',' || c == '，'
                || c == '。' || c == ':' || c == '：' || c == ';' || c == '；'
                || c == '(' || c == ')' || c == '（' || c == '）' || c == '[' || c == ']'
                || c == '【' || c == '】' || c == '<' || c == '>' || c == '《' || c == '》';
    }

    private static long hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            long val = 0;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | (digest[i] & 0xFF);
            }
            return val;
        } catch (NoSuchAlgorithmException e) {
            return token.hashCode();
        }
    }
}
