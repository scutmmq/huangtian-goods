package com.scutmmq.utils;


import java.util.concurrent.TimeUnit;

public class RedisConstants {

    public static final Integer CACHE_PRODUCT_DETAIL_EXPIRATION = 30;
    public static final TimeUnit  CACHE_PRODUCT_DETAIL_UNIT  = TimeUnit.MINUTES;

    public static final String CACHE_USER = "cache:user:";
    public static final String CACHE_PRODUCT_DETAIL = "cache:product:detail:";
    public static final String PRODUCT_LIKED_USER = "product:favorite:user:"; // + userId  productId score

    public static final String TOKEN_KEY = "login:token:";
    public static final Integer TOKEN_EXPIRATION = 1;
    public static final TimeUnit TOKEN_TIME_UNIT = TimeUnit.HOURS;

    // 生成订单号业务前缀
    public static final String SHOPPING_PREFIX = "shopping";

    // 用户权限
    public static final String USER_PERMISSION = "auth_write_read";

    // 库存管理
    public static final String PRODUCT_STOCK_AVAILABLE = "product:stock:available:";// + productId
    public static final String LOCK_STOCK = "lock:stock:"; // +productId
    public static final String ORDER_ID_MAP_TO_TEMP_ID = "order:id:map:to:temp:id";

    // 超时订单管理
    public static final String ORDER_TIMEOUT_TRIGGER = "order:timeout:trigger";
    public static final String ORDER_TIMEOUT_GROUP = "order:timeout:group";
    public static final String ORDER_TIMEOUT_STREAM = "order:timeout:stream";

    // 签到
    public static final String SIGN_KEY = "sign:user:";

    // 通知（Redis Stream 前缀）
    public static final String NOTIFY_USER_STREAM_PREFIX = "notify:user:";
    public static final String NOTIFY_MERCHANT_STREAM_PREFIX = "notify:merchant:";
    public static final String CACHE_ADDRESS_KEY = "cache:address:";
}
