package com.scutmmq.ai.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 注入防御 3 层(L2 输入清洗)。Strategy §7A.2 / §8.1。
 *
 *  1. 黑名单(L1):命中任一 deny 模式直接抛异常,防止提示词覆盖类攻击。
 *  2. 白名单(L2):对于类目名 / 商家名,只允许 中文 / 英文 / 数字 / 少量中英文标点;
 *     不匹配返回 "[FILTERED]" 占位,让上层调用方识别并提示用户。
 *  3. JSON 转义(L3):把控制字符、引号、反斜杠转成 JSON 安全形式,
 *     防止换行注入或引号闭合破坏 prompt 拼接。
 *
 * 用 inline escapeJson 而非 commons-text:依赖面最小,
 * 仅需覆盖 §8.1 测试场景(双引号 / 反斜杠 / 控制字符)。
 */
@Component
public class PromptSanitizer {

    public enum FieldType { CATEGORY_NAME, MERCHANT_NAME, FREE_TEXT }

    private static final List<Pattern> DENY_LIST = List.of(
            // DSML / 特殊控制标签:匹配 <|...> 以及全宽竖线 <｜...>(U+FF5C)。
            // 用 .*? 非贪婪 → 找到首个 > 即可命中。
            Pattern.compile("<[|｜].*?>"),
            Pattern.compile("(?i)ignore\\s+(previous|all|above)"),
            Pattern.compile("(?i)system\\s*:\\s*"),
            Pattern.compile("(?i)assistant\\s*:\\s*"),
            Pattern.compile("(?i)you\\s+are\\s+now"),
            Pattern.compile("(?i)disregard\\s+(all|previous)")
    );

    private static final Pattern SAFE_NAME = Pattern.compile(
            "^[一-龥a-zA-Z0-9\\s\\-_()&（）【】]{1,32}$");

    /**
     * 清洗一段用户/外部输入,供 LLM prompt 拼接使用。
     *
     * @param raw  原始字符串(null/empty 视为无输入)
     * @param type 字段类型,决定是否启用 SAFE_NAME 过滤
     * @return SAFE 通过则返回 inline JSON 转义后的字符串;
     *         类目/商家名不通过 SAFE_NAME 时返回 "[FILTERED]";
     *         黑名单命中时抛 {@link PromptInjectionException}
     */
    public String sanitize(String raw, FieldType type) {
        if (raw == null || raw.isEmpty()) return "";
        for (Pattern p : DENY_LIST) {
            if (p.matcher(raw).find()) {
                throw new PromptInjectionException(
                        "Deny-list match: " + p.pattern() + " input=" + raw);
            }
        }
        if ((type == FieldType.CATEGORY_NAME || type == FieldType.MERCHANT_NAME)
                && !SAFE_NAME.matcher(raw).matches()) {
            return "[FILTERED]";
        }
        return escapeJson(raw);
    }

    /**
     * Inline JSON 转义:覆盖 " \\ \n \r \t \b \f 以及其他 < 0x20 控制字符。
     * 与 {@code org.apache.commons.text.StringEscapeUtils.escapeJson} 在本任务
     * 8 个测试覆盖的输入集上行为一致;不引入 commons-text 依赖。
     */
    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}