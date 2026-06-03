package com.example.demo2.service;

import com.example.demo2.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * ─────────────────────────────────────────────────
 * REQUIREMENT 8: Transaction Integrity / ACID
 * ─────────────────────────────────────────────────
 * خدمة الدفع المسؤولة عن خصم المبلغ من محفظة المستخدم.
 *
 * لماذا service منفصلة للدفع؟
 * ─────────────────────────────
 * بسبب كيفية عمل Spring Transaction Propagation:
 *
 * الحالة A (بدون Transaction خارجية - سيناريو المشكلة):
 *   TransactionService.placeOrderWithoutTransaction() [لا @Transactional]
 *     └─ PaymentService.processPayment() [@Transactional REQUIRED]
 *           → لا توجد transaction خارجية، فينشئ transaction جديدة ويُكمّلها (COMMIT).
 *           → المال يُخصم ويُحفظ بشكل دائم ومستقل.
 *
 * الحالة B (مع Transaction خارجية - سيناريو الحل):
 *   TransactionService.placeOrderWithTransaction() [@Transactional]
 *     └─ PaymentService.processPayment() [@Transactional REQUIRED]
 *           → توجد transaction خارجية، ينضم إليها دون إنشاء transaction جديدة.
 *           → المال لا يُحفظ إلا عند COMMIT الـ transaction الخارجية.
 *           → إذا حدث خطأ → ROLLBACK يُعيد المال.
 *
 * هذا هو جوهر الـ Transaction Propagation في Spring.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserRepository userRepository;

    // Constructor Injection: أفضل من @Autowired على الحقل
    public PaymentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * processPayment: خصم المبلغ من محفظة المستخدم.
     *
     * @Transactional(propagation = Propagation.REQUIRED):
     *   - إذا كانت هناك transaction خارجية → انضم إليها (الوضع الطبيعي).
     *   - إذا لم تكن → أنشئ transaction جديدة.
     *   - REQUIRED هو الافتراضي لكن نكتبه صراحةً للوضوح في الكود.
     *
     * rollbackFor = Exception.class:
     *   - الافتراضي في Spring هو Rollback عند RuntimeException فقط.
     *   - نُضيف هذا لضمان ROLLBACK عند أي نوع استثناء.
     *
     * @param userId رقم المستخدم
     * @param amount المبلغ المراد خصمه
     * @throws RuntimeException إذا كان الرصيد غير كافٍ → يُشغّل ROLLBACK
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void processPayment(Long userId, BigDecimal amount) {
        log.info("   💳 [PaymentService] بدء خصم {} من userId={}", amount, userId);

        // deductBalance يُرجع عدد الصفوف المُعدَّلة
        int rowsAffected = userRepository.deductBalance(userId, amount);

        if (rowsAffected == 0) {
            // 0 صفوف = الرصيد غير كافٍ أو المستخدم غير موجود
            log.error("   ❌ [PaymentService] فشل الدفع - رصيد غير كافٍ: userId={}, amount={}", userId, amount);
            throw new RuntimeException("Payment failed: Insufficient balance for userId=" + userId);
        }

        log.info("   ✅ [PaymentService] نجح خصم {} من userId={}", amount, userId);
    }
}
