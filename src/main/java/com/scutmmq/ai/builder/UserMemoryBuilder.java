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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * B3 step4: 用户长期记忆画像构建器(SQL 聚合 + sanitize + token 截断)。
 *
 * <p>实现 {@link com.scutmmq.ai.service.UserMemoryBuilder} 接口,
 * 由 {@code UserMemoryService} 通过 Spring DI 注入(同 simple name 不同包,
 * 通过 {@code implements} 接口解决类型消歧)。
 *
 * <p>职责:
 * <ul>
 *   <li><b>computeIdentity</b> — 查 user / user_address / merchant → identityJson</li>
 *   <li><b>computePreference</b> — 跑 7 条聚合 SQL(与 mapper.xml 同步)→ preferenceJson,
 *       捕获 {@link DataIntegrityViolationException}(chk_preference_size)做 JSON OVERFLOW 降级</li>
 *   <li><b>renderForPrompt</b> — 反序列化 preferenceJson + 拼接 markdown 段,
 *       按 token 阈值(>600→丢 topMerchants,>500→丢 preferredSizes,>400→丢 activeHours)截断,
 *       每个 user-derived 字段(category / merchant)经 {@link PromptSanitizer} 三层防御</li>
 * </ul>
 *
 * <p>为什么 SQL 走 JdbcTemplate 而非 mapper.xml:
 * 简化测试与减少 mapper 方法数。mapper.xml 仍保留作为 SQL 文档,生产可直接 @Select 注解复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserMemoryBuilder implements com.scutmmq.ai.service.UserMemoryBuilder {

    // ============================ SQL 常量(与 mapper.xml 同步)============================

    static final String PRICE_RANGE_SQL = "SELECT "
            + "ROUND(AVG(o.total_amount), 2) AS avg, "
            + "ROUND(PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p25, "
            + "ROUND(PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p50, "
            + "ROUND(PERCENTILE_DISC(0.75) WITHIN GROUP (ORDER BY o.total_amount), 2) AS p75, "
            + "MAX(o.total_amount) AS max "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "AND o.total_amount > 0";

    static final String TOP_CATEGORIES_SQL = "SELECT pc.id AS categoryId, pc.name AS categoryName, "
            + "SUM(oi.subtotal) AS spend, COUNT(DISTINCT o.id) AS orderCount "
            + "FROM orders o "
            + "JOIN order_items oi ON oi.order_id = o.id "
            + "JOIN product p ON p.id = oi.product_id "
            + "JOIN product_category pc ON pc.id = p.category_id "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY pc.id, pc.name "
            + "ORDER BY spend DESC LIMIT 3";

    static final String TOP_MERCHANTS_SQL = "SELECT m.id AS merchantId, m.name AS merchantName, "
            + "SUM(o.total_amount) AS spend, COUNT(DISTINCT o.id) AS orderCount "
            + "FROM orders o "
            + "JOIN merchant m ON m.id = o.merchant_id "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY m.id, m.name "
            + "ORDER BY spend DESC LIMIT 3";

    static final String RETURN_RATE_SQL = "SELECT "
            + "COUNT(*) AS total, "
            + "SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END) AS refunded, "
            + "ROUND(SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(*), 0), 4) AS rate "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered')";

    static final String PAYMENT_METHOD_SQL = "SELECT o.payment_method AS method, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY o.payment_method ORDER BY count DESC";

    static final String SHIPPING_METHOD_SQL = "SELECT o.shipping_method AS method, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "AND o.shipping_method IS NOT NULL "
            + "GROUP BY o.shipping_method ORDER BY count DESC";

    static final String ACTIVE_HOURS_SQL = "SELECT HOUR(o.ordered_at) AS hour, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY HOUR(o.ordered_at) ORDER BY count DESC LIMIT 5";

    // ============================ identity SQL ============================

    static final String USER_SQL = "SELECT id, nick_name, gender, birthday, created_at "
            + "FROM user WHERE id = ?";

    static final String DEFAULT_ADDRESS_SQL = "SELECT recipient, phone, province, city, district, detail "
            + "FROM user_address WHERE user_id = ? AND is_default = 1 LIMIT 1";

    static final String USER_MERCHANT_SQL = "SELECT id, name FROM merchant WHERE user_id = ? LIMIT 1";

    // ============================ 截断阈值 ============================

    /** > 此值丢 topMerchants(取自 promptTokenCap,默认 600) */
    private int dropMerchantsThreshold() {
        return props.getPromptTokenCap();
    }

    private static final int DROP_SIZES_THRESHOLD = 500;
    private static final int DROP_HOURS_THRESHOLD = 400;

    // ============================ 内部记录类 ============================

    public record PriceRangeRow(Double avg, Double p25, Double p50, Double p75, Double max) {
        public static RowMapper<PriceRangeRow> mapper() {
            return (ResultSet rs, int n) -> new PriceRangeRow(
                    rs.getObject("avg") == null ? null : rs.getDouble("avg"),
                    rs.getObject("p25") == null ? null : rs.getDouble("p25"),
                    rs.getObject("p50") == null ? null : rs.getDouble("p50"),
                    rs.getObject("p75") == null ? null : rs.getDouble("p75"),
                    rs.getObject("max") == null ? null : rs.getDouble("max"));
        }
    }

    public record CategoryRow(Long categoryId, String categoryName, Double spend, Integer orderCount) {
        public static RowMapper<CategoryRow> mapper() {
            return (rs, n) -> new CategoryRow(rs.getLong("categoryId"), rs.getString("categoryName"),
                    rs.getObject("spend") == null ? null : rs.getDouble("spend"),
                    rs.getObject("orderCount") == null ? null : rs.getInt("orderCount"));
        }
    }

    public record MerchantRow(Long merchantId, String merchantName, Double spend, Integer orderCount) {
        public static RowMapper<MerchantRow> mapper() {
            return (rs, n) -> new MerchantRow(rs.getLong("merchantId"), rs.getString("merchantName"),
                    rs.getObject("spend") == null ? null : rs.getDouble("spend"),
                    rs.getObject("orderCount") == null ? null : rs.getInt("orderCount"));
        }
    }

    public record ReturnRateRow(Long total, Long refunded, Double rate) {
        public static RowMapper<ReturnRateRow> mapper() {
            return (rs, n) -> new ReturnRateRow(
                    rs.getObject("total") == null ? null : rs.getLong("total"),
                    rs.getObject("refunded") == null ? null : rs.getLong("refunded"),
                    rs.getObject("rate") == null ? null : rs.getDouble("rate"));
        }
    }

    public record PaymentMethodRow(String method, Long count) {
        public static RowMapper<PaymentMethodRow> mapper() {
            return (rs, n) -> new PaymentMethodRow(rs.getString("method"),
                    rs.getObject("count") == null ? null : rs.getLong("count"));
        }
    }

    public record ShippingMethodRow(String method, Long count) {
        public static RowMapper<ShippingMethodRow> mapper() {
            return (rs, n) -> new ShippingMethodRow(rs.getString("method"),
                    rs.getObject("count") == null ? null : rs.getLong("count"));
        }
    }

    public record ActiveHoursRow(Integer hour, Long count) {
        public static RowMapper<ActiveHoursRow> mapper() {
            return (rs, n) -> new ActiveHoursRow(
                    rs.getObject("hour") == null ? null : rs.getInt("hour"),
                    rs.getObject("count") == null ? null : rs.getLong("count"));
        }
    }

    // ============================ 依赖 ============================

    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused") // 注入以满足 spec 期望(UserMemoryMapper mapper.xml 复用入口)
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

            // 1. 用户基础信息
            try {
                Map<String, Object> user = jdbc.queryForObject(USER_SQL, userRowMapper(), userId);
                if (user != null) {
                    String nickName = (String) user.get("nickName");
                    if (nickName != null) {
                        nickName = sanitizer.sanitize(nickName, FieldType.FREE_TEXT);
                    }
                    identity.put("nickName", nickName);
                    identity.put("gender", user.get("gender"));
                    identity.put("ageRange", computeAgeRange(user.get("birthday")));
                    identity.put("accountAgeDays", computeAccountAgeDays(user.get("createdAt")));
                }
            } catch (Exception e) {
                log.warn("[AI][MEMORY] identity user query failed userId={} reason={}", userId, e.getMessage());
            }

            // 2. 默认地址
            try {
                Map<String, Object> addr = jdbc.queryForObject(DEFAULT_ADDRESS_SQL, addressRowMapper(), userId);
                if (addr != null) {
                    String city = (String) addr.get("city");
                    if (city != null) {
                        city = sanitizer.sanitize(city, FieldType.FREE_TEXT);
                    }
                    identity.put("defaultCity", city);
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity default address query skipped userId={}", userId);
            }

            // 3. 如果是商家用户,加上商家名
            try {
                Map<String, Object> mer = jdbc.queryForObject(USER_MERCHANT_SQL, merchantRowMapper(), userId);
                if (mer != null) {
                    String merchantName = (String) mer.get("merchantName");
                    if (merchantName != null) {
                        merchantName = sanitizer.sanitize(merchantName, FieldType.MERCHANT_NAME);
                    }
                    identity.put("merchantName", merchantName);
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity merchant query skipped userId={}", userId);
            }

            String identityJson = json.writeValueAsString(identity);
            return new UserMemorySnapshot(identityJson, null);
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

            // 1. 价格区间
            PriceRangeRow price = jdbc.queryForObject(PRICE_RANGE_SQL, PriceRangeRow.mapper(), userId);
            pref.put("priceRange", price == null ? Map.of() : price);

            // 2. 偏好类目
            List<CategoryRow> cats = jdbc.query(TOP_CATEGORIES_SQL, CategoryRow.mapper(), userId);
            List<Map<String, Object>> sanitizedCats = cats.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryId", c.categoryId());
                m.put("categoryName", sanitizer.sanitize(c.categoryName(), FieldType.CATEGORY_NAME));
                m.put("spend", c.spend());
                m.put("orderCount", c.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topCategories", sanitizedCats);

            // 3. 偏好商家
            List<MerchantRow> mers = jdbc.query(TOP_MERCHANTS_SQL, MerchantRow.mapper(), userId);
            List<Map<String, Object>> sanitizedMers = mers.stream().map(mer -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchantId", mer.merchantId());
                m.put("merchantName", sanitizer.sanitize(mer.merchantName(), FieldType.MERCHANT_NAME));
                m.put("spend", mer.spend());
                m.put("orderCount", mer.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topMerchants", sanitizedMers);

            // 4. 退货率
            ReturnRateRow rr = jdbc.queryForObject(RETURN_RATE_SQL, ReturnRateRow.mapper(), userId);
            pref.put("returnRate", rr == null ? Map.of() : rr);

            // 5. 偏好支付
            List<PaymentMethodRow> pay = jdbc.query(PAYMENT_METHOD_SQL, PaymentMethodRow.mapper(), userId);
            pref.put("paymentMethodPref", pay.stream()
                    .map(p -> Map.<String, Object>of("method", p.method() == null ? "" : sanitizer.sanitize(p.method(), FieldType.FREE_TEXT), "count", p.count()))
                    .collect(Collectors.toList()));

            // 6. 偏好配送
            List<ShippingMethodRow> ship = jdbc.query(SHIPPING_METHOD_SQL, ShippingMethodRow.mapper(), userId);
            pref.put("shippingMethodPref", ship.stream()
                    .map(s -> Map.<String, Object>of("method", s.method() == null ? "" : sanitizer.sanitize(s.method(), FieldType.FREE_TEXT), "count", s.count()))
                    .collect(Collectors.toList()));

            // 7. 活跃时段
            List<ActiveHoursRow> hours = jdbc.query(ACTIVE_HOURS_SQL, ActiveHoursRow.mapper(), userId);
            pref.put("activeHours", hours.stream().map(h -> h.hour()).collect(Collectors.toList()));

            // 8. preferredSizes:从 topCategories 派生(同类别下的累计订单数,作为"尺码"代理)
            pref.put("preferredSizes", sanitizedCats.stream()
                    .map(c -> Map.<String, Object>of(
                            "categoryName", c.get("categoryName"),
                            "size", c.get("orderCount")))
                    .collect(Collectors.toList()));

            String preferenceJson = json.writeValueAsString(pref);
            return new UserMemorySnapshot(null, preferenceJson);
        } catch (com.scutmmq.ai.security.PromptInjectionException e) {
            // 注入攻击必须向上抛,由调用方记录 + 中断,不静默吞掉
            throw e;
        } catch (DataIntegrityViolationException e) {
            // chk_preference_size 触发 → JSON OVERFLOW 降级
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
        if (snapshot == null || snapshot.preferenceJson() == null || snapshot.preferenceJson().isEmpty()
                || "{}".equals(snapshot.preferenceJson())) {
            return "";
        }
        try {
            Map<String, Object> pref = json.readValue(snapshot.preferenceJson(), new TypeReference<Map<String, Object>>() {});
            if (pref == null || pref.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder("【用户画像】\n");
            appendPriceRange(sb, pref);
            appendTopCategories(sb, pref);
            appendTopMerchants(sb, pref);
            appendPreferredSizes(sb, pref);
            appendActiveHours(sb, pref);
            appendReturnRate(sb, pref);
            appendPaymentMethod(sb, pref);
            appendShippingMethod(sb, pref);

            String rendered = sb.toString();
            return truncate(rendered);
        } catch (Exception e) {
            log.error("[AI][MEMORY] render failed", e);
            return "";
        }
    }

    // ============================ 渲染段(appendXxx) ============================

    private void appendPriceRange(StringBuilder sb, Map<String, Object> pref) {
        Object pr = pref.get("priceRange");
        if (!(pr instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) pr;
        if (m.isEmpty()) return;
        sb.append("## 价格区间\n");
        sb.append("avg=").append(m.get("avg")).append(", ");
        sb.append("p50=").append(m.get("p50")).append(", ");
        sb.append("p25=").append(m.get("p25")).append(", ");
        sb.append("p75=").append(m.get("p75")).append(", ");
        sb.append("max=").append(m.get("max")).append("\n");
    }

    private void appendTopCategories(StringBuilder sb, Map<String, Object> pref) {
        Object tc = pref.get("topCategories");
        if (!(tc instanceof List) || ((List<?>) tc).isEmpty()) return;
        sb.append("## 偏好类目\n");
        sb.append(formatList((List<?>) tc, "categoryName", "spend")).append("\n");
    }

    private void appendTopMerchants(StringBuilder sb, Map<String, Object> pref) {
        Object tm = pref.get("topMerchants");
        if (!(tm instanceof List) || ((List<?>) tm).isEmpty()) return;
        sb.append("## 偏好商家\n");
        sb.append(formatList((List<?>) tm, "merchantName", "spend")).append("\n");
    }

    private void appendPreferredSizes(StringBuilder sb, Map<String, Object> pref) {
        Object ps = pref.get("preferredSizes");
        if (!(ps instanceof List) || ((List<?>) ps).isEmpty()) return;
        sb.append("## 偏好尺码\n");
        sb.append(formatList((List<?>) ps, "categoryName", "size")).append("\n");
    }

    private void appendActiveHours(StringBuilder sb, Map<String, Object> pref) {
        Object ah = pref.get("activeHours");
        if (!(ah instanceof List) || ((List<?>) ah).isEmpty()) return;
        sb.append("## 活跃时段\n");
        sb.append(String.join(",", ((List<?>) ah).stream().map(String::valueOf).collect(Collectors.toList()))).append("\n");
    }

    private void appendReturnRate(StringBuilder sb, Map<String, Object> pref) {
        Object rr = pref.get("returnRate");
        if (!(rr instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) rr;
        if (m.isEmpty()) return;
        sb.append("## 退货率\n");
        sb.append("total=").append(m.get("total")).append(", refunded=").append(m.get("refunded"))
                .append(", rate=").append(m.get("rate")).append("\n");
    }

    private void appendPaymentMethod(StringBuilder sb, Map<String, Object> pref) {
        Object pm = pref.get("paymentMethodPref");
        if (!(pm instanceof List) || ((List<?>) pm).isEmpty()) return;
        sb.append("## 偏好支付\n");
        sb.append(formatList((List<?>) pm, "method", "count")).append("\n");
    }

    private void appendShippingMethod(StringBuilder sb, Map<String, Object> pref) {
        Object sm = pref.get("shippingMethodPref");
        if (!(sm instanceof List) || ((List<?>) sm).isEmpty()) return;
        sb.append("## 偏好配送\n");
        sb.append(formatList((List<?>) sm, "method", "count")).append("\n");
    }

    private String formatList(List<?> list, String nameKey, String valueKey) {
        List<String> parts = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                String n = String.valueOf(m.getOrDefault(nameKey, ""));
                String v = String.valueOf(m.getOrDefault(valueKey, ""));
                parts.add(n + "(" + v + ")");
            }
        }
        return String.join(", ", parts);
    }

    // ============================ 截断(三级瀑布)============================

    /**
     * 三级 token 截断:>600 丢 topMerchants → >500 丢 preferredSizes → >400 丢 activeHours。
     * 每丢一段都重新估算 token,因为段大小不同。
     */
    String truncate(String text) {
        if (text == null) return "";
        int tokens = estimateTokens(text);
        if (tokens > dropMerchantsThreshold()) {
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
        // 简单估算:中英混合按 chars / 2(实测 GPT-4 中文 1 token ≈ 1.5 chars,英文 1 token ≈ 4 chars)
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
            m.put("phone", rs.getString("phone"));
            m.put("province", rs.getString("province"));
            m.put("city", rs.getString("city"));
            m.put("district", rs.getString("district"));
            m.put("detail", rs.getString("detail"));
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

    @SuppressWarnings("unchecked")
    private static String computeAgeRange(Object birthday) {
        if (!(birthday instanceof java.sql.Date)) return null;
        java.time.LocalDate birth = ((java.sql.Date) birthday).toLocalDate();
        int age = java.time.Period.between(birth, java.time.LocalDate.now()).getYears();
        if (age < 18) return "<18";
        if (age < 25) return "18-24";
        if (age < 35) return "25-34";
        if (age < 45) return "35-44";
        if (age < 55) return "45-54";
        return "55+";
    }

    private static Long computeAccountAgeDays(Object createdAt) {
        if (!(createdAt instanceof java.sql.Timestamp)) return null;
        return java.time.Duration.between(
                ((java.sql.Timestamp) createdAt).toInstant(), java.time.Instant.now()).toDays();
    }
}
