package com.scutmmq.ai.builder;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;

/**
 * B3 step4 review-fix: UserMemoryBuilder 7 条聚合 SQL 的行类型 — 独立文件以减小主类行数。
 *
 * <p>每条记录对应一条 SQL,与 {@link UserMemorySql} 一一对应:
 * <ul>
 *   <li>{@link PriceRangeRow} — price_range(avg/p25/p50/p75/max)</li>
 *   <li>{@link CategoryRow} — top_categories(category_id/name/spend/order_count)</li>
 *   <li>{@link MerchantRow} — top_merchants(merchant_id/name/spend/order_count)</li>
 *   <li>{@link ReturnRateRow} — return_rate(total/refunded/rate)</li>
 *   <li>{@link PaymentMethodRow} — payment_method(method/count)</li>
 *   <li>{@link ShippingMethodRow} — shipping_method(method/count)</li>
 *   <li>{@link ActiveHoursRow} — active_hours(hour/count)</li>
 * </ul>
 *
 * <p>{@code mapper()} 是 Spring JDBC 的 {@link RowMapper} 工厂,
 * 处理 {@link ResultSet#getObject(String)} 返回 null 时 {@code getDouble/getLong/getInt}
 * 抛 SQLException 的边界,统一返 null。
 */
public final class UserMemoryRow {

    private UserMemoryRow() {}

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

    // ---- ResultSet 边界工具 ----

    /** NULL 列 转 null(否则 getDouble 对 null 抛 SQLException) */
    static Double dbl(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getDouble(col); } catch (Exception e) { return null; }
    }

    static Long lng(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getLong(col); } catch (Exception e) { return null; }
    }

    static Integer intOrNull(ResultSet rs, String col) {
        try { return rs.getObject(col) == null ? null : rs.getInt(col); } catch (Exception e) { return null; }
    }
}
