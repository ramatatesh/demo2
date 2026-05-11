package com.example.demo2;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);

    // ═══════════════════════════════════════════════════
    // ① LOGGING + ② PERFORMANCE + ③ TRACING
    // @Around = يحيط الـ method من الجانبين
    // ═══════════════════════════════════════════════════
    @Around("execution(* com.example.demo2.service.*.*(..))")
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {

        String methodName = joinPoint.getSignature().toShortString();
        String threadName = Thread.currentThread().getName();

        // ③ TRACING: نولّد traceId فريد لكل request
        // هذا هو الـ Tracing — كل request له ID مميز
        // MDC = ربط الـ ID بكل log line تلقائياً
        String traceId = UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();
        MDC.put("traceId", traceId);

        // ① LOGGING: تسجيل البداية مع الـ traceId
        logger.info("┌─[TRACE:{}]─────────────────────────────", traceId);
        logger.info("│ ▶ START  : {}", methodName);
        logger.info("│   Thread : {}", threadName);

        // ② PERFORMANCE: قياس الوقت
        long start = System.currentTimeMillis();

        // تنفيذ الـ method الأصلية (Business Logic)
        Object result = joinPoint.proceed();

        long duration = System.currentTimeMillis() - start;

        // تصنيف الأداء
        String performance = duration < 5000  ? "🟢 FAST"
                : duration < 10000 ? "🟡 MEDIUM"
                : "🔴 SLOW";
        // ① LOGGING: تسجيل النهاية
        logger.info("│ ■ END    : {}", methodName);
        logger.info("│   Time   : {} ms  {}", duration, performance);
        logger.info("└─[TRACE:{}]─────────────────────────────", traceId);

        // تنظيف الـ MDC بعد انتهاء الـ request
        MDC.remove("traceId");

        return result;
    }

    // ④ ERROR HANDLING
    // @AfterThrowing = يُنفَّذ تلقائياً لو رمت الـ method خطأ
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

    // قبل الـ Scheduled Job
    @Before("execution(* com.example.demo2.service.BatchService.scheduledDailyBatch(..))")
    public void beforeBatchJob() {
        logger.info("⏰ [AOP] Scheduled Daily Batch Job يبدأ الآن تلقائياً");
    }
}