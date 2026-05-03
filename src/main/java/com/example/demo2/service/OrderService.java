package com.example.demo2.service;


import com.example.demo2.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class OrderService {

    // inject الـ ThreadPoolExecutor الذي أنشأناه في ThreadPoolConfig
    private final ThreadPoolExecutor executor;
    private final ProductRepository repo;

    public OrderService(
            @Qualifier("orderExecutor") ThreadPoolExecutor executor,
            ProductRepository repo
    ) {
        this.executor = executor;
        this.repo = repo;
    }

    // المسار الأول: BEFORE - بدون Thread Pool (مشكلة)
    public String processOrderLegacy(Long productId, int qty) throws InterruptedException {
        // محاكاة عمل يأخذ وقتاً (مثل استعلام قاعدة بيانات بطيء)
        Thread.sleep(500);
        return "Legacy Order processed for product " + productId;
    }

    // المسار الثاني: AFTER - مع Thread Pool (الحل)
    public Future<String> processOrderWithPool(Long productId, int qty) {

        return executor.submit(() -> {

            long start = System.currentTimeMillis();  // ✅ بداية القياس

            Thread.sleep(500); // الشغل الحقيقي

            long duration = System.currentTimeMillis() - start; // ✅ النهاية

            String performance = duration < 100  ? "🟢 FAST"
                    : duration < 1000 ? "🟡 MEDIUM"
                    : "🔴 SLOW";

            System.out.println("╔══ WORKER MONITOR ═══════════════════════");
            System.out.println("║ Method  : processOrderWithPool (INSIDE)");
            System.out.println("║ Duration: " + duration + " ms  " + performance);
            System.out.println("║ Thread  : " + Thread.currentThread().getName());
            System.out.println("╚════════════════════════════════════════");

            return "Pool Order processed for product " + productId
                    + " by " + Thread.currentThread().getName();
        });
    }
    // تابع لجلب إحصائيات الـ Thread Pool (مهم للعرض)
    public ThreadPoolStats getPoolStats() {
        return new ThreadPoolStats(
                executor.getCorePoolSize(),      // عدد الـ core threads
                executor.getMaximumPoolSize(),   // الحد الأقصى
                executor.getActiveCount(),       // threads تعمل الآن
                executor.getCompletedTaskCount(), // tasks أُنجزت
                ((ArrayBlockingQueue<?>) executor.getQueue()).size() // طلبات في الانتظار
        );
    }

    // Record بسيط لتجميع الإحصائيات (Java 16+)
    public record ThreadPoolStats(
            int coreSize,
            int maxSize,
            int activeThreads,
            long completedTasks,
            int queuedTasks
    ) {}
}