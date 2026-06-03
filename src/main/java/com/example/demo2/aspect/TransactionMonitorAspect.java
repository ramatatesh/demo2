package com.example.demo2.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ═══════════════════════════════════════════════════════════
 * REQUIREMENT 8: Transaction Integrity / ACID
 * ═══════════════════════════════════════════════════════════
 *
 * TransactionMonitorAspect:
 * ─────────────────────────
 * Aspect مخصص لمراقبة الـ Transaction ويُكمّل PerformanceAspect الموجود.
 *
 * ─── لماذا Aspect منفصل وليس إضافة لـ PerformanceAspect؟ ──
 *   PerformanceAspect → مسؤول عن الأداء والـ Tracing العام (موجود)
 *   TransactionMonitorAspect → مسؤول تحديداً عن:
 *     - إظهار BEGIN/COMMIT/ROLLBACK بشكل واضح
 *     - مقارنة Before/After الـ Transaction
 *     - تحذيرات خاصة بـ Atomicity violations
 *   هذا تطبيق مبدأ Single Responsibility في الـ AOP.
 *
 * ─── @Order(2) ─────────────────────────────────────────────
 *   Spring يُنفّذ الـ Aspects حسب الـ Order.
 *   PerformanceAspect ليس له @Order → افتراضي = Ordered.LOWEST_PRECEDENCE
 *   @Order(2) → هذا الـ Aspect يُنفَّذ قبل PerformanceAspect في الدخول
 *               وبعده في الخروج (LIFO - stack behavior).
 *
 * ─── Cross-Cutting Concerns المُطبَّقة هنا ──────────────────
 *   1. Transaction Lifecycle Logging (BEGIN/COMMIT/ROLLBACK)
 *   2. Atomicity Violation Detection (التحذير عند Partial Failure)
 *   3. Thread Tracing (تتبع أي Thread ينفذ الـ Transaction)
 */
@Aspect
@Component
@Order(2)   // يُنفَّذ بعد @Order(1) إذا وُجد، ويُكمّل PerformanceAspect
public class TransactionMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionMonitorAspect.class);


    // ── Pointcuts ────────────────────────────────────────────

    /**
     * Pointcut خاص بـ TransactionService فقط.
     * نتجنب تداخل مع Pointcut الـ PerformanceAspect العام.
     */
    @Pointcut("execution(* com.example.demo2.service.TransactionService.*(..))")
    public void transactionServiceMethods() {}

    /**
     * Pointcut للدوال التي تحمل @Transactional.
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalAnnotated() {}


    // ── Around Advice: Transaction Lifecycle ─────────────────

    /**
     * يُغلّف دوال TransactionService المُعلَّمة بـ @Transactional.
     * يُظهر BEGIN/COMMIT/ROLLBACK بشكل واضح في الـ logs.
     *
     * لماذا @Around وليس @Before + @After؟
     *   @Around يُتيح التحكم الكامل: يُنفَّذ قبل وبعد وعند الاستثناء،
     *   ويمنحنا الوقت الدقيق للـ Transaction بالكامل.
     */
    @Around("transactionServiceMethods() && transactionalAnnotated()")
    public Object monitorTransactionLifecycle(ProceedingJoinPoint joinPoint) throws Throwable {

        String methodName = joinPoint.getSignature().getName();
        String threadName = Thread.currentThread().getName();
        long   threadId   = Thread.currentThread().getId();

        // ── قبل التنفيذ: إعلان بداية الـ Transaction
        log.info("╔══ [TX-MONITOR] TRANSACTION BEGIN ══════════════════╗");
        log.info("║ Method : {}", methodName);
        log.info("║ Thread : {} (ID: {})", threadName, threadId);
        log.info("║ Isolation: REPEATABLE_READ");
        log.info("╟────────────────────────────────────────────────────╢");

        long start = System.nanoTime();

        try {
            // تنفيذ الدالة الأصلية
            Object result = joinPoint.proceed();

            long durationMs = (System.nanoTime() - start) / 1_000_000;

            // ── نجح: COMMIT
            log.info("╟────────────────────────────────────────────────────╢");
            log.info("║ ✅ COMMIT: كل العمليات نجحت وحُفظت في DB");
            log.info("║ Duration: {} ms", durationMs);
            log.info("╚══ [TX-MONITOR] TRANSACTION COMMITTED ══════════════╝");

            return result;

        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            // ── فشل: ROLLBACK (Spring سيُنفّذه تلقائياً)
            log.error("╟────────────────────────────────────────────────────╢");
            log.error("║ ❌ ROLLBACK TRIGGERED: {}", ex.getMessage());
            log.error("║ Duration before failure: {} ms", durationMs);
            log.error("║ Spring سيُلغي كل التغييرات وسيعود DB لحالته الأولى");
            log.error("╚══ [TX-MONITOR] TRANSACTION ROLLED BACK ════════════╝");

            // أعِد رمي الاستثناء لأن Spring يحتاجه لتنفيذ ROLLBACK
            throw ex;
        }
    }


    // ── Before Advice: إظهار الـ Thread في دوال بدون Transaction ──

    /**
     * يُسجّل بداية الدوال في TransactionService التي لا تحمل @Transactional
     * (أي placeOrderWithoutTransaction) لإظهار غياب الحماية.
     */
    @Before("transactionServiceMethods() && !transactionalAnnotated()")
    public void warnNoTransaction(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        if (!methodName.contains("Transaction") && !methodName.contains("reset")) return;

        log.warn("╔══ [TX-MONITOR] ⚠️  NO TRANSACTION ══════════════════╗");
        log.warn("║ Method : {}", methodName);
        log.warn("║ Thread : {}", Thread.currentThread().getName());
        log.warn("║ ⚠️  كل عملية ستُنفَّذ في transaction مستقلة         ║");
        log.warn("║ ⚠️  أي فشل سيُسبب Partial Success / Data Corruption  ║");
        log.warn("╚══════════════════════════════════════════════════════╝");
    }


    // ── AfterThrowing: تحذير Atomicity Violation ─────────────

    /**
     * يُنفَّذ عند أي استثناء في TransactionService.
     * يُميّز بين:
     *   - الفشل داخل @Transactional → ROLLBACK آمن ✅
     *   - الفشل خارج @Transactional → Partial Success خطير ❌
     *
     * ملاحظة: PerformanceAspect لديه @AfterThrowing على service.*
     * هذا الـ @AfterThrowing أكثر تخصصاً ويُضيف سياق Transaction.
     */
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
            log.error("   Status: 🟢 PROTECTED - ROLLBACK سيُعيد كل شيء لحالته الأصلية");
            log.error("   ACID  : Atomicity مُحفوظة ✅");
        } else {
            log.error("   Status: 🔴 UNPROTECTED - Partial Success حدث!");
            log.error("   ACID  : Atomicity مُنتهَكة ❌ - قد تكون البيانات تالفة");
        }
        log.error("────────────────────────────────────────────────────");
        log.error("");
    }
}
