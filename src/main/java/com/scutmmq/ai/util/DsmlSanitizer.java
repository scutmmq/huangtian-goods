package com.scutmmq.ai.util;

import java.util.regex.Pattern;

/**
 * 清洗 AI 模型输出中的内部标签：
 * 1. DeepSeek / MiniMax 思考标签 (<think>...</think>)；
 * 2. DeepSeek DSML 工具调用标签 (<｜｜DSML｜｜tool_calls>...</｜｜DSML｜｜tool_calls>)。
 *
 * 保证 ai_message.content / SSE 给前端的最终内容 100% 干净，不泄露内部思考过程与工具调用标签。
 */
public final class DsmlSanitizer {

    private static final String MARKER = "｜｜"; // ｜｜(全角竖线 ×2)
    private static final String OPEN_PREFIX = "<" + MARKER + "DSML" + MARKER;
    private static final String CLOSE_PREFIX = "</" + MARKER + "DSML" + MARKER;

    private static final Pattern THINK_BLOCK_PATTERN = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern UNCLOSED_THINK_PATTERN = Pattern.compile("(?s)<think>.*$");
    private static final Pattern LOOSE_THINK_END_PATTERN = Pattern.compile("</think>");

    private DsmlSanitizer() { }

    /**
     * 剔除所有 think 思考块与 DSML 标签块,返回处理后字符串。
     */
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty()) {
            return "";
        }
        String s = stripThinkTags(text);
        s = stripDsml(s);
        return s;
    }

    /**
     * 剔除 <think>...</think> 及其各种变体
     */
    public static String stripThinkTags(String text) {
        if (text == null) {
            return null;
        }
        if (text.isEmpty() || (!text.contains("<think>") && !text.contains("</think>"))) {
            return text;
        }
        String cleaned = THINK_BLOCK_PATTERN.matcher(text).replaceAll("");
        cleaned = UNCLOSED_THINK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LOOSE_THINK_END_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    /**
     * 剔除所有 DSML 标签块
     */
    public static String stripDsml(String text) {
        if (text == null || text.isEmpty() || text.indexOf(OPEN_PREFIX) < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        while (true) {
            int openIdx = sb.indexOf(OPEN_PREFIX);
            if (openIdx < 0) break;
            int openEnd = openIdx + OPEN_PREFIX.length();
            int bodyStart = sb.indexOf(">", openEnd);
            if (bodyStart < 0) break;
            bodyStart++; // 跳过 '>'

            int closeIdx = findMatchingClose(sb, bodyStart);
            if (closeIdx < 0) break; // 损坏的 DSML,放弃后续清理

            int deleteEnd = sb.indexOf(">", closeIdx + CLOSE_PREFIX.length());
            if (deleteEnd < 0) break;
            deleteEnd++; // 含 close '>'
            sb.delete(openIdx, deleteEnd);
        }
        return sb.toString();
    }

    private static int findMatchingClose(StringBuilder sb, int start) {
        int depth = 1;
        int cursor = start;
        while (cursor < sb.length() && depth > 0) {
            int nextOpen = sb.indexOf(OPEN_PREFIX, cursor);
            int nextClose = sb.indexOf(CLOSE_PREFIX, cursor);
            if (nextClose < 0) return -1;
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                cursor = nextOpen + OPEN_PREFIX.length();
            } else {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                cursor = nextClose + CLOSE_PREFIX.length();
            }
        }
        return -1;
    }
}

