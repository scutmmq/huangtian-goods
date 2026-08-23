package com.scutmmq.ai.builder;

import com.scutmmq.ai.config.AiMemoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * B3 step4: Prompt 段落渲染助手 — 负责把 preference JSON 转 markdown 段,
 * 并按 token 阈值三级截断(>600 → 丢 topMerchants,>500 → 丢 preferredSizes,
 * >400 → 丢 activeHours)。
 *
 * <p>从 UserMemoryBuilder 抽出,使主类回到 < 350 行软上限。
 * 依赖 {@link AiMemoryProperties#getPromptTokenCap()} 读取一级截断阈值。
 */
@Component
@RequiredArgsConstructor
public class PromptSectionRenderer {

    private static final int DROP_SIZES_THRESHOLD = 500;
    private static final int DROP_HOURS_THRESHOLD = 400;

    private final AiMemoryProperties props;

    // ============================ 段拼接 ============================

    /**
     * 把一个 preference section 拼到 StringBuilder 上:有值才拼,空 list / 空 map / null 不拼。
     */
    public void appendSection(StringBuilder sb, String header, Object value, Function<Object, String> formatter) {
        if (value == null) return;
        if (value instanceof List && ((List<?>) value).isEmpty()) return;
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) return;
        String body = formatter.apply(value);
        if (body == null || body.isEmpty()) return;
        sb.append(header).append(body).append('\n');
    }

    // ============================ 格式化器 ============================

    public String formatPriceRange(Object v) {
        if (!(v instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) v;
        return "avg=" + m.get("avg") + ", p50=" + m.get("p50") + ", p25=" + m.get("p25")
                + ", p75=" + m.get("p75") + ", max=" + m.get("max");
    }

    public String formatReturnRate(Object v) {
        if (!(v instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) v;
        return "total=" + m.get("total") + ", refunded=" + m.get("refunded") + ", rate=" + m.get("rate");
    }

    public String formatList(List<?> list, String nameKey, String valueKey) {
        List<String> parts = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                parts.add(String.valueOf(m.getOrDefault(nameKey, "")) + "(" + m.getOrDefault(valueKey, "") + ")");
            }
        }
        return String.join(", ", parts);
    }

    // ============================ 截断 ============================

    /**
     * 三级 token 截断:>600 丢 topMerchants → >500 丢 preferredSizes → >400 丢 activeHours。
     */
    public String truncate(String text) {
        if (text == null) return "";
        int tokens = estimateTokens(text);
        if (tokens > props.getPromptTokenCap()) {
            text = removeSection(text, "## 偏好商家\n");
            tokens = estimateTokens(text);
        }
        if (tokens > DROP_SIZES_THRESHOLD) {
            text = removeSection(text, "## 偏好尺码\n");
            tokens = estimateTokens(text);
        }
        if (tokens > DROP_HOURS_THRESHOLD) {
            text = removeSection(text, "## 活跃时段\n");
        }
        return text;
    }

    static int estimateTokens(String text) {
        // 简单估算:中英混合按 chars / 2
        return text == null ? 0 : text.length() / 2;
    }

    static String removeSection(String text, String header) {
        int start = text.indexOf(header);
        if (start < 0) return text;
        int end = text.indexOf("\n## ", start + header.length());
        if (end < 0) end = text.length();
        return text.substring(0, start) + text.substring(end);
    }
}