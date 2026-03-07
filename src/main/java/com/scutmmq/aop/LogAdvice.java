package com.scutmmq.aop;

import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.entity.OperationLog;
import com.scutmmq.enums.OperationType;
import com.scutmmq.service.OperationLogService;
import com.scutmmq.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class LogAdvice {

    private final OperationLogService operationLogService;

    public LogAdvice(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(com.scutmmq.anno.LogAnnotation) || @within(com.scutmmq.anno.LogAnnotation)")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 创建操作日志对象
        OperationLog operationLog = new OperationLog();
        
        // 获取当前用户信息
        try {
            if (UserHolder.getUser() != null) {
                operationLog.setOperationUser(UserHolder.getUser().getNickName());
                operationLog.setUserId(UserHolder.getUser().getId());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: {}", e.getMessage());
        }
        
        // 获取请求信息
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operationLog.setRequestMethod(request.getMethod());
                operationLog.setRequestUrl(request.getRequestURL().toString());
                operationLog.setOperationIp(getClientIpAddress(request));
            }
        } catch (Exception e) {
            log.warn("获取请求信息失败: {}", e.getMessage());
        }
        
        // 获取方法签名和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        // 从注解中获取操作模块和操作类型
        com.scutmmq.anno.LogAnnotation logAnnotation = method.getAnnotation(com.scutmmq.anno.LogAnnotation.class);
        if (logAnnotation == null) {
            // 检查类上的注解
            logAnnotation = joinPoint.getTarget().getClass().getAnnotation(com.scutmmq.anno.LogAnnotation.class);
        }

        LogAnnotation logAnnotationClass = joinPoint.getTarget().getClass().getAnnotation(com.scutmmq.anno.LogAnnotation.class);

        if (logAnnotationClass != null) {
            operationLog.setOperationModule(logAnnotationClass.module());
        }

        if (logAnnotation != null) {
            operationLog.setOperationType(logAnnotation.type());
            operationLog.setOperationDesc(logAnnotation.description());
        } else {
            operationLog.setOperationModule(className);
            operationLog.setOperationType(OperationType.NONE);
            operationLog.setOperationDesc(className + "." + methodName);
        }
        
        // 记录方法参数
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            StringBuilder paramBuilder = new StringBuilder();
            for (Object arg : args) {
                if (arg != null) {
                    paramBuilder.append(arg.toString()).append(";");
                }
            }
            operationLog.setRequestParam(paramBuilder.toString());
        }
        
        operationLog.setOperationTime(LocalDateTime.now());
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;
        
        try {
            log.info("开始执行方法: {}.{}", className, methodName);
            // 执行原方法
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            operationLog.setExecutionTime(executionTime);
            
            // 记录响应结果或异常信息
            if (exception != null) {
                operationLog.setResponseResult("Exception: " + exception.getMessage());
            } else if (result != null) {
                // 限制响应结果长度，避免过大
                String resultStr = result.toString();
                if (resultStr.length() > 1000) {
                    resultStr = resultStr.substring(0, 1000) + "...";
                }
                operationLog.setResponseResult(resultStr);
            }
            operationLog.setCreateTime(LocalDateTime.now());
            operationLog.setUpdateTime(LocalDateTime.now());
            
            // 输出操作日志到控制台
            log.info("操作日志: 模块={}, 类型={}, 描述={}, 执行时间={}ms, 用户={}, IP={}", 
                    operationLog.getOperationModule(), 
                    operationLog.getOperationType(), 
                    operationLog.getOperationDesc(), 
                    operationLog.getExecutionTime(),
                    operationLog.getOperationUser(),
                    operationLog.getOperationIp());
            
            // 异步保存操作日志
            try {
                operationLogService.save(operationLog);
            } catch (Exception e) {
                log.error("保存操作日志失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}