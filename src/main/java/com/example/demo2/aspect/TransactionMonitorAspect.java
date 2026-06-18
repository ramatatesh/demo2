package com.example.demo2.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class TransactionMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionMonitorAspect.class);



    @Pointcut("execution(* com.example.demo2.service.TransactionService.*(..))")
    public void transactionServiceMethods() {}

    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalAnnotated() {}


    @Around("transactionServiceMethods() && transactionalAnnotated()")
    public Object monitorTransactionLifecycle(ProceedingJoinPoint joinPoint) throws Throwable {

        String methodName = joinPoint.getSignature().getName();
        String threadName = Thread.currentThread().getName();
        long   threadId   = Thread.currentThread().getId();

        log.info("╔══ [TX-MONITOR] TRANSACTION BEGIN ══════════════════╗");
        log.info("║ Method : {}", methodName);
        log.info("║ Thread : {} (ID: {})", threadName, threadId);
        log.info("║ Isolation: REPEATABLE_READ");
        log.info("╟────────────────────────────────────────────────────╢");

        long start = System.nanoTime();

        try {
            Object result = joinPoint.proceed();

            long durationMs = (System.nanoTime() - start) / 1_000_000;

            log.info("╟────────────────────────────────────────────────────╢");
            log.info("║  COMMIT: كل العمليات نجحت وحُفظت في DB");
            log.info("║ Duration: {} ms", durationMs);
            log.info("╚══ [TX-MONITOR] TRANSACTION COMMITTED ══════════════╝");

            return result;

        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            log.error("╟────────────────────────────────────────────────────╢");
            log.error("║  ROLLBACK TRIGGERED: {}", ex.getMessage());
            log.error("║ Duration before failure: {} ms", durationMs);
            log.error("║ Spring سيُلغي كل التغييرات وسيعود DB لحالته الأولى");
            log.error("╚══ [TX-MONITOR] TRANSACTION ROLLED BACK ════════════╝");

            throw ex;
        }
    }

    @Before("transactionServiceMethods() && !transactionalAnnotated()")
    public void warnNoTransaction(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        if (!methodName.contains("Transaction") && !methodName.contains("reset")) return;

        log.warn("╔══ [TX-MONITOR]   NO TRANSACTION ══════════════════╗");
        log.warn("║ Method : {}", methodName);
        log.warn("║ Thread : {}", Thread.currentThread().getName());
        log.warn("║   كل عملية ستُنفَّذ في transaction مستقلة         ║");
        log.warn("║ ️  أي فشل سيُسبب Partial Success / Data Corruption  ║");
        log.warn("╚══════════════════════════════════════════════════════╝");
    }




    @AfterThrowing(
        pointcut  = "transactionServiceMethods()",
        throwing  = "ex"
    )
    public void detectAtomicityViolation(JoinPoint joinPoint, Throwable ex) {
        String methodName = joinPoint.getSignature().getName();
        boolean isProtected = methodName.contains("WithTransaction");

        log.error("");
        log.error("⚡ [TX-MONITOR] ATOMICITY CHECK ─────────────────────");
        log.error("   Method: {}", methodName);
        log.error("   Error : {}", ex.getMessage());

        if (isProtected) {
            log.error("   Status:  PROTECTED - ROLLBACK سيُعيد كل شيء لحالته الأصلية");
            log.error("   ACID  : Atomicity مُحفوظة ");
        } else {
            log.error("   Status:  UNPROTECTED - Partial Success حدث!");
            log.error("   ACID  : Atomicity مُنتهَكة  - قد تكون البيانات تالفة");
        }
        log.error("────────────────────────────────────────────────────");
        log.error("");
    }
}
