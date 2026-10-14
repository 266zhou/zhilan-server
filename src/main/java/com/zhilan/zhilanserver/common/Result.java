package com.zhilan.zhilanserver.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构
 */
@Data
public class Result<T> implements Serializable {

    /** 200 成功；400 参数错误；401 未登录；500 系统错误*/
    private int code;
    private  String msg;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500,msg);
    }

    public static <T> Result<T> fail(int code,String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}