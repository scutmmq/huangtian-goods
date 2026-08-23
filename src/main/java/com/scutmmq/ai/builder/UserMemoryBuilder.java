package com.scutmmq.ai.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.ai.security.PromptSanitizer.FieldType;
import com.scutmmq.ai.service.AuditService;
import com.scutmmq.ai.service.UserMemorySnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
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
 *   <li>computeIdentity — 查 user / user_address / merchant → identityJson,
 *       捕获 chk_identity_size 做 JSON OVERFLOW 降级</li>
 *   <li>computePreference — 跑 7 条聚合 SQL(与 mapper.xml + UserMemorySql 同步)→ preferenceJson,
 *       捕获 chk_preference_size 做 JSON OVERFLOW 降级</li>
 *   <li>renderForPrompt — 反序列化 preferenceJson + 拼接 markdown 段,
 *       按 token 阈值截断,委托 {@link PromptSectionRenderer} 实现</li>
 * </ul>
 *
 * <p>SQL 走 JdbcTemplate 直接调用(常量见 {@link UserMemorySql}),便于单测 mock;
 * mapper.xml 保留作为 MyBatis 复用入口,字符串与本类引用完全一致。
 */
@Slf4j
@Component
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

    // ============================ 依赖 ============================

    /** B3 step10: DB 查询耗时"长事务"阈值(>1s 视为长事务,记 counter) */
    private static final long DB_LONG_TX_THRESHOLD_MS = 1_000L;

    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused") // 注入以满足 spec 期望(mapper.xml 复用入口)
    private final UserMemoryMapper mapper;
    private final PromptSanitizer sanitizer;
    private final ObjectMapper json;
    private final AuditService auditService;
    private final PromptSectionRenderer renderer;
    private final MeterRegistry meter;

    /** B3 step10: token 估算 summary — {@code ai_memory_injection_token_total{quantile=0.5|0.95}} */
    private final io.micrometer.core.instrument.DistributionSummary injectionTokenSummary;

    /** B3 step10: DB 长事务计数器 — {@code ai_memory_db_long_tx_total} */
    private final Counter dbLongTxCounter;

    public UserMemoryBuilder(JdbcTemplate jdbc, UserMemoryMapper mapper, PromptSanitizer sanitizer,
                             ObjectMapper json, AuditService auditService, PromptSectionRenderer renderer,
                             MeterRegistry meter) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.sanitizer = sanitizer;
        this.json = json;
        this.auditService = auditService;
        this.renderer = renderer;
        this.meter = meter;
        this.injectionTokenSummary = io.micrometer.core.instrument.DistributionSummary
                .builder("ai_memory_injection_token_total")
                .description("注入到 prompt 的画像 token 估算(基于 chars/2)")
                .publishPercentiles(0.5, 0.95)
                .register(meter);
        this.dbLongTxCounter = Counter.builder("ai_memory_db_long_tx_total")
                .description("DB 查询 >1s 视为长事务计数")
                .register(meter);
    }

    /** B3 step10: 记录一条 DB 查询耗时到 ai_memory_db_query_seconds{sql}。 */
    private void recordDbQuery(String sqlName, Runnable query) {
        long start = System.currentTimeMillis();
        try {
            query.run();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            Timer.builder("ai_memory_db_query_seconds")
                    .description("记忆 SQL 查询耗时分布")
                    .tags(Tags.of("sql", sqlName))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meter)
                    .record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (elapsed > DB_LONG_TX_THRESHOLD_MS) {
                dbLongTxCounter.increment();
            }
        }
    }

    // ============================ computeIdentity ============================

    @Override
    public UserMemorySnapshot computeIdentity(Long userId) {
        try {
            Map<String, Object> identity = new LinkedHashMap<>();

            try {
                final Map<String, Object>[] userBox = new Map[]{null};
                recordDbQuery("user", () -> {
                    try { userBox[0] = jdbc.queryForObject(USER, userRowMapper(), userId); }
                    catch (DataIntegrityViolationException dive) { throw dive; }
                });
                Map<String, Object> user = userBox[0];
                if (user != null) {
                    identity.put("nickName", sanitize((String) user.get("nickName"), FieldType.FREE_TEXT));
                    identity.put("gender", user.get("gender"));
                    identity.put("ageRange", computeAgeRange(user.get("birthday")));
                    identity.put("accountAgeDays", computeAccountAgeDays(user.get("createdAt")));
                }
            } catch (DataIntegrityViolationException e) {
                // chk_identity_size 由外层 catch 统一审计降级,这里直接 rethrow
                throw e;
            } catch (Exception e) {
                log.warn("[AI][MEMORY] identity user query failed userId={} reason={}", userId, e.getMessage());
            }

            try {
                final Map<String, Object>[] addrBox = new Map[]{null};
                recordDbQuery("default_address", () -> {
                    addrBox[0] = jdbc.queryForObject(DEFAULT_ADDRESS, addressRowMapper(), userId);
                });
                Map<String, Object> addr = addrBox[0];
                if (addr != null) {
                    identity.put("defaultCity", sanitize((String) addr.get("city"), FieldType.FREE_TEXT));
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity default address skipped userId={}", userId);
            }

            try {
                final Map<String, Object>[] merBox = new Map[]{null};
                recordDbQuery("user_merchant", () -> {
                    merBox[0] = jdbc.queryForObject(USER_MERCHANT, merchantRowMapper(), userId);
                });
                Map<String, Object> mer = merBox[0];
                if (mer != null) {
                    identity.put("merchantName",
                            sanitize((String) mer.get("merchantName"), FieldType.MERCHANT_NAME));
                }
            } catch (Exception e) {
                log.debug("[AI][MEMORY] identity merchant skipped userId={}", userId);
            }

            return new UserMemorySnapshot(json.writeValueAsString(identity), null);
        } catch (com.scutmmq.ai.security.PromptInjectionException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("chk_identity_size")) {
                log.warn("[AI][MEMORY] identity_json > 8KB userId={}", userId);
                auditService.logJsonOverflow(userId, "identity");
                return UserMemorySnapshot.empty();
            }
            throw e;
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

            // price_range
            final Object[] priceBox = new Object[]{null};
            recordDbQuery("price_range", () -> {
                priceBox[0] = jdbc.queryForObject(PRICE_RANGE, PriceRangeRow.mapper(), userId);
            });
            pref.put("priceRange", orEmpty(priceBox[0]));

            // top_categories
            final List<CategoryRow>[] catsBox = new List[]{List.of()};
            recordDbQuery("top_categories", () -> {
                catsBox[0] = jdbc.query(TOP_CATEGORIES, CategoryRow.mapper(), userId);
            });
            List<CategoryRow> cats = catsBox[0];
            List<Map<String, Object>> sanitizedCats = cats.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("categoryId", c.categoryId());
                m.put("categoryName", sanitizer.sanitize(c.categoryName(), FieldType.CATEGORY_NAME));
                m.put("spend", c.spend());
                m.put("orderCount", c.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topCategories", sanitizedCats);

            // top_merchants
            final List<MerchantRow>[] mersBox = new List[]{List.of()};
            recordDbQuery("top_merchants", () -> {
                mersBox[0] = jdbc.query(TOP_MERCHANTS, MerchantRow.mapper(), userId);
            });
            List<MerchantRow> mers = mersBox[0];
            List<Map<String, Object>> sanitizedMers = mers.stream().map(mer -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchantId", mer.merchantId());
                m.put("merchantName", sanitizer.sanitize(mer.merchantName(), FieldType.MERCHANT_NAME));
                m.put("spend", mer.spend());
                m.put("orderCount", mer.orderCount());
                return m;
            }).collect(Collectors.toList());
            pref.put("topMerchants", sanitizedMers);

            // return_rate
            final Object[] retBox = new Object[]{null};
            recordDbQuery("return_rate", () -> {
                retBox[0] = jdbc.queryForObject(RETURN_RATE, ReturnRateRow.mapper(), userId);
            });
            pref.put("returnRate", orEmpty(retBox[0]));

            // payment_method
            final List<PaymentMethodRow>[] payBox = new List[]{List.of()};
            recordDbQuery("payment_method", () -> {
                payBox[0] = jdbc.query(PAYMENT_METHOD, PaymentMethodRow.mapper(), userId);
            });
            pref.put("paymentMethodPref", payBox[0].stream()
                    .map(p -> Map.<String, Object>of("method",
                            sanitize(p.method(), FieldType.FREE_TEXT), "count", p.count()))
                    .collect(Collectors.toList()));

            // shipping_method
            final List<ShippingMethodRow>[] shipBox = new List[]{List.of()};
            recordDbQuery("shipping_method", () -> {
                shipBox[0] = jdbc.query(SHIPPING_METHOD, ShippingMethodRow.mapper(), userId);
            });
            pref.put("shippingMethodPref", shipBox[0].stream()
                    .map(s -> Map.<String, Object>of("method",
                            sanitize(s.method(), FieldType.FREE_TEXT), "count", s.count()))
                    .collect(Collectors.toList()));

            // active_hours
            final List<ActiveHoursRow>[] hoursBox = new List[]{List.of()};
            recordDbQuery("active_hours", () -> {
                hoursBox[0] = jdbc.query(ACTIVE_HOURS, ActiveHoursRow.mapper(), userId);
            });
            pref.put("activeHours", hoursBox[0].stream()
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
            renderer.appendSection(sb, "## 价格区间\n", pref.get("priceRange"), renderer::formatPriceRange);
            renderer.appendSection(sb, "## 偏好类目\n", pref.get("topCategories"),
                    v -> renderer.formatList((List<?>) v, "categoryName", "spend"));
            renderer.appendSection(sb, "## 偏好商家\n", pref.get("topMerchants"),
                    v -> renderer.formatList((List<?>) v, "merchantName", "spend"));
            renderer.appendSection(sb, "## 偏好尺码\n", pref.get("preferredSizes"),
                    v -> renderer.formatList((List<?>) v, "categoryName", "size"));
            renderer.appendSection(sb, "## 活跃时段\n", pref.get("activeHours"),
                    v -> String.join(",", ((List<?>) v).stream().map(String::valueOf).collect(Collectors.toList())));
            renderer.appendSection(sb, "## 退货率\n", pref.get("returnRate"), renderer::formatReturnRate);
            renderer.appendSection(sb, "## 偏好支付\n", pref.get("paymentMethodPref"),
                    v -> renderer.formatList((List<?>) v, "method", "count"));
            renderer.appendSection(sb, "## 偏好配送\n", pref.get("shippingMethodPref"),
                    v -> renderer.formatList((List<?>) v, "method", "count"));

            String fullText = sb.toString();
            String truncated = renderer.truncate(fullText);
            // 截断后如果 text 变短,根据被丢 section 计数
            if (!truncated.equals(fullText)) {
                if (!truncated.contains("## 偏好商家\n")) {
                    meter.counter("ai_memory_overflow_drop_total",
                            "field", "top_merchants").increment();
                }
                if (!truncated.contains("## 偏好尺码\n")) {
                    meter.counter("ai_memory_overflow_drop_total",
                            "field", "preferred_sizes").increment();
                }
                if (!truncated.contains("## 活跃时段\n")) {
                    meter.counter("ai_memory_overflow_drop_total",
                            "field", "active_hours").increment();
                }
            }
            // B3 step10: 记录注入 prompt 的 token 数
            injectionTokenSummary.record(PromptSectionRenderer.estimateTokens(truncated));
            return truncated;
        } catch (Exception e) {
            log.error("[AI][MEMORY] render failed", e);
            return "";
        }
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