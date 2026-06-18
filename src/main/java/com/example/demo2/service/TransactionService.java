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




    public TransactionResult placeOrderWithoutTransaction(
            Long userId, Long productId, int qty, double price, boolean simulateFailure) {

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("  [NO-TRANSACTION] بدء العملية بدون Transaction موحدة");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");



        paymentService.processPayment(userId, BigDecimal.valueOf(price));
        log.warn("   [STEP 1/3]  تم خصم المال - COMMITTED بشكل مستقل ← خطر!");


        if (simulateFailure) {
            log.error("   [INJECT]    خطأ مُصطنع بعد خصم المال مباشرةً!");
            log.error("   [RESULT]    المال خُصم، لن يُنشأ أي طلب → Inconsistent State!");
            throw new RuntimeException("NO-TX SIMULATED FAILURE: Payment done, order lost!");
        }


        productService.decreaseStockInTransaction(productId, qty);
        log.warn("   [STEP 2/3]  تم تخفيض المخزون - COMMITTED بشكل مستقل ← خطر!");


        Sale order = new Sale(productId, qty, price);
        saleRepository.save(order);
        log.warn("   [STEP 3/3]  تم إنشاء سجل الطلب (Sale#{})", order.getId());

        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return new TransactionResult(true, "تمت العملية (بدون حماية - خطر!)", null);
    }





    @Transactional(
        isolation   = Isolation.REPEATABLE_READ,
        rollbackFor = Exception.class,
        propagation = Propagation.REQUIRED
    )
    public TransactionResult placeOrderWithTransaction(
            Long userId, Long productId, int qty, double price, boolean simulateFailure) {

        log.info("═══════════════════════════════════════════════");
        log.info(" [WITH-TRANSACTION] بدأت Transaction موحدة");
        log.info("   Isolation: REPEATABLE_READ | Propagation: REQUIRED");
        log.info("═══════════════════════════════════════════════");


        paymentService.processPayment(userId, BigDecimal.valueOf(price));
        log.info("   [STEP 1/3] خُصم المال في TX Buffer (لم يُحفظ بعد)");


        if (simulateFailure) {
            log.error("   [INJECT]    خطأ مُصطنع داخل الـ Transaction!");
            log.error("   [ROLLBACK]  Spring سيُطلق ROLLBACK → المال يعود!");
            throw new RuntimeException("WITH-TX SIMULATED FAILURE: Full ROLLBACK triggered!");
        }



        productService.decreaseStockInTransaction(productId, qty);
        log.info("   [STEP 2/3]  خُفِّض المخزون في TX Buffer");


        Sale order = new Sale(productId, qty, price);
        saleRepository.save(order);
        log.info("   [STEP 3/3]  أُنشئ سجل الطلب في TX Buffer");

        log.info("═══════════════════════════════════════════════");
        log.info(" [COMMIT] كل الخطوات نجحت - سيُحفظ كل شيء الآن");
        log.info("═══════════════════════════════════════════════");

        return new TransactionResult(true,
                "تمت العملية بنجاح كامل مع ACID  (orderId=" + order.getId() + ")",
                order.getId());
    }


    public record TransactionResult(
            boolean success,
            String message,
            Long orderId
    ) {}
}
