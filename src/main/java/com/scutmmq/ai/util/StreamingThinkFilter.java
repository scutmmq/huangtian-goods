package com.scutmmq.ai.util;

/**
 * 流式思考标签过滤器。
 * 针对 DeepSeek-R1 / MiniMax-M3 等推理模型，流式逐 chunk 过滤 `<think>...</think>` 内部的思考过程。
 * 确保前端打字机只推送最终给用户的正文，内部思考过程存入 reasoning 容器。
 */
public class StreamingThinkFilter {

    private boolean insideThink = false;
    private final StringBuilder thinkBuffer = new StringBuilder();

    /**
     * 过滤当前 chunk 的 delta 增量。
     *
     * @param delta            当前接收到的文本 chunk
     * @param reasoningBuilder 内部思考过程累积器（可选）
     * @return 过滤后属于最终用户可见的正文片段
     */
    public String filter(String delta, StringBuilder reasoningBuilder) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        StringBuilder visible = new StringBuilder();
        int cursor = 0;
        while (cursor < delta.length()) {
            if (insideThink) {
                int endIdx = delta.indexOf("</think>", cursor);
                if (endIdx >= 0) {
                    thinkBuffer.append(delta, cursor, endIdx);
                    if (reasoningBuilder != null) {
                        reasoningBuilder.append(thinkBuffer);
                    }
                    thinkBuffer.setLength(0);
                    insideThink = false;
                    cursor = endIdx + 8; // length of "</think>"
                } else {
                    thinkBuffer.append(delta, cursor, delta.length());
                    break;
                }
            } else {
                int startIdx = delta.indexOf("<think>", cursor);
                if (startIdx >= 0) {
                    visible.append(delta, cursor, startIdx);
                    insideThink = true;
                    cursor = startIdx + 7; // length of "<think>"
                } else {
                    // 容错：孤立的 </think> 标签直接剔除
                    int looseEnd = delta.indexOf("</think>", cursor);
                    if (looseEnd >= 0) {
                        visible.append(delta, cursor, looseEnd);
                        cursor = looseEnd + 8;
                    } else {
                        visible.append(delta, cursor, delta.length());
                        break;
                    }
                }
            }
        }
        return DsmlSanitizer.strip(visible.toString());
    }

    public boolean isInsideThink() {
        return insideThink;
    }
}
