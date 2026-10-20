package com.zhilan.zhilanserver.utils;


import cn.hutool.log.Log;
import com.github.benmanes.caffeine.cache.Cache;
import com.zhilan.zhilanserver.common.BusinessException;
import com.zhilan.zhilanserver.common.RedisData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 缓存工具类：封装Redis + Caffeine 的常见读写模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    /** Caffeine 一级缓存 */
    private final Cache<String,Object> localCache;

    /** 异步重建缓存用的线程池（避免每次都new Thread） */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /** 空值缓存的TTL（分钟）：防穿透用。不能太长，否则新增数据后会长期查不到 */
    private static final long NULL_CACHE_MINUTES = 2L;

    /** 重建锁的TTL（秒）：防止拿到锁的线程挂掉导致锁永不释放 */
    private static final long LOCK_SECONDS = 10L;

    // ==================== 写入 ====================

    /**
     * 写缓存（带物理TTL）
     * TTL 额外加0~60 秒随机值 -> 把过期时间“打散”，防止大量key同时失效（缓存雪崩）
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        if (value == null) {
            return;
        }
        long ttlSeconds = unit.toSeconds(time) + ThreadLocalRandom.current().nextLong(60);
        stringRedisTemplate.opsForValue().set(key,toJson(value),ttlSeconds,TimeUnit.SECONDS);
        localCache.put(key,value);
        log.info("[缓存]写入 key={}, ttl={}s",key,ttlSeconds);
    }

    /**
     * 写「逻辑过期」缓存：Redis里的 key 永不过期，过期时间存在 value 内部
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        if (value == null) {
            return;
        }
        RedisData<Object> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key,toJson(redisData));
        localCache.put(key,redisData);
        log.info("[缓存] 写入逻辑过期缓存 key ={}, 逻辑过期时间={}", key , redisData.getExpireTime());
    }

    /**
     * 删除缓存：一级和二级都要删
     */
    public void detele(String key) {
        stringRedisTemplate.delete(key);
        localCache.invalidate(key);
        log.info("[缓存] 删除 key={}",key);
    }

    // ==================== 查询 ====================
    /**
     * 查询缓存：Caffeine（一级） -> Redis（二级） -> 数据库
     * 特点：物理过期；用「空值缓存」防止缓存穿透
     *
     *  keyPrefix  key 前缀，如"cache:shop"
     *  id         业务 id
     *  type       返回值的类型，用于反序列化
     *  dbFallback 回源数据库的方法（查不到时怎么查库）
     *  time       缓存有效期
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                           Function<ID,R> dbFallback,
                                           Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1 一级缓存：Caffeine
        Object cached = localCache.getIfPresent(key);
        if (cached instanceof RedisData<?> localData && !isLogicalExpired(localData)) {
            log.info("[缓存] 一级(Caffeine)命中 key={}",key);
            return cast(localData.getData());
        }

        // 2 二级缓存：Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.1 完全没命中
        if (json == null || json.isEmpty()) {
            if (json != null) {
                //命中的是空值 -> 防穿透
                log.info("[缓存] 二级(Redis)命中【空值】 key={} —— 防穿透生效",key);
                return null;
            }
            //真的没有：回源数据库（首次加载/缓存被删）
            log.info("[缓存] 未命中，回源数据库 key={}", key);
            R value = dbFallback.apply(id);
            if (value == null) {
                stringRedisTemplate.opsForValue().set(key,"",NULL_CACHE_MINUTES,TimeUnit.MINUTES);
                log.info("[缓存] 数据库无此数据，写入空值缓存 key={}",key);
                return null;
            }
            setWithLogicalExpire(key,value,time,unit);
            return value;
        }

        // 2.2 命中了，检查逻辑是否过期
        RedisData<R> redisData = fromJsonToRedisData(json,type);

        if (!isLogicalExpired(redisData)) {
            log.info("[缓存] 二级(Redis)命中且未过期 key={}",key);
            localCache.put(key,redisData);
            return redisData.getData();
        }

        // 2.3 逻辑已过期 -> 抢锁重建
        log.info("[缓存] 二级(Redis)命中但【逻辑已过期】 key={}",key);
        String lockKey = "lock:" + key;
        if (tryLock(lockKey)) {
            //抢到锁：交给线程池【异步】重建，当前请求先返回旧数据（用户不用等）
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    log.info("[缓存] 开始异步重建 key={}",key);
                    R fresh = dbFallback.apply(id);
                    if (fresh == null) {
                        stringRedisTemplate.opsForValue()
                                .set(key,"",NULL_CACHE_MINUTES,TimeUnit.MINUTES);
                        localCache.invalidate(key);
                    } else {
                        setWithLogicalExpire(key,fresh,time,unit);
                    }
                } catch (Exception e) {
                    log.error("[缓存] 重建失败 key={}",key,e);
                } finally {
                    unlock(lockKey);
                }
            });
        } else {
            log.info("[缓存] 没抢到重建锁，直接返回旧数据 key={}",key);
        }

        //无论有没有抢到锁，都返回旧数据（这就是“逻辑过期”的核心：不阻塞用户）
        return redisData.getData();
    }

    // ==================== 私有辅助方法 ====================

    /** 判断逻辑是否已经过期 */
    private boolean isLogicalExpired(RedisData<?> redisData) {
        return redisData.getExpireTime() == null
                || redisData.getExpireTime().isBefore(LocalDateTime.now());
    }

    /** 尝试加锁（setnx + 过期时间） */
    private boolean tryLock(String lockKey) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey,"1",LOCK_SECONDS,TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /** 释放锁 */
    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    /** 对象 -> JSON */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("[缓存]序列化失败", e);
            throw new BusinessException("缓存序列化失败");
        }
    }

    /** JSON -> 对象 */
    private <R> R fromJson(String json,Class<R> type) {
        try {
            return objectMapper.readValue(json,type);
        } catch (JsonProcessingException e) {
            log.error("[缓存] 反序列化失败 json={}",json,e);
            throw new BusinessException("缓存反序列化失败");
        }
    }

    /** JSON -> RedisData<R> (带泛型，所以要构造 JavaType)  */
    private <R> RedisData<R> fromJsonToRedisData(String json, Class<R> type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory()
                    .constructParametricType(RedisData.class,type);
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            log.error("[缓存] 反序列化失败 json={}",json,e);
            throw new BusinessException("缓存反序列化失败");
        }
    }


    private <R> R cast(Object value) {
        return (R) value;
    }
}
