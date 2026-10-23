package com.zhilan.zhilanserver.constant;

/**
 * Redis Key 前缀常量
 */

public final class RedisKeyConstants {

    /** 工具类，禁止实例化 */
    private RedisKeyConstants() {
    }

    /** 商家详情缓存：cache:shop:{id} */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /** 商家详情缓存逻辑过期时间（秒） */
    public static final Long CACHE_SHOP_TTL = 1800L;

    /** 秒杀库存：seckill:stock:{voucherId} */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /** 秒杀已下单用户集合：seckill:order:{voucherId}（Set 类型） */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    /** 秒杀券详情缓存：cache:voucher:{id} */
    public static final String CACHE_VOUCHER_KEY = "cache:voucher:";
}
