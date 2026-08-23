package com.scutmmq.ai.builder;

/**
 * B3 step4: UserMemoryBuilder 用到的 SQL 常量与 RowMapper 工具类。
 *
 * <p>与 resources/com/scutmmq/ai/mapper/UserMemoryMapper.xml 保持同步 —
 * 两边 SQL 字符串一字不差,mapper.xml 供 MyBatis 直接使用,
 * 本文件供 {@link UserMemoryBuilder} 通过 JdbcTemplate 调用(便于单测 mock)。
 *
 * <p>所有聚合 SQL 都过滤:
 * <pre>
 *   user_id = ?
 *   AND ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
 *   AND status IN ('paid','shipped','delivered')
 * </pre>
 */
public final class UserMemorySql {

    private UserMemorySql() {}

    // ============================ 聚合 SQL(7 条)============================

    /** 1. 价格区间:AVG + p25/p50/p75 + MAX,排除 0 元订单 */
    public static final String PRICE_RANGE = "SELECT "
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

    /** 2. 偏好类目:JOIN 4 表,按 spend 排序 LIMIT 3 */
    public static final String TOP_CATEGORIES = "SELECT pc.id AS categoryId, pc.name AS categoryName, "
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

    /** 3. 偏好商家:JOIN orders / merchant,按 spend 排序 LIMIT 3 */
    public static final String TOP_MERCHANTS = "SELECT m.id AS merchantId, m.name AS merchantName, "
            + "SUM(o.total_amount) AS spend, COUNT(DISTINCT o.id) AS orderCount "
            + "FROM orders o "
            + "JOIN merchant m ON m.id = o.merchant_id "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY m.id, m.name "
            + "ORDER BY spend DESC LIMIT 3";

    /** 4. 退货率:所有订单总数 + 已退款数 + 比例 */
    public static final String RETURN_RATE = "SELECT "
            + "COUNT(*) AS total, "
            + "SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END) AS refunded, "
            + "ROUND(SUM(CASE WHEN o.payment_status = 'refunded' THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(*), 0), 4) AS rate "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered')";

    /** 5. 偏好支付方式:GROUP BY payment_method */
    public static final String PAYMENT_METHOD = "SELECT o.payment_method AS method, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY o.payment_method ORDER BY count DESC";

    /** 6. 偏好配送方式:GROUP BY shipping_method(忽略 NULL) */
    public static final String SHIPPING_METHOD = "SELECT o.shipping_method AS method, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "AND o.shipping_method IS NOT NULL "
            + "GROUP BY o.shipping_method ORDER BY count DESC";

    /** 7. 活跃时段:按 HOUR(ordered_at) 分组 TOP 5 */
    public static final String ACTIVE_HOURS = "SELECT HOUR(o.ordered_at) AS hour, COUNT(*) AS count "
            + "FROM orders o "
            + "WHERE o.user_id = ? "
            + "AND o.ordered_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) "
            + "AND o.status IN ('paid','shipped','delivered') "
            + "GROUP BY HOUR(o.ordered_at) ORDER BY count DESC LIMIT 5";

    // ============================ identity SQL ============================

    public static final String USER = "SELECT id, nick_name, gender, birthday, created_at "
            + "FROM user WHERE id = ?";

    public static final String DEFAULT_ADDRESS = "SELECT recipient, phone, province, city, district, detail "
            + "FROM user_address WHERE user_id = ? AND is_default = 1 LIMIT 1";

    public static final String USER_MERCHANT = "SELECT id, name FROM merchant WHERE user_id = ? LIMIT 1";
}
