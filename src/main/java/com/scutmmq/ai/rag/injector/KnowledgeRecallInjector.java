package com.scutmmq.ai.rag.injector;

import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.rag.embedding.EmbeddingService;
import com.scutmmq.ai.rag.vectorstore.SearchFilter;
import com.scutmmq.ai.rag.vectorstore.SearchResult;
import com.scutmmq.ai.rag.vectorstore.VectorStore;
import com.scutmmq.ai.security.PromptSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库 Prompt 上下文召回注入器（Context Enricher）。
 *
 * <p><b>注入与安全原则：</b></p>
 * <ul>
 *   <li><b>防幻觉兜底</b>：当未检索到高置信度官方知识时，显式向 Prompt 返回防幻觉占位说明，阻止模型凭先验知识编造商城政策；</li>
 *   <li><b>输入防御清洗</b>：通过 {@link PromptSanitizer} 清洗知识库内容中的特殊指令，并使用动态 Nonce 进行隔离标签封装；</li>
 *   <li><b>多租户隔离</b>：支持传入 {@code currentMerchantId}，实现特定店铺知识的精准隔离，杜绝跨租户串台。</li>
 * </ul>
 */
@Slf4j
@Component
public class KnowledgeRecallInjector {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final PromptSanitizer sanitizer;
    private final AiRagProperties props;

    public KnowledgeRecallInjector(EmbeddingService embeddingService,
                                   VectorStore vectorStore,
                                   PromptSanitizer sanitizer,
                                   AiRagProperties props) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.sanitizer = sanitizer;
        this.props = props;
    }

    public String renderKnowledgeSection(String userQuery) {
        return renderKnowledgeSection(userQuery, null);
    }

    /**
     * 根据用户当前查询语句及当前店铺上下文，检索最相关的知识片段并渲染为提示词段落。
     *
     * @param userQuery         用户输入的提问
     * @param currentMerchantId 当前查看的店铺 ID（可为 null，表示全平台通用检索）
     * @return 格式化后的知识段落（带防幻觉兜底与 Nonce 标签隔离）
     */
    public String renderKnowledgeSection(String userQuery, Long currentMerchantId) {
        if (!props.isEnabled() || userQuery == null || userQuery.trim().isEmpty()) {
            return "";
        }

        try {
            float[] queryVec = embeddingService.embedQuery(userQuery);
            SearchFilter filter = (currentMerchantId != null && currentMerchantId > 0)
                    ? SearchFilter.builder().merchantId(currentMerchantId).build()
                    : SearchFilter.all();

            List<SearchResult> results = vectorStore.similaritySearch(
                    queryVec, filter, props.getTopK(), props.getMinScore()
            );

            if (results.isEmpty()) {
                // 召回为空时显式给出防幻觉硬约束占位
                return "\n【相关商城官方知识与政策】\n"
                        + "[RAG_NO_CONFIDENT_RESULT] 知识库中未检索到与当前问题直接匹配的官方商城规则。\n"
                        + "请严格依据【商城规则与政策硬约束】告知用户官方暂无相关明确记录，并建议联系在线人工客服确认，严禁自行编造任何政策、金额与时效。\n";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n【相关商城官方知识与政策（以下为内部知识库检索结果，时效与准确性以平台公示为准）】\n");
            sb.append("<knowledge_hits>\n");

            for (SearchResult r : results) {
                String cleanTitle = sanitizer.sanitize(r.chunk().getTitle(), PromptSanitizer.FieldType.FREE_TEXT);
                String safeContent;
                try {
                    safeContent = sanitizer.sanitize(r.chunk().getContent(), PromptSanitizer.FieldType.FREE_TEXT);
                } catch (Exception e) {
                    log.warn("[AI][RAG] Knowledge chunk content hit security policy: chunkId={}", r.chunk().getId());
                    safeContent = "[FILTERED_BY_POLICY 该知识片段由于触发安全策略已被脱敏过滤]";
                }
                String wrappedContent = sanitizer.wrapUntrustedKnowledge(safeContent);

                sb.append("· 知识条目: ").append(cleanTitle).append(" (相似度得分: ")
                        .append(String.format("%.2f", r.similarityScore())).append(")\n");
                sb.append(wrappedContent).append("\n\n");
            }

            sb.append("</knowledge_hits>\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("[AI][RAG] Failed to inject knowledge section: {}", e.getMessage());
            return "";
        }
    }
}
