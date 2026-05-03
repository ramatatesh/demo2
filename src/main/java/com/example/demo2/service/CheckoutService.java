package com.example.demo2.service;
import com.example.demo2.model.NotificationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    public final AsyncQueueService queueService;

    public CheckoutService(AsyncQueueService queueService) {
        this.queueService = queueService;
    }

    // ══════════════════════════════════════
    // BEFORE: Synchronous - المستخدم ينتظر كل شيء
    // ══════════════════════════════════════
    public CheckoutResult checkoutLegacy(Long productId, String email) throws InterruptedException {
        long start = System.currentTimeMillis();

        log.info("🔴 [LEGACY] Starting synchronous checkout...");

        // [1] الخطوة الضرورية: التحقق من المخزون (سريع)
        Thread.sleep(50);
        log.info("✅ [1/4] Stock validated (50ms)");

        // [2] المستخدم ينتظر: إنشاء الفاتورة (بطيء)
        Thread.sleep(2000);
        log.info("✅ [2/4] Invoice generated (2000ms)");

        // [3] المستخدم ينتظر: إرسال الإيميل (بطيء)
        Thread.sleep(1500);
        log.info("✅ [3/4] Email sent (1500ms)");

        // [4] المستخدم ينتظر: إشعار المستودع (بطيء)
        Thread.sleep(800);
        log.info("✅ [4/4] Warehouse notified (800ms)");

        long totalTime = System.currentTimeMillis() - start;
        log.info("🔴 [LEGACY] Total time: {}ms", totalTime);

        return new CheckoutResult(
                "Order confirmed ✅ (Legacy)",
                totalTime,
                "SYNCHRONOUS"
        );
    }

    // ══════════════════════════════════════
    // AFTER: Asynchronous Queue - المستخدم يحصل على رد فوري
    // ══════════════════════════════════════
    public CheckoutResult checkoutAsync(Long productId, String email) throws InterruptedException {
        long start = System.currentTimeMillis();

        log.info("🟢 [ASYNC] Starting asynchronous checkout...");

        // [1] الخطوة الضرورية فقط: التحقق من المخزون
        Thread.sleep(50);
        log.info("✅ [1/1] Stock validated (50ms) - Critical path done!");

        Long orderId = System.currentTimeMillis(); // order ID اصطناعي

        // [2,3,4] نرسل المهام البطيئة إلى الـ Queue - لا ننتظر!
        // الـ Producer يُضيف إلى Queue ويرجع فوراً
        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "INVOICE"));
        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "EMAIL"));
        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "WAREHOUSE"));

        long totalTime = System.currentTimeMillis() - start;
        log.info("🟢 [ASYNC] Response time: {}ms (background tasks queued)", totalTime);

        return new CheckoutResult(
                "Order confirmed ✅ (Async) - Processing in background!",
                totalTime,
                "ASYNCHRONOUS"
        );
    }

    public AsyncQueueService.QueueStats getQueueStats() {
        return queueService.getStats();
    }

    public record CheckoutResult(String message, long responseTimeMs, String mode) {}
}