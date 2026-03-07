package com.scutmmq.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductAdvice {

    @Around("execution(* com.scutmmq.service.Impl.ProductServiceImpl*.*(..))")
    public Object ProductTime(ProceedingJoinPoint pjp) throws Throwable {

        final long start = System.currentTimeMillis();

        final Object result = pjp.proceed();

        final long end = System.currentTimeMillis();
//        log.info("目标对象为:{}",pjp.getTarget());
//        log.info("目标类的类名:{}",pjp.getTarget().getClass().getName());
//        log.info("目标方法的方法名:{}",pjp.getSignature().getName());
//        log.info("目标方法参数:{}",pjp.getArgs());
        log.info("方法{}执行耗时:{}ms",pjp.getSignature().getName(),end-start);

        return result;
    }

}