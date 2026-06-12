package com.example.demo2;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);

    private static final ConcurrentHashMap<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();

    public static void resetMetrics() {
        callCounts.clear();
        totalDurations.clear();
    }

    public static long getAverageDuration(String methodName) {
        AtomicInteger count = callCounts.get(methodName);
        AtomicLong duration = totalDurations.get(methodName);
        if (count == null || count.get() == 0) return 0;
        return duration.get() / count.get();
    }

    public static int getCallCount(String methodName) {
        AtomicInteger count = callCounts.get(methodName);
        return count == null ? 0 : count.get();
    }

    @Around("execution(* com.example.demo2..*.*(..)) && " +
            "!execution(* com.example.demo2.loadbalancer.VirtualServer.*(..))")

    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {

        String methodName = joinPoint.getSignature().toShortString();
        String threadName = Thread.currentThread().getName();

        String traceId = UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();
        MDC.put("traceId", traceId);

        logger.info("┌─[TRACE:{}]─────────────────────────────", traceId);
        logger.info("│ START  : {}", methodName);
        logger.info("│ Thread : {}", threadName);

        long start = System.currentTimeMillis();


        Object result = joinPoint.proceed();

        long duration = System.currentTimeMillis() - start;
        long end = System.currentTimeMillis();


        callCounts.computeIfAbsent(methodName, k -> new AtomicInteger(0)).incrementAndGet();
        totalDurations.computeIfAbsent(methodName, k -> new AtomicLong(0)).addAndGet(duration);


        String performance = duration < 100  ? " FAST"
                : duration < 600 ? " MEDIUM"
                : " SLOW";

        logger.info("│END    : {}", methodName);
        logger.info("│   Time   : {} ms  {}", duration, performance);
        logger.info("└─[TRACE:{}]─────────────────────────────", traceId);
        logger.info("AOP -> {} executed in {} ms",
                joinPoint.getSignature(),
                (end - start));
        MDC.remove("traceId");

        return result;
    }

    @AfterThrowing(
            pointcut = "execution(* com.example.demo2.service.*.*(..))",
            throwing  = "ex"
    )
    public void logError(JoinPoint joinPoint, Exception ex) {
        String methodName = joinPoint.getSignature().toShortString();
        String traceId    = MDC.get("traceId") != null
                ? MDC.get("traceId") : "N/A";

        logger.error("╔══ ERROR [TRACE:{}] ══════════════════════", traceId);
        logger.error("║ Method : {}", methodName);
        logger.error("║ Error  : {}", ex.getMessage());
        logger.error("╚═════════════════════════════════════════");
    }

    @Before("execution(* com.example.demo2.service.BatchService.scheduledDailyBatch(..))")
    public void beforeBatchJob() {
        logger.info(" [AOP] Scheduled Daily Batch Job يبدأ الآن تلقائياً");
    }
}