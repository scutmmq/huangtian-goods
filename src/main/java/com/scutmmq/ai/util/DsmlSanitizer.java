package com.scutmmq.ai.util;

import java.util.regex.Pattern;

/**
 * 把 DeepSeek Chat Completions 流式输出里夹带的 DSML 工具调用标签
 * (例如 {@code <｜｜DSML｜｜tool_calls>...</｜｜DSML｜｜tool_calls>})
 * 从文本里剔除,避免原始 token 出现在 ai_message.content / SSE 给前端的最终内容里。
 *
 * <p>背景:DeepSeek 边输出 content 边把 tool_call 拼成 DSML 字符串。
 * 如果模型半途被 timeout 截断、或者 tool_call 参数解析失败,
 * DSML 标签会作为"原生文"流到 ai_message.content / SSE delta,前端暴露在用户面前。
 *
 * <p>剔除策略:处理嵌套。整个 DSML 块结构是
 * <pre>
 *   <｜｜DSML｜｜tool_calls>
 *     <｜｜DSML｜｜invoke name="X">
 *       <｜｜DSML｜｜parameter>...</｜｜DSML｜｜parameter>
 *     </｜｜DSML｜｜invoke>
 *   </｜｜DSML｜｜tool_calls>
 * </pre>
 * 内部用栈式扫描定位最外层 open 与 close,中间内容全部删除,支持任意深度嵌套。
 *
 * <p>性能:典型 assistant 消息 < 10KB,常几次 toString 即可,不做预编译。
 *
 * <p>B0/C2 修复无关,这里是 P0 UX hotfix(2026-08 模型事故复盘):
 * 现象是模型调 draft_create_order 时空参数,B1 已正确返回错误文案,
 * 但原始 DSML 标签被 PersistingOrchestratorListener 累计写入 ai_message.content。
 *
 * @since 2026-08 hotfix
 */
public final class DsmlSanitizer {

    private static final String MARKER = "｜｜"; // ｜｜(全角竖线 ×2)
    private static final String OPEN_PREFIX = "<" + MARKER + "DSML" + MARKER;
    private static final String CLOSE_PREFIX = "</" + MARKER + "DSML" + MARKER;

    private DsmlSanitizer() { }

    /**
     * 剔除所有 DSML 标签块,返回处理后字符串。
     * 嵌套匹配:栈式扫描,外层 open 与最近一个匹配的 close 之间全部删除。
     * 原文没有 DSML 块时返回原 String(同一引用,无复制)。
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty() || text.indexOf(OPEN_PREFIX) < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        while (true) {
            int openIdx = sb.indexOf(OPEN_PREFIX);
            if (openIdx < 0) break;
            int openEnd = openIdx + OPEN_PREFIX.length();
            // 跳过 "<...DSML｜｜" 之后到下一个 '>' 之间的属性(name="..." 等),
            // 这样我们只从 '>' 之后开始算 body,避免误把 open tag 后面的 '>' 误判为 close。
            int bodyStart = sb.indexOf(">", openEnd);
            if (bodyStart < 0) break;
            bodyStart++; // 跳过 '>'

            int closeIdx = findMatchingClose(sb, bodyStart);
            if (closeIdx < 0) break; // 损坏的 DSML,放弃后续清理

            // 删 span:[openIdx, closeIdx + CLOSE_PREFIX.length() + 直到该 close tag 的 '>'+1]
            int deleteEnd = sb.indexOf(">", closeIdx + CLOSE_PREFIX.length());
            if (deleteEnd < 0) break;
            deleteEnd++; // 含 close '>'
            sb.delete(openIdx, deleteEnd);
        }
        return sb.toString().trim();
    }

    /**
     * 从 start 开始,在 sb 里找与最近未闭合 open 配对的 close。
     * 嵌套:内层 open/close 平衡。
     * 返回 CLOSE_PREFIX 在 sb 中的开始位置。
     */
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

    /** 仅做 sanity check 用的占位,避免 IDE 警告 unused import。*/
    @SuppressWarnings("unused")
    private static final Pattern ANCHOR = Pattern.compile(OPEN_PREFIX);
}
