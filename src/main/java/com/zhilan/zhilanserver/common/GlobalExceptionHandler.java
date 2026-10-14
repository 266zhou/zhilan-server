package com.zhilan.zhilanserver.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：code={}, msg={}",e.getCode(),e.getMessage());
        return Result.fail(e.getCode(),e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400,msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegaArgument(IllegalArgumentException e) {
        return  Result.fail(400,e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
       log.error("系统异常",e);
       return Result.fail(500,"系统繁忙，请稍后再试");
    }
}