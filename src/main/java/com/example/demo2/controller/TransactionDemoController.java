package com.example.demo2.controller;

import com.example.demo2.model.User;
import com.example.demo2.repository.SaleRepository;
import com.example.demo2.repository.UserRepository;
import com.example.demo2.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════
 * REQUIREMENT 8: Transaction Integrity / ACID
 * ═══════════════════════════════════════════════════════════
 *
 * نقطة الدخول (Endpoints) للمتطلب الثامن.
 *
 * المسار الأساسي: /transaction
 *
 * ─── الـ Endpoints المتاحة ────────────────────────────────
 *
 * POST /transaction/place
 *   → نقطة الدخول الرئيسية مع Toggle بين Before/After
 *   Parameters:
 *     - userId          : رقم المستخدم
 *     - productId       : رقم المنتج
 *     - qty             : الكمية
 *     - price           : السعر
 *     - useFix          : true = مع Transaction | false = بدون Transaction
 *     - simulateFailure : true = حقن فشل في المنتصف
 *
 * GET /transaction/state
 *   → عرض الحالة الحالية لقاعدة البيانات (للمقارنة قبل/بعد)
 *
 * POST /transaction/reset
 *   → إعادة البيانات لحالتها الأصلية (لإعادة الاختبار)
 *
 * ─── سيناريو العرض الحي ───────────────────────────────────
 *
 * 1. GET  /transaction/state               → الحالة الأولية
 * 2. POST /transaction/place?useFix=false&simulateFailure=true
 *                                          → عرض المشكلة
 * 3. GET  /transaction/state               → رصيد نقص بدون طلب!
 * 4. POST /transaction/reset               → إعادة البيانات
 * 5. POST /transaction/place?useFix=true&simulateFailure=true
 *                                          → عرض الحل (ROLLBACK)
 * 6. GET  /transaction/state               → لا شيء تغير ✅
 * 7. POST /transaction/place?useFix=true&simulateFailure=false
 *                                          → طلب ناجح
 * 8. GET  /transaction/state               → كل شيء متسق ✅
 */
@RestController
@RequestMapping("/transaction")
public class TransactionDemoController {

    private static final Logger log = LoggerFactory.getLogger(TransactionDemoController.class);

    private final TransactionService transactionService;
    private final UserRepository     userRepository;
    private final SaleRepository     saleRepository;

    public TransactionDemoController(TransactionService transactionService,
                                      UserRepository userRepository,
                                      SaleRepository saleRepository) {
        this.transactionService = transactionService;
        this.userRepository     = userRepository;
        this.saleRepository     = saleRepository;
    }


    // ─── POST /transaction/place ──────────────────────────────────────────────

    /**
     * نقطة الدخول الرئيسية مع Toggle Scenario.
     *
     * useFix=false → placeOrderWithoutTransaction → يُظهر المشكلة
     * useFix=true  → placeOrderWithTransaction    → يُظهر الحل
     *
     * defaultValue="true"  → الافتراضي هو الحل الآمن
     * defaultValue="false" → لا يُحقن فشل بالافتراضي (طلب ناجح)
     */
    @PostMapping("/place")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestParam(defaultValue = "1")    Long    userId,
            @RequestParam(defaultValue = "1")    Long    productId,
            @RequestParam(defaultValue = "1")    int     qty,
            @RequestParam(defaultValue = "100")  double  price,
            @RequestParam(defaultValue = "true") boolean useFix,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {

        log.info("📨 [Controller] طلب شراء | useFix={} | simulateFailure={}", useFix, simulateFailure);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("useFix",          useFix);
        response.put("simulateFailure", simulateFailure);
        response.put("mode",            useFix ? "✅ WITH Transaction (ACID)" : "❌ WITHOUT Transaction");

        try {
            TransactionService.TransactionResult result;

            if (useFix) {
                result = transactionService.placeOrderWithTransaction(
                        userId, productId, qty, price, simulateFailure);
            } else {
                result = transactionService.placeOrderWithoutTransaction(
                        userId, productId, qty, price, simulateFailure);
            }

            response.put("status",  "SUCCESS");
            response.put("message", result.message());
            response.put("orderId", result.orderId());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            response.put("status", "FAILED");
            response.put("error",  e.getMessage());

            if (useFix) {
                response.put("dbStatus",
                    "🟢 SAFE: ROLLBACK تم تلقائياً - قاعدة البيانات في حالة متسقة");
            } else {
                response.put("dbStatus",
                    "🔴 DANGER: لا يوجد ROLLBACK - قد تكون البيانات في حالة غير متسقة!");
            }

            // HTTP 500 لكلا الحالتين لإظهار الفشل
            return ResponseEntity.internalServerError().body(response);
        }
    }


    // ─── GET /transaction/state ───────────────────────────────────────────────

    /**
     * عرض الحالة الحالية لقاعدة البيانات.
     * استخدمه قبل وبعد كل اختبار للمقارنة المباشرة.
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getDatabaseState(
            @RequestParam(defaultValue = "1") Long userId) {

        Map<String, Object> state = new LinkedHashMap<>();

        // رصيد المستخدم
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            state.put("user_id",      userId);
            state.put("user_balance", userOpt.get().getWalletBalance() + " $");
        } else {
            state.put("user_error", "User not found: id=" + userId);
        }

        // عدد الطلبات (Sales) في DB
        long totalOrders = saleRepository.count();
        state.put("total_orders_in_db", totalOrders);
        state.put("note", "قارن هذه القيم قبل وبعد كل اختبار");

        return ResponseEntity.ok(state);
    }


    // ─── POST /transaction/reset ──────────────────────────────────────────────

    /**
     * إعادة البيانات لحالتها الأصلية.
     * استخدمه بين اختبارات Before/After.
     *
     * ⚠️ يحذف كل السجلات من جدول sales (الطلبات).
     *    إذا كانت لديك بيانات مهمة في sales، أضِف شرطاً للتصفية.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetData(
            @RequestParam(defaultValue = "1")       Long       userId,
            @RequestParam(defaultValue = "1000.00") BigDecimal resetBalance) {

        // إعادة رصيد المستخدم
        userRepository.findById(userId).ifPresent(user -> {
            user.setWalletBalance(resetBalance);
            userRepository.save(user);
            log.info("🔄 [Reset] رصيد userId={} أُعيد إلى {}", userId, resetBalance);
        });

        // حذف سجلات الطلبات (Sales) غير المعالجة
        saleRepository.deleteAll(saleRepository.findByProcessedFalse());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",     "RESET DONE");
        result.put("userId",     userId);
        result.put("newBalance", resetBalance + " $");
        result.put("note",       "تم حذف Sales غير المعالجة. الآن يمكن إعادة الاختبار.");

        return ResponseEntity.ok(result);
    }
}
