package com.zhilan.zhilanserver.constant;

/**
 * Redis Key 前缀常量
 */

public final class RedisKeyConstants {

    /** 工具类，禁止实例化 */
    private RedisKeyConstants() {
    }

    /** 商家详情缓存：cache:shop:{id} */
    public static final String CACHE_SHOP_KEY = "chche:shop";

    /** 商家详情缓存逻辑过期时间（秒） */
    public static final Long CACHE_SHOP_TTL = 1800L;
}
