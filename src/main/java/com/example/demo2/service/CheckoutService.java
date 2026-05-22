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


    public CheckoutResult checkoutLegacy(Long productId, String email) throws InterruptedException {
        long start = System.currentTimeMillis();

        log.info(" [LEGACY] Starting synchronous checkout...");

        Thread.sleep(50);
        log.info(" [1/4] Stock validated (50ms)");

        Thread.sleep(2000);
        log.info(" [2/4] Invoice generated (2000ms)");

        Thread.sleep(1500);
        log.info(" [3/4] Email sent (1500ms)");

        Thread.sleep(800);
        log.info(" [4/4] Warehouse notified (800ms)");

        long totalTime = System.currentTimeMillis() - start;
        log.info(" [LEGACY] Total time: {}ms", totalTime);

        return new CheckoutResult(
                "Order confirmed  (Legacy)",
                totalTime,
                "SYNCHRONOUS"
        );
    }

    // AFTER
    public CheckoutResult checkoutAsync(Long productId, String email) throws InterruptedException {
        long start = System.currentTimeMillis();

        log.info(" [ASYNC] Starting asynchronous checkout...");

        Thread.sleep(50);
        log.info("[1/1] Stock validated (50ms) - Critical path done!");

        Long orderId = System.currentTimeMillis();


        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "INVOICE"));
        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "EMAIL"));
        queueService.enqueue(new NotificationTask(orderId, email, "Product-" + productId, "WAREHOUSE"));

        long totalTime = System.currentTimeMillis() - start;
        log.info(" [ASYNC] Response time: {}ms (background tasks queued)", totalTime);

        return new CheckoutResult(
                "Order confirmed (Async) - Processing in background!",
                totalTime,
                "ASYNCHRONOUS"
        );
    }

    public AsyncQueueService.QueueStats getQueueStats() {
        return queueService.getStats();
    }

    public record CheckoutResult(String message, long responseTimeMs, String mode) {}
}