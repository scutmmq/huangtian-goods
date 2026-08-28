package com.scutmmq.exception;

import com.scutmmq.entity.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    public Result exceptionHandler(Exception e, HttpServletRequest request){
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/druid/")) {
            // 抛出原异常，让 Druid 的 Servlet 处理
            throw new RuntimeException(e);
        }
        // 打印完整堆栈，便于排查（之前只记录 message 丢失了栈信息）
        log.error("系统异常, 请求路径:{}", requestURI, e);
        return Result.error("系统异常,请联系管理员");
    }

    @ExceptionHandler
    public Result duplicateKeyExceptionHandler(DuplicateKeyException e){
        log.warn("唯一约束冲突:{}", e.getMessage());
        return Result.error(parseDuplicateField(e.getMessage()) + "已经存在");
    }

    /**
     * 从 DuplicateKeyException 的消息中尽力解析出冲突的字段值。
     * 解析失败时回退为通用提示，避免因消息格式变化抛出 StringIndexOutOfBounds。
     */
    private String parseDuplicateField(String message){
        if (message == null) {
            return "数据";
        }
        try {
            int i = message.indexOf("Duplicate entry");
            if (i < 0) {
                return "数据";
            }
            String[] arr = message.substring(i).split(" ");
            return arr.length > 2 ? arr[2] : "数据";
        } catch (Exception ex) {
            log.warn("解析唯一约束冲突字段失败:{}", message);
            return "数据";
        }
    }

    @ExceptionHandler
    public Result businessExceptionHandler(BusinessException e){
        log.warn("业务异常:{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public Result authorizeExceptionHandler(AuthorizeException e){
        log.warn("登录异常:{}", e.getMessage());
        return Result.error("登录异常:" + e.getMessage());
    }

    /**
     * 请求参数缺失 / 类型不匹配
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Result badParamHandler(Exception e){
        log.warn("请求参数错误:{}", e.getMessage());
        return Result.error("请求参数错误");
    }

    /**
     * 请求体无法解析（如 JSON 格式错误）
     */
    @ExceptionHandler
    public Result messageNotReadableHandler(HttpMessageNotReadableException e){
        log.warn("请求体解析失败:{}", e.getMessage());
        return Result.error("请求体格式错误");
    }

    /**
     * 请求方法不支持（如对仅支持 POST 的接口发 GET）
     */
    @ExceptionHandler
    public Result methodNotSupportedHandler(HttpRequestMethodNotSupportedException e){
        log.warn("请求方法不支持:{}", e.getMessage());
        return Result.error("请求方法不支持");
    }

}
