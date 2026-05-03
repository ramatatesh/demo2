package com.example.demo2;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);

    // ✅ الإصلاح: أضفنا service.* حتى يشمل الـ subpackage
    @Around("execution(* com.example.demo2.service.*.*(..))")
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {

        String methodName = joinPoint.getSignature().toShortString();
        String threadName = Thread.currentThread().getName();

        long start = System.currentTimeMillis();

        Object result = joinPoint.proceed(); // تنفيذ الـ method الأصلية

        long duration = System.currentTimeMillis() - start;

        // تصنيف الأداء
        String performance = duration < 100  ? "🟢 FAST"
                : duration < 1000 ? "🟡 MEDIUM"
                : "🔴 SLOW";

        logger.info("╔══ AOP MONITOR ══════════════════════════════");
        logger.info("║ Method  : {}", methodName);
        logger.info("║ Duration: {} ms  {}", duration, performance);
        logger.info("║ Thread  : {}", threadName);
        logger.info("╚═════════════════════════════════════════════");

        return result;
    }
}