package com.scutmmq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scutmmq.ai.client.ToolCallDelta;
import com.scutmmq.ai.tool.AgentToolCall;

import java.util.List;
import java.util.Objects;

/**
 * 工具调用流式累积器(2026-08-23 凌晨事故后抽出)。
 *
 * <p>DeepSeek 流式 tool_call 按 chunk 拆开发送(arguments 累积尤其脆弱),
 * 之前把累积逻辑散落在 AgentOrchestrator 的 5 个静态方法里,导致:
 * <ul>
 *   <li>C10 — appendArguments 在 current={} 时把 "{}"+delta 拼出永远不合 JSON 的字符串</li>
 *   <li>C10.1 — appendArguments 在 current={ 时把 "{"+{"..."} 拼出 "{{..."</li>
 *   <li>C11 — mergeToolCallDelta 的 index fallback 在 chunk.id 空时要求 slot.id 也空,
 *       导致创建 phantom entry 并被后续 chunk 跨污染继承 args</li>
 *   <li>C8 — 重复工具调用死循环,需要 (name, args) 签名检测</li>
 * </ul>
 *
 * <p>把这块逻辑封到一个类里,有三个收益:
 * <ol>
 *   <li>边界细节(累积、phantom、空对象)集中在一处,后续 hotfix 修改面变小</li>
 *   <li>可以独立单测(就是现在的 AgentOrchestratorArgumentAccumulationTest / MergeToolCallDeltaTest)</li>
 *   <li>AgentOrchestrator 不再持有 STATIC_MAPPER,可注入 ObjectMapper</li>
 * </ol>
 *
 * <p>状态:Thread-safe?**否**。一个 Accumulator 实例只在一个 Run 内使用(每次 runStreaming new 一个),
 * 不跨 Run 共享。
 */
public class ToolCallAccumulator {

    /** 累积过程中的占位字段:raw JSON 字符串存在这里 */
    static final String STREAMING_FIELD = "__streaming__";

    private final ObjectMapper objectMapper;

    public ToolCallAccumulator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * C10.1:DeepSeek 第一个 chunk 可能只发 "{",后续 chunk 又发完整对象
     * "{...}",合并出 "{{..." 永远不是合法 JSON。边界要 strip 多余的前缀。
     *
     * <p>同时 parseArgumentsSafely 在 raw="" 时返回 "{}",prev="{}" + delta='{"a":1}'
     * 也会产生 "{}{...}"。所以也要 strip "{}" 前缀。
     */
    static String normalizeMergedRaw(String merged) {
        if (merged.startsWith("{{")) {
            return merged.substring(1);
        }
        if (merged.startsWith("{}")) {
            return merged.substring(2);
        }
        return merged;
    }

    /**
     * 解析一个 chunk 的 raw arguments 字符串。
     * <ul>
     *   <li>raw 是合法 JSON → 返回 parsed JsonNode</li>
     *   <li>raw 是 partial JSON 或空 → 返回带 __streaming__ 占位的节点</li>
     * </ul>
     */
    public JsonNode parseFirstChunk(String raw) {
        if (raw == null || raw.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n != null) {
                return n;
            }
        } catch (Exception ignored) {
            // 还在累积
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put(STREAMING_FIELD, raw);
        return node;
    }

    /**
     * C10 修复:把新的 chunk delta 合并到现有 args JsonNode。
     * <p>逻辑:
     * <ol>
     *   <li>从 current 抽 raw(优先 __streaming__ 字段,其次 toString,空对象视为空)</li>
     *   <li>prev + delta 合并,strip 多余前缀(避免 "{{..." / "{}{...")</li>
     *   <li>整体 readTree:成功 → 返回 parsed;失败 → 继续累积 __streaming__</li>
     * </ol>
     */
    public JsonNode appendChunk(JsonNode current, String delta) {
        if (delta == null || delta.isEmpty()) return current;

        String prev = "";
        if (current != null && current.isObject() && current.has(STREAMING_FIELD)) {
            prev = current.get(STREAMING_FIELD).asText("");
        } else if (current != null && current.isObject() && current.size() == 0) {
            // 空 {} → 当作 null 处理,否则 "{}"+delta 会变成 "{}{...}" 永远不合 JSON
            prev = "";
        } else if (current != null) {
            prev = current.toString();
        }

        String merged = normalizeMergedRaw(prev + delta);

        try {
            JsonNode n = objectMapper.readTree(merged);
            if (n != null) {
                return n;
            }
        } catch (Exception ignored) {
            // 还没凑齐
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put(STREAMING_FIELD, merged);
        return node;
    }

    /**
     * C11 修复:把一个 stream 来的 ToolCallDelta 合并进 toolCalls 列表。
     * <p>chunk.id 为空(纯 args 续传)时强制按 index 合并,无论 slot 是否有 id。
     * 旧逻辑要求 slot.id 也为空才合并 → 创建 phantom entry,
     * phantom 的 args 被后续带完整 id/name 的 chunk 通过 by-id 匹配继承,
     * 导致 draft_create_order 收到 search_products 的 args 等跨污染事故。
     */
    public void mergeDelta(List<AgentToolCall> toolCalls, ToolCallDelta d) {
        if (d == null) return;
        AgentToolCall existing = null;
        // 先按 id 匹配(完整 id 的 chunk 通常是 tool_call 的起始/独立调用)
        for (AgentToolCall tc : toolCalls) {
            if (tc.getId() != null && !tc.getId().isEmpty()
                    && d.getId() != null && !d.getId().isEmpty()
                    && tc.getId().equals(d.getId())) {
                existing = tc;
                break;
            }
        }
        if (existing == null) {
            // 按 index 兜底
            if (d.getIndex() >= 0 && d.getIndex() < toolCalls.size()) {
                AgentToolCall slot = toolCalls.get(d.getIndex());
                // C11 关键:chunk.id 为空 → 强制按 index 合并(args 续传场景)
                if (d.getId() == null || d.getId().isEmpty()) {
                    existing = slot;
                } else if (slot.getId() == null || slot.getId().isEmpty()) {
                    // slot 还没建好 id,但 chunk 有完整 id → 视为 slot 的补充
                    existing = slot;
                }
            }
        }

        if (existing == null) {
            // 新建一条
            String id = d.getId() == null ? "" : d.getId();
            String name = d.getName() == null ? "" : d.getName();
            JsonNode argsNode = parseFirstChunk(d.getArgumentsDelta());
            AgentToolCall fresh = new AgentToolCall(id, name, argsNode);
            // 确保 id 尚未被别的占用
            for (AgentToolCall tc : toolCalls) {
                if (id.equals(tc.getId())) {
                    existing = tc;
                    break;
                }
            }
            if (existing == null) {
                toolCalls.add(fresh);
                existing = fresh;
            }
        }

        // 补齐 id/name
        if ((existing.getId() == null || existing.getId().isEmpty())
                && d.getId() != null && !d.getId().isEmpty()) {
            existing.setId(d.getId());
        }
        if ((existing.getName() == null || existing.getName().isEmpty())
                && d.getName() != null && !d.getName().isEmpty()) {
            existing.setName(d.getName());
        }
        // 追加 arguments 增量(合并到现有 JsonNode 里)
        if (d.getArgumentsDelta() != null && !d.getArgumentsDelta().isEmpty()) {
            JsonNode merged = appendChunk(existing.getArguments(), d.getArgumentsDelta());
            existing.setArguments(merged);
        }
    }

    /**
     * C8:计算工具调用签名,用于检测重复。
     * 同一 (name, args 序列化) 在一次 Run 内重复出现即视为死循环信号。
     */
    public String computeSignature(String name, JsonNode arguments) {
        String argsStr = (arguments == null || arguments.isNull()) ? "null" : arguments.toString();
        return name + "|" + argsStr;
    }

    /**
     * C8:被拦截的重复工具调用,返回给模型的 sentinel 内容。
     * 必须明确说「请立即给最终回复,不要再调用此工具」,
     * 模型收到这个 tool_response 后通常会终止迭代。
     */
    public String buildDuplicateSentinel(String name, int count) {
        return "[系统提示] 工具 " + name + " 已经用相同参数调用过 " + count
                + " 次,结果不会改变。请立即停止重复调用,直接给用户最终回复"
                + "(可以基于之前的结果,或直接告知商城中没有该商品)。不要再调用此工具。";
    }
}
