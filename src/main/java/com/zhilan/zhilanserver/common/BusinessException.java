package com.zhilan.zhilanserver.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转成 Result 返回
 */

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String msg) {
        this(500,msg);
    }

    public BusinessException(int code,String msg) {
        super(msg);
        this.code = code;
    }
}