package com.zhilan.zhilanserver.common;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 逻辑过期包装类：把「逻辑过期时间」和「真实数据」一起缓存
 *
 * @param<T> 真实的数据类型
 */
@Data
public class RedisData<T> implements Serializable {

    /** 逻辑过期时间：Redis 里的 key 本身永不过期，靠这个字段判断“逻辑上是否过期” */
    private LocalDateTime expireTime;

    /** 真实业务数据 */
    private T data;
}
