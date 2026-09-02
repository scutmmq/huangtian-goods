package com.scutmmq.ai.rag.embedding;

/**
 * 向量嵌入模型调用异常类（Fail-Fast 快速失败机制）。
 *
 * <p><b>为什么需要 Fail-Fast 而非静默降级？</b></p>
 * <p>
 * 在企业级生产环境中，如果云端真实 Embedding 模型调用失败却静默降级为本地哈希 Mock 向量，
 * 会导致用户的 Query 向量与数据库中已有的真实向量处于两个完全不兼容的向量空间，
 * 进而引发随机向量碰撞与不可控的系统性幻觉。
 * 因此在生产环境下必须快速抛出异常并触发熔断，保障系统数据一致性与可审计性。
 * </p>
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
