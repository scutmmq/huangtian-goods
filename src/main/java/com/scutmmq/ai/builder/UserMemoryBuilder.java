package com.scutmmq.ai.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scutmmq.ai.mapper.UserMemoryMapper;
import com.scutmmq.ai.observability.UserMemoryMetrics;
import com.scutmmq.ai.security.PromptSanitizer;
import com.scutmmq.ai.security.PromptSanitizer.FieldType;
import com.scutmmq.ai.service.AuditService;
import com.scutmmq.ai.service.UserMemorySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
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
 * B3 step4: 用户长期记忆画像构建器。
 *
 * <p>SQL 常量见 {@link UserMemorySql};聚合行类型见 {@link UserMemoryRow};
 * 指标见 {@link UserMemoryMetrics};SQL 渲染见 {@link PromptSectionRenderer}。
 */
@Slf4j
@Component
public class UserMemoryBuilder implements com.scutmmq.ai.service.UserMemoryBuilder {

    private final JdbcTemplate jdbc;
    @SuppressWarnings("unused") // 注入以满足 spec 期望(mapper.xml 复用入口)
    private final UserMemoryMapper mapper;
    private final PromptSanitizer sanitizer;
    private final ObjectMapper json;
    private final AuditService auditService;
    private final PromptSectionRenderer renderer;
    private final UserMemoryMetrics metrics;

    public UserMemoryBuilder(JdbcTemplate jdbc, UserMemoryMapper mapper, PromptSanitizer sanitizer,
                             ObjectMapper json, AuditService auditService, PromptSectionRenderer renderer,
                             UserMemoryMetrics metrics) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.sanitizer = sanitizer;
        this.json = json;
        this.auditService = auditService;
        this.renderer = renderer;
        this.metrics = metrics;
    }

    private void recordDbQuery(String sqlName, Runnable query) {
        metrics.recordDbQuery(sqlName, query);
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
                // 留给外层 catch 走 JSON OVERFLOW 降级
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
                priceBox[0] = jdbc.queryForObject(PRICE_RANGE, UserMemoryRow.PriceRangeRow.mapper(), userId);
            });
            pref.put("priceRange", orEmpty(priceBox[0]));

            // top_categories
            final List<UserMemoryRow.CategoryRow>[] catsBox = new List[]{List.of()};
            recordDbQuery("top_categories", () -> {
                catsBox[0] = jdbc.query(TOP_CATEGORIES, UserMemoryRow.CategoryRow.mapper(), userId);
            });
            List<UserMemoryRow.CategoryRow> cats = catsBox[0];
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
            final List<UserMemoryRow.MerchantRow>[] mersBox = new List[]{List.of()};
            recordDbQuery("top_merchants", () -> {
                mersBox[0] = jdbc.query(TOP_MERCHANTS, UserMemoryRow.MerchantRow.mapper(), userId);
            });
            List<UserMemoryRow.MerchantRow> mers = mersBox[0];
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
                retBox[0] = jdbc.queryForObject(RETURN_RATE, UserMemoryRow.ReturnRateRow.mapper(), userId);
            });
            pref.put("returnRate", orEmpty(retBox[0]));

            // payment_method
            final List<UserMemoryRow.PaymentMethodRow>[] payBox = new List[]{List.of()};
            recordDbQuery("payment_method", () -> {
                payBox[0] = jdbc.query(PAYMENT_METHOD, UserMemoryRow.PaymentMethodRow.mapper(), userId);
            });
            pref.put("paymentMethodPref", payBox[0].stream()
                    .map(p -> Map.<String, Object>of("method",
                            sanitize(p.method(), FieldType.FREE_TEXT), "count", p.count()))
                    .collect(Collectors.toList()));

            // shipping_method
            final List<UserMemoryRow.ShippingMethodRow>[] shipBox = new List[]{List.of()};
            recordDbQuery("shipping_method", () -> {
                shipBox[0] = jdbc.query(SHIPPING_METHOD, UserMemoryRow.ShippingMethodRow.mapper(), userId);
            });
            pref.put("shippingMethodPref", shipBox[0].stream()
                    .map(s -> Map.<String, Object>of("method",
                            sanitize(s.method(), FieldType.FREE_TEXT), "count", s.count()))
                    .collect(Collectors.toList()));

            // active_hours
            final List<UserMemoryRow.ActiveHoursRow>[] hoursBox = new List[]{List.of()};
            recordDbQuery("active_hours", () -> {
                hoursBox[0] = jdbc.query(ACTIVE_HOURS, UserMemoryRow.ActiveHoursRow.mapper(), userId);
            });
            pref.put("activeHours", hoursBox[0].stream()
                    .map(UserMemoryRow.ActiveHoursRow::hour).collect(Collectors.toList()));

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
                if (!truncated.contains("## 偏好商家\n")) metrics.recordOverflowDrop("top_merchants");
                if (!truncated.contains("## 偏好尺码\n")) metrics.recordOverflowDrop("preferred_sizes");
                if (!truncated.contains("## 活跃时段\n")) metrics.recordOverflowDrop("active_hours");
            }
            // B3 step10: 记录注入 prompt 的 token 数
            metrics.recordInjectionTokens(PromptSectionRenderer.estimateTokens(truncated));
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