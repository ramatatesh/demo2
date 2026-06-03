package com.example.demo2.service;

import com.example.demo2.model.Sale;
import com.example.demo2.repository.SaleRepository;
import com.example.demo2.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * ═══════════════════════════════════════════════════════════
 * REQUIREMENT 8: Transaction Integrity / ACID
 * ═══════════════════════════════════════════════════════════
 *
 * هذا الـ Service هو قلب تطبيق المتطلب الثامن.
 * يحتوي على سيناريوين متكاملين:
 *
 *   ❌ placeOrderWithoutTransaction → يُظهر المشكلة (Partial Success)
 *   ✅ placeOrderWithTransaction    → يُظهر الحل (Full ACID)
 *
 * ─── لماذا نستخدم Sale كسجل طلب؟ ─────────────────────────────
 * Sale موجودة بالفعل في المشروع وتحتوي على:
 *   productId, quantity, revenue, saleDate, processed
 * تُستخدم هنا كـ "سجل الطلب" (Order Record).
 *
 * ─── آلية Transaction Propagation ────────────────────────────
 * PaymentService.processPayment()         [@Transactional REQUIRED]
 * ProductService.decreaseStockInTransaction() [@Transactional REQUIRED]
 *
 * عند استدعائهما من placeOrderWithoutTransaction() [بدون @Transactional]:
 *   → كل منهما يُنشئ transaction مستقلة ويُكمّلها → COMMIT مستقل
 *   → إذا حدث خطأ بين الاستدعاءات → Partial Success ❌
 *
 * عند استدعائهما من placeOrderWithTransaction() [@Transactional]:
 *   → كلاهما ينضمان للـ transaction الخارجية (REQUIRED)
 *   → إذا حدث خطأ → ROLLBACK لكل شيء ✅
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final PaymentService paymentService;
    private final ProductService productService;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;

    public TransactionService(PaymentService paymentService,
                               ProductService productService,
                               SaleRepository saleRepository,
                               UserRepository userRepository) {
        this.paymentService   = paymentService;
        this.productService   = productService;
        this.saleRepository   = saleRepository;
        this.userRepository   = userRepository;
    }


    // ══════════════════════════════════════════════════════════
    //  ❌ السيناريو الأول: بدون Transaction الموحدة (المشكلة)
    // ══════════════════════════════════════════════════════════

    /**
     * placeOrderWithoutTransaction:
     * ──────────────────────────────
     * يُجري عمليات الشراء الثلاث بدون transaction موحدة.
     *
     * كيف يعمل؟
     *   processPayment()              → [@Transactional REQUIRED] → ينشئ tx خاصة → COMMIT
     *   [اختياري: حقن فشل هنا]
     *   decreaseStockInTransaction()  → [@Transactional REQUIRED] → ينشئ tx خاصة → COMMIT
     *   [اختياري: حقن فشل هنا]
     *   saleRepository.save()         → لا @Transactional خارجية → طلب جديد للـ DB
     *
     * لماذا هذا خطير؟
     *   إذا حدث فشل بعد processPayment() وقبل saleRepository.save():
     *   → المال خُصم ← (محفوظ دائماً)
     *   → المخزون نقص ← (محفوظ دائماً)
     *   → لا سجل للطلب ← (لم يُنشأ)
     *   = Inconsistent State ❌
     *
     * @param simulateFailure إذا true → يحقن RuntimeException بعد خصم المال مباشرة
     */
    public TransactionResult placeOrderWithoutTransaction(
            Long userId, Long productId, int qty, double price, boolean simulateFailure) {

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("⚠️  [NO-TRANSACTION] بدء العملية بدون Transaction موحدة");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── الخطوة 1: خصم المال ──────────────────────────────
        // PaymentService.processPayment هي @Transactional REQUIRED
        // بما أننا هنا بدون @Transactional → ستُنشئ transaction مستقلة وتُكملها
        // = المال يُخصم ويُحفظ بشكل دائم (COMMITTED)
        paymentService.processPayment(userId, BigDecimal.valueOf(price));
        log.warn("   [STEP 1/3] ✅ تم خصم المال - COMMITTED بشكل مستقل ← خطر!");

        // ── حقن الفشل (للتوضيح) ──────────────────────────────
        if (simulateFailure) {
            log.error("   [INJECT]   💥 خطأ مُصطنع بعد خصم المال مباشرةً!");
            log.error("   [RESULT]   ❌ المال خُصم، لن يُنشأ أي طلب → Inconsistent State!");
            throw new RuntimeException("NO-TX SIMULATED FAILURE: Payment done, order lost!");
        }

        // ── الخطوة 2: تخفيض المخزون ─────────────────────────
        // نفس الوضع: transaction مستقلة → COMMITTED
        productService.decreaseStockInTransaction(productId, qty);
        log.warn("   [STEP 2/3] ✅ تم تخفيض المخزون - COMMITTED بشكل مستقل ← خطر!");

        // ── الخطوة 3: إنشاء سجل الطلب (Sale) ────────────────
        // save() بدون @Transactional خارجية → Spring ينشئ tx صغيرة تلقائياً
        Sale order = new Sale(productId, qty, price);
        saleRepository.save(order);
        log.warn("   [STEP 3/3] ✅ تم إنشاء سجل الطلب (Sale#{})", order.getId());

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return new TransactionResult(true, "تمت العملية (بدون حماية - خطر!)", null);
    }


    // ══════════════════════════════════════════════════════════
    //  ✅ السيناريو الثاني: مع @Transactional الموحدة (الحل)
    // ══════════════════════════════════════════════════════════

    /**
     * placeOrderWithTransaction:
     * ───────────────────────────
     * يُغلّف عمليات الشراء الثلاث داخل Transaction موحدة واحدة.
     *
     * @Transactional(isolation = REPEATABLE_READ, rollbackFor = Exception.class)
     *
     * isolation = Isolation.REPEATABLE_READ:
     *   من Slide 9 في المحاضرة:
     *   "Ensures that if you read a row twice, the data won't change in between."
     *   → نمنع Non-Repeatable Reads (تغيير رصيد المستخدم بين قراءتين)
     *   → أسرع من SERIALIZABLE (من Slide 10: "Choose Isolation Wisely")
     *
     * rollbackFor = Exception.class:
     *   → نضمن الـ ROLLBACK عند أي استثناء (وليس RuntimeException فقط)
     *
     * كيف يعمل مع الـ Propagation؟
     *   processPayment()              [@Transactional REQUIRED] → ينضم للـ TX الخارجية
     *   decreaseStockInTransaction()  [@Transactional REQUIRED] → ينضم للـ TX الخارجية
     *   saleRepository.save()         → ينفذ داخل نفس الـ TX الخارجية
     *
     *   → كل شيء في tx واحدة → إذا حدث فشل → ROLLBACK للكل ✅
     *
     * @param simulateFailure إذا true → يحقن RuntimeException → ROLLBACK لكل شيء
     */
    @Transactional(
        isolation   = Isolation.REPEATABLE_READ,
        rollbackFor = Exception.class,
        propagation = Propagation.REQUIRED
    )
    public TransactionResult placeOrderWithTransaction(
            Long userId, Long productId, int qty, double price, boolean simulateFailure) {

        log.info("═══════════════════════════════════════════════");
        log.info("🔐 [WITH-TRANSACTION] بدأت Transaction موحدة");
        log.info("   Isolation: REPEATABLE_READ | Propagation: REQUIRED");
        log.info("═══════════════════════════════════════════════");

        // ── الخطوة 1: خصم المال ──────────────────────────────
        // PaymentService.processPayment [@Transactional REQUIRED]
        // توجد transaction خارجية (هذه الدالة) → ينضم إليها بدون إنشاء جديدة
        // = المال لن يُحفظ إلا عند COMMIT الـ TX الخارجية في نهاية هذه الدالة
        paymentService.processPayment(userId, BigDecimal.valueOf(price));
        log.info("   [STEP 1/3] ✅ خُصم المال في TX Buffer (لم يُحفظ بعد)");

        // ── حقن الفشل (للتوضيح) ──────────────────────────────
        if (simulateFailure) {
            log.error("   [INJECT]   💥 خطأ مُصطنع داخل الـ Transaction!");
            log.error("   [ROLLBACK] 🔄 Spring سيُطلق ROLLBACK → المال يعود!");
            // هذا الـ RuntimeException يُخبر Spring بتنفيذ ROLLBACK لكل الـ TX
            throw new RuntimeException("WITH-TX SIMULATED FAILURE: Full ROLLBACK triggered!");
        }

        // ── الخطوة 2: تخفيض المخزون ─────────────────────────
        // productService.decreaseStockInTransaction [@Transactional REQUIRED]
        // → ينضم لنفس الـ TX الخارجية
        productService.decreaseStockInTransaction(productId, qty);
        log.info("   [STEP 2/3] ✅ خُفِّض المخزون في TX Buffer");

        // ── الخطوة 3: إنشاء سجل الطلب (Sale) ────────────────
        // save() داخل الـ @Transactional الخارجية → جزء من نفس الـ TX
        Sale order = new Sale(productId, qty, price);
        saleRepository.save(order);
        log.info("   [STEP 3/3] ✅ أُنشئ سجل الطلب في TX Buffer");

        // وصلنا هنا = كل الخطوات نجحت → Spring يُنفّذ COMMIT تلقائياً
        log.info("═══════════════════════════════════════════════");
        log.info("🎉 [COMMIT] كل الخطوات نجحت - سيُحفظ كل شيء الآن");
        log.info("═══════════════════════════════════════════════");

        return new TransactionResult(true,
                "تمت العملية بنجاح كامل مع ACID ✅ (orderId=" + order.getId() + ")",
                order.getId());
    }


    // ══════════════════════════════════════════════════════════
    //  Response Record
    // ══════════════════════════════════════════════════════════

    /**
     * TransactionResult: نموذج الاستجابة للـ API.
     * نتبع نفس أسلوب المشروع في استخدام Records (مثل CheckoutResult, BatchResult).
     */
    public record TransactionResult(
            boolean success,
            String message,
            Long orderId
    ) {}
}
