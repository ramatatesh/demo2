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




    @PostMapping("/place")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestParam(defaultValue = "1")    Long    userId,
            @RequestParam(defaultValue = "1")    Long    productId,
            @RequestParam(defaultValue = "1")    int     qty,
            @RequestParam(defaultValue = "100")  double  price,
            @RequestParam(defaultValue = "true") boolean useFix,
            @RequestParam(defaultValue = "false") boolean simulateFailure) {

        log.info(" [Controller] طلب شراء | useFix={} | simulateFailure={}", useFix, simulateFailure);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("useFix",          useFix);
        response.put("simulateFailure", simulateFailure);
        response.put("mode",            useFix ? " WITH Transaction (ACID)" : " WITHOUT Transaction");

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
                    " SAFE: ROLLBACK تم تلقائياً - قاعدة البيانات في حالة متسقة");
            } else {
                response.put("dbStatus",
                    " DANGER: لا يوجد ROLLBACK - قد تكون البيانات في حالة غير متسقة!");
            }


            return ResponseEntity.internalServerError().body(response);
        }
    }




    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getDatabaseState(
            @RequestParam(defaultValue = "1") Long userId) {

        Map<String, Object> state = new LinkedHashMap<>();

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            state.put("user_id",      userId);
            state.put("user_balance", userOpt.get().getWalletBalance() + " $");
        } else {
            state.put("user_error", "User not found: id=" + userId);
        }

        long totalOrders = saleRepository.count();
        state.put("total_orders_in_db", totalOrders);
        state.put("note", "قارن هذه القيم قبل وبعد كل اختبار");

        return ResponseEntity.ok(state);
    }




    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetData(
            @RequestParam(defaultValue = "1")       Long       userId,
            @RequestParam(defaultValue = "1000.00") BigDecimal resetBalance) {

        userRepository.findById(userId).ifPresent(user -> {
            user.setWalletBalance(resetBalance);
            userRepository.save(user);
            log.info(" [Reset] رصيد userId={} أُعيد إلى {}", userId, resetBalance);
        });

        saleRepository.deleteAll(saleRepository.findByProcessedFalse());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",     "RESET DONE");
        result.put("userId",     userId);
        result.put("newBalance", resetBalance + " $");
        result.put("note",       "تم حذف Sales غير المعالجة. الآن يمكن إعادة الاختبار.");

        return ResponseEntity.ok(result);
    }
}
