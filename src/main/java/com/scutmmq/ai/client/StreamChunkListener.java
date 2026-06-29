package com.scutmmq.ai.client;

import java.util.List;

/**
 * 流式 Chat Completions 的回调接口。Provider 每解析完一个 SSE chunk 就会回调对应方法一次。
 *
 * 实现方不应在回调里抛出异常 —— Provider 内部已经捕获并降级为 onError。
 * 流式生命周期：onContentDelta / onReasoningDelta / onToolCallDelta 可能被调用多次，
 * 最后以 onComplete（成功）或 onError（失败）收尾，两者必有其一。
 */
public interface StreamChunkListener {

    /**
     * 文本内容增量（assistant 正在输出的自然语言片段）。
     * 非 thinking 模型也会回调；连续多个 chunk 累加即得最终 reply。
     */
    void onContentDelta(String delta);

    /**
     * 思考过程增量（DeepSeek thinking 模式）。
     * 非 thinking 模型永远不会回调。
     */
    void onReasoningDelta(String delta);

    /**
     * 工具调用分片增量。OpenAI / DeepSeek 协议：第一个分片携带 id + name，
     * 后续分片只携带 arguments 的字符串增量；多 index 表示多个并发 tool_call。
     */
    void onToolCallDelta(List<ToolCallDelta> deltas);

    /**
     * 流正常结束（收到 data: [DONE]）。
     */
    void onComplete();

    /**
     * 流异常结束（网络错误、4xx/5xx、解析致命错误等）。
     */
    void onError(Throwable error);
}
