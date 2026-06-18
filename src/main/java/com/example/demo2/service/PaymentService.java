package com.example.demo2.service;

import com.example.demo2.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserRepository userRepository;

    // Constructor Injection: أفضل من @Autowired على الحقل
    public PaymentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

   
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
