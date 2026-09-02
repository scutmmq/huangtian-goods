package com.scutmmq.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiRagProperties;
import com.scutmmq.ai.rag.embedding.EmbeddingService;
import com.scutmmq.ai.rag.vectorstore.SearchFilter;
import com.scutmmq.ai.rag.vectorstore.SearchResult;
import com.scutmmq.ai.rag.vectorstore.VectorStore;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.ai.tool.AgentToolDefinition;
import com.scutmmq.ai.tool.AgentToolResult;
import com.scutmmq.ai.tool.MallAgentTool;
import com.scutmmq.ai.tool.SchemaBuilder;
import com.scutmmq.ai.tool.ToolMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库语义搜索工具（Tool）。
 * 供大模型在对话过程中主动调用，以精确检索商城平台规则、售后保障政策、商品长规格及店铺信息。
 *
 * <p><b>Prompt 注入纵深防御说明：</b></p>
 * <p>
 * 知识库中的商品描述或第三方商家信息属于半可信数据。
 * 工具返回结果统一通过 {@link PromptSanitizer} 进行安全指令过滤并包裹带有 16 位动态随机 Nonce 的
 * {@code <UNTRUSTED_KNOWLEDGE>} 标签，杜绝恶意输入破坏 AI 决策。
 * </p>
 */
@Slf4j
@Component
public class SearchKnowledgeTool implements MallAgentTool {

    private static final String NAME = "search_knowledge";

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final PromptSanitizer sanitizer;
    private final AiRagProperties props;
    private final ObjectMapper objectMapper;

    public SearchKnowledgeTool(EmbeddingService embeddingService,
                               VectorStore vectorStore,
                               PromptSanitizer sanitizer,
                               AiRagProperties props,
                               ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.sanitizer = sanitizer;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolMode mode() {
        return ToolMode.READ_ONLY;
    }

    @Override
    public AgentToolDefinition definition() {
        JsonNode parameters = new SchemaBuilder(objectMapper)
                .prop("query", "string", "查询问题或检索关键词，例如：'7天无理由退货政策'、'退运费规则'、'如何开具电子发票'。必填。")
                .prop("sourceType", "string", "知识源类型过滤，可选：RULE（商城规则/售后FAQ）、PRODUCT（商品规格详情）、MERCHANT（店铺服务）。可选。")
                .prop("merchantId", "integer", "店铺 ID，用于限定特定商家的政策与服务查询，防止跨租户串台。可选。")
                .prop("topK", "integer", "期望返回的最大分块数量，默认 3，最大 5。可选。")
                .require("query")
                .build();

        return AgentToolDefinition.builder()
                .name(NAME)
                .description("在商城官方知识库中进行语义检索。当用户询问售后退换货流程、运费险与运费承担、发票开具、正品保障、特定商品长规格或店铺服务政策时调用。")
                .parameters(parameters)
                .build();
    }

    @Override
    public AgentToolResult execute(JsonNode arguments) {
        if (arguments == null || !arguments.has("query") || arguments.get("query").isNull()) {
            return AgentToolResult.ofText("缺少必填检索参数 query。");
        }

        String query = arguments.get("query").asText("").trim();
        if (query.isEmpty()) {
            return AgentToolResult.ofText("检索 query 不能为空。");
        }

        String sourceType = arguments.has("sourceType") && !arguments.get("sourceType").isNull()
                ? arguments.get("sourceType").asText().trim()
                : null;

        Long merchantId = arguments.has("merchantId") && arguments.get("merchantId").isIntegralNumber()
                ? arguments.get("merchantId").asLong()
                : null;

        int topK = arguments.has("topK") && arguments.get("topK").isInt()
                ? Math.max(1, Math.min(5, arguments.get("topK").asInt()))
                : props.getTopK();

        try {
            // 1. 生成 Query 嵌入向量
            float[] queryVector = embeddingService.embedQuery(query);

            // 2. 执行向量余弦相似度近邻搜索（支持多租户与类型过滤）
            SearchFilter filter = SearchFilter.builder()
                    .sourceType(sourceType)
                    .merchantId(merchantId)
                    .build();

            List<SearchResult> searchResults = vectorStore.similaritySearch(
                    queryVector, filter, topK, props.getMinScore()
            );

            if (searchResults.isEmpty()) {
                return AgentToolResult.ofText("在知识库中未检索到与“" + query + "”直接相关的官方规则或商品信息。"
                        + "请如实告知用户官方暂无相关明确记录或建议联系人工客服确认，切勿自行编造规则。");
            }

            // 3. 组装格式化响应，统一委托 PromptSanitizer 深度清洗与动态 Nonce 隔离
            StringBuilder sb = new StringBuilder();
            sb.append("已为您检索到以下相关官方知识库内容：\n\n");

            for (int i = 0; i < searchResults.size(); i++) {
                SearchResult r = searchResults.get(i);
                String safeContent;
                try {
                    safeContent = sanitizer.sanitize(r.chunk().getContent(), PromptSanitizer.FieldType.FREE_TEXT);
                } catch (Exception e) {
                    log.warn("[AI][TOOL] Knowledge chunk content hit security policy: chunkId={}", r.chunk().getId());
                    safeContent = "[FILTERED_BY_POLICY 该知识片段由于触发安全策略已被脱敏过滤]";
                }

                String cleanTitle = sanitizer.sanitize(r.chunk().getTitle(), PromptSanitizer.FieldType.FREE_TEXT);
                String wrappedChunk = sanitizer.wrapUntrustedKnowledge(
                        String.format("【条目 %d】%s (相关度: %.2f)\n%s", i + 1, cleanTitle, r.similarityScore(), safeContent)
                );
                sb.append(wrappedChunk).append("\n\n");
            }

            return AgentToolResult.ofText(sb.toString().trim());
        } catch (Exception e) {
            log.error("[AI][RAG] search_knowledge execution failed: {}", e.getMessage(), e);
            return AgentToolResult.ofText("知识库检索服务暂时不可用: " + e.getMessage());
        }
    }
}
