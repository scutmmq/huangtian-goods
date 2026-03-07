package com.scutmmq.exception;

import com.scutmmq.entity.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    public Result exceptionHandler(Exception e, HttpServletRequest request){
        log.error("捕获到异常:{}", e.getMessage());

        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/druid/")) {
            // 抛出原异常，让 Druid 的 Servlet 处理
            throw new RuntimeException(e);
        }
        return Result.error("系统异常,请联系管理员");
    }

    @ExceptionHandler
    public Result SQLIntegrityConstraintViolationExceptionHandler(DuplicateKeyException e){
        log.error("捕获到异常:{}", e.getMessage());
        String msg = e.getMessage();
        String message = e.getMessage();
        int i = message.indexOf("Duplicate entry");
        String errorMsg = message.substring(i);
        String[] arr =errorMsg.split(" ");

        return  Result.error(arr[2]+"已经存在");
    }

    @ExceptionHandler
    public Result BusinessExceptionHandler(BusinessException e){
        log.warn("订单业务异常:{}",e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler
    public Result AuthorizeExceptionHandler(AuthorizeException e){
        return Result.error("登录异常:"+e.getMessage());
    }

}
