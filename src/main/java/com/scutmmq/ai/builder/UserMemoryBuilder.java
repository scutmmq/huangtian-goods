package com.scutmmq.ai.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.config.AiMemoryProperties;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.ai.security.PromptSanitizer.FieldType;
import com.scutmmq.ai.service.AuditService;
import com.scutmmq.ai.service.UserMemorySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.scutmmq.ai.builder.UserMemorySql.ACTIVE_HOURS;
import static com.scutmmq.ai.builder.UserMemorySql.DEFAULT_ADDRESS;
import static com.scutmmq.ai.builder.UserMemorySql.PAYMENT_METHOD;
import static com.scutmmq.ai.builder.UserMemorySql.PRICE_RANGE;
import static com.scutmmq.ai.builder.UserMemorySql.RETURN_RATE;
import static com.scutmmq.ai.builder.UserMemorySql.SHIPPING_METHOD;
import static com.scutmmq.ai.builder.UserMemorySql.TOP_CATEGORIES;
import static com.scutmmq.ai.builder.UserMemorySql.TOP_MERCHANTS;
import static com.scutmmq.ai.builder.UserMemorySql.USER;
import static com.scutmmq.ai.builder.UserMemorySql.USER_MERCHANT;

/**
 * B3 step4: 用户长期记忆画像构建器(SQL 聚合 + sanitize + token 截断)。
 *
 * <p>职责:
 * <ul>
 *   <li>computeIdentity — 查 user / user_address / merchant → identityJson</li>
 *   <li>computePreference — 跑 7 条聚合 SQL(与 mapper.xml + UserMemorySql 同步)→ preferenceJson,
 *       捕获 chk_preference_size 做 JSON OVERFLOW 降级</li>
 *   <li>renderForPrompt — 反序列化 preferenceJson + 拼接 markdown 段,
 *       按 token 阈值(>600→丢 topMerchants,>500→丢 preferredSizes,>400→丢 activeHours)截断,
 *       每个 user-derived 字段经 PromptSanitizer 三层防御</li>
 * </ul>
 *
 * <p>SQL 走 JdbcTemplate 直接调用(常量见 {@link UserMemorySql}),便于单测 mock;
 * mapper.xml 保留作为 MyBatis 复用入口,字符串与本类引用完全一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryBuilder implements com.scutmmq.ai.service.UserMemoryBuilder {

    // ============================ 内部记录类(7 条聚合行) ============================

    public record PriceRangeRow(Double avg, Double p25, Double p50, Double p75, Double max) {
        public static RowMapper<PriceRangeRow> mapper() {
            return (rs, n) -> new PriceRangeRow(
                    dbl(rs, "avg"), dbl(rs, "p25"), dbl(rs, "p50"), dbl(rs, "p75"), dbl(rs, "max"));
        }
    }

    public record CategoryRow(Long categoryId, String categoryName, Double spend, Integer orderCount) {
        public static RowMapper<CategoryRow> mapper() {
            return (rs, n) -> new CategoryRow(rs.getLong("categoryId"), rs.getString("categoryName"),
                    dbl(rs, "spend"), intOrNull(rs, "orderCount"));
        }
    }

    public record MerchantRow(Long merchantId, String merchantName, Double spend, Integer orderCount) {
        public static RowMapper<MerchantRow> mapper() {
            return (rs, n) -> new MerchantRow(rs.getLong("merchantId"), rs.getString("merchantName"),
                    dbl(rs, "spend"), intOrNull(rs, "orderCount"));
        }
    }

    public record ReturnRateRow(Long total, Long refunded, Double rate) {
        public static RowMapper<ReturnRateRow> mapper() {
            return (rs, n) -> new ReturnRateRow(lng(rs, "total"), lng(rs, "refunded"), dbl(rs, "rate"));
        }
    }

    public record PaymentMethodRow(String method, Long count) {
        public static RowMapper<PaymentMethodRow> mapper() {
            return (rs, n) -> new PaymentMethodRow(rs.getString("method"), lng(rs, "count"));
        }
    }

    public record ShippingMethodRow(String method, Long count) {
        public static RowMapper<ShippingMethodRow> mapper() {
            return (rs, n) -> new ShippingMethodRow(rs.getString("method"), lng(rs, "count"));
        }
    }

    public record ActiveHoursRow(Integer hour, Long count) {
        public static RowMapper<ActiveHoursRow> mapper() {
            return (rs, n) -> new ActiveHoursRow(intOrNull(rs, "hour"), lng(rs, "count"));
        }
    }

    // ============================ 截断阈值 ============================

    private static final int DROP_SIZES_THRESHOLD = 500;
    private static final int DROP_HOURS_THRESHOLD = 400;

    // ============================ 依赖 ============================

    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused") // 注入以满足 spec 期望(mapper.xml 复用入口)
    private final UserMemoryMapper mapper;
    private final PromptSanitizer sanitizer;
    private final ObjectMapper json;
    private final AiMemoryProperties props;
    private final AuditService auditService;

    // ============================ computeIdentity ============================

    @Override
    public UserMemorySnapshot computeIdentity(Long userId) {
        try {
            Map<String, Object> identity = new LinkedHashMap<>();

            try {
                Map<String, Object> user = jdbc.queryForObject(USER, userRowMapper(), userId);
                if (user != null) {
                    identity.put("nickName", sanitize((String) user.get("nickName"), FieldType.FREE_TEXT));
                    identity.put("gender", user.get("gender"));
                    identity.put("ageRange", computeAgeRange(user.get("birthday")));
                    identity.put("accountAgeDays", computeAccountAgeDays(user.get("createdAt")));
                }
            } catch (Exception e) {
                log.warn("[AI][MEMORY] identity user query failed userId={} reason={}", userId, e.getMessage());
            }

            try {
                Map<String, Object> addr = jdbc.queryForObject(DEFAULT_ADDRESS, addressRowMapper(), userId);
                if (addr != null) {
                    identity.put("defaultCity", sanitize((String) addr.get("city"), FieldType.FREE_TEXT));
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity default address skipped userId={}", userId);
            }

            try {
                Map<String, Object> mer = jdbc.queryForObject(USER_MERCHANT, merchantRowMapper(), userId);
                if (mer != null) {
                    identity.put("merchantName",
                            sanitize((String) mer.get("merchantName"), FieldType.MERCHANT_NAME));
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity merchant skipped userId={}", userId);
            }

            return new UserMemorySnapshot(json.writeValueAsString(identity), null);
        } catch (Exception e) {
            log.error("[AI][MEMORY] computeIdentity failed userId={}", userId, e);
            return UserMemorySnapshot.empty();
        }
    }

    // ============================ computePreference ============================

    @Override
    public UserMemorySnapshot computePreference(Long userId) {
        try {
            Map<String, Object> pref = new LinkedHashMap<>();
            pref.put("priceRange", orEmpty(jdbc.queryForObject(PRICE_RANGE, PriceRangeRow.mapper(), userId)));

            List<CategoryRow> cats = jdbc.query(TOP_CATEGORIES, CategoryRow.mapper(), userId);
            List<Map<String, Object>> sanitizedCats = cats.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryId", c.categoryId());
                m.put("categoryName", sanitizer.sanitize(c.categoryName(), FieldType.CATEGORY_NAME));
                m.put("spend", c.spend());
                m.put("orderCount", c.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topCategories", sanitizedCats);

            List<MerchantRow> mers = jdbc.query(TOP_MERCHANTS, MerchantRow.mapper(), userId);
            List<Map<String, Object>> sanitizedMers = mers.stream().map(mer -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchantId", mer.merchantId());
                m.put("merchantName", sanitizer.sanitize(mer.merchantName(), FieldType.MERCHANT_NAME));
                m.put("spend", mer.spend());
                m.put("orderCount", mer.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topMerchants", sanitizedMers);

            pref.put("returnRate", orEmpty(jdbc.queryForObject(RETURN_RATE, ReturnRateRow.mapper(), userId)));

            pref.put("paymentMethodPref", jdbc.query(PAYMENT_METHOD, PaymentMethodRow.mapper(), userId).stream()
                    .map(p -> Map.<String, Object>of("method",
                            sanitize(p.method(), FieldType.FREE_TEXT), "count", p.count()))
                    .collect(Collectors.toList()));

            pref.put("shippingMethodPref", jdbc.query(SHIPPING_METHOD, ShippingMethodRow.mapper(), userId).stream()
                    .map(s -> Map.<String, Object>of("method",
                            sanitize(s.method(), FieldType.FREE_TEXT), "count", s.count()))
                    .collect(Collectors.toList()));

            pref.put("activeHours", jdbc.query(ACTIVE_HOURS, ActiveHoursRow.mapper(), userId).stream()
                    .map(ActiveHoursRow::hour).collect(Collectors.toList()));

            pref.put("preferredSizes", sanitizedCats.stream()
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("categoryName", c.get("categoryName"));
                        m.put("size", c.get("orderCount"));
                        return m;
                    })
                    .collect(Collectors.toList()));

            return new UserMemorySnapshot(null, json.writeValueAsString(pref));
        } catch (com.scutmmq.ai.security.PromptInjectionException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("chk_preference_size")) {
                log.warn("[AI][MEMORY] preference_json > 8KB userId={}", userId);
                auditService.logJsonOverflow(userId, "preference");
                return UserMemorySnapshot.empty();
            }
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[AI][MEMORY] computePreference JSON serialization failed userId={}", userId, e);
            return UserMemorySnapshot.empty();
        } catch (Exception e) {
            log.error("[AI][MEMORY] computePreference failed userId={}", userId, e);
            return UserMemorySnapshot.empty();
        }
    }

    // ============================ renderForPrompt ============================

    @Override
    public String renderForPrompt(UserMemorySnapshot snapshot) {
        if (snapshot == null) return "";
        String prefJson = snapshot.preferenceJson();
        if (prefJson == null || prefJson.isEmpty() || "{}".equals(prefJson)) return "";
        try {
            Map<String, Object> pref = json.readValue(prefJson, new TypeReference<Map<String, Object>>() {});
            if (pref == null || pref.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("【用户画像】\n");
            appendSection(sb, "## 价格区间\n", pref.get("priceRange"), this::formatPriceRange);
            appendSection(sb, "## 偏好类目\n", pref.get("topCategories"), v -> formatList((List<?>) v, "categoryName", "spend"));
            appendSection(sb, "## 偏好商家\n", pref.get("topMerchants"), v -> formatList((List<?>) v, "merchantName", "spend"));
            appendSection(sb, "## 偏好尺码\n", pref.get("preferredSizes"), v -> formatList((List<?>) v, "categoryName", "size"));
            appendSection(sb, "## 活跃时段\n", pref.get("activeHours"), v -> String.join(",",
                    ((List<?>) v).stream().map(String::valueOf).collect(Collectors.toList())));
            appendSection(sb, "## 退货率\n", pref.get("returnRate"), this::formatReturnRate);
            appendSection(sb, "## 偏好支付\n", pref.get("paymentMethodPref"), v -> formatList((List<?>) v, "method", "count"));
            appendSection(sb, "## 偏好配送\n", pref.get("shippingMethodPref"), v -> formatList((List<?>) v, "method", "count"));

            return truncate(sb.toString());
        } catch (Exception e) {
            log.error("[AI][MEMORY] render failed", e);
            return "";
        }
    }

    // ============================ 渲染段 + 截断 ============================

    private void appendSection(StringBuilder sb, String header, Object value, java.util.function.Function<Object, String> formatter) {
        if (value == null) return;
        if (value instanceof List && ((List<?>) value).isEmpty()) return;
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) return;
        String body = formatter.apply(value);
        if (body == null || body.isEmpty()) return;
        sb.append(header).append(body).append('\n');
    }

    private String formatPriceRange(Object v) {
        if (!(v instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) v;
        return "avg=" + m.get("avg") + ", p50=" + m.get("p50") + ", p25=" + m.get("p25")
                + ", p75=" + m.get("p75") + ", max=" + m.get("max");
    }

    private String formatReturnRate(Object v) {
        if (!(v instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) v;
        return "total=" + m.get("total") + ", refunded=" + m.get("refunded") + ", rate=" + m.get("rate");
    }

    private String formatList(List<?> list, String nameKey, String valueKey) {
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

    /**
     * 三级 token 截断:>600 丢 topMerchants → >500 丢 preferredSizes → >400 丢 activeHours。
     */
    String truncate(String text) {
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

    private static int estimateTokens(String text) {
        // 简单估算:中英混合按 chars / 2
        return text == null ? 0 : text.length() / 2;
    }

    private static String removeSection(String text, String header) {
        int start = text.indexOf(header);
        if (start < 0) return text;
        int end = text.indexOf("\n## ", start + header.length());
        if (end < 0) end = text.length();
        return text.substring(0, start) + text.substring(end);
    }

    // ============================ 辅助 ============================

    private String sanitize(String raw, FieldType type) {
        return raw == null ? null : sanitizer.sanitize(raw, type);
    }

    private static Object orEmpty(Object v) {
        return v == null ? Map.of() : v;
    }

    private static Double dbl(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getDouble(col); } catch (Exception e) { return null; }
    }

    private static Long lng(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getLong(col); } catch (Exception e) { return null; }
    }

    private static Integer intOrNull(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getInt(col); } catch (Exception e) { return null; }
    }

    private static RowMapper<Map<String, Object>> userRowMapper() {
        return (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getObject("id"));
            m.put("nickName", rs.getString("nick_name"));
            m.put("gender", rs.getObject("gender"));
            m.put("birthday", rs.getDate("birthday"));
            m.put("createdAt", rs.getTimestamp("created_at"));
            return m;
        };
    }

    private static RowMapper<Map<String, Object>> addressRowMapper() {
        return (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recipient", rs.getString("recipient"));
            m.put("city", rs.getString("city"));
            return m;
        };
    }

    private static RowMapper<Map<String, Object>> merchantRowMapper() {
        return (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("merchantName", rs.getString("name"));
            return m;
        };
    }

    private static String computeAgeRange(Object birthday) {
        if (!(birthday instanceof Date)) return null;
        int age = Period.between(((Date) birthday).toLocalDate(), LocalDate.now()).getYears();
        if (age < 18) return "<18";
        if (age < 25) return "18-24";
        if (age < 35) return "25-34";
        if (age < 45) return "35-44";
        if (age < 55) return "45-54";
        return "55+";
    }

    private static Long computeAccountAgeDays(Object createdAt) {
        if (!(createdAt instanceof Timestamp)) return null;
        return Duration.between(((Timestamp) createdAt).toInstant(), Instant.now()).toDays();
    }
}
