package com.example.demo2.service;


import com.example.demo2.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class OrderService {

    private final ThreadPoolExecutor executor;
    private final ProductRepository repo;

    public OrderService(
            @Qualifier("orderExecutor") ThreadPoolExecutor executor,
            ProductRepository repo
    ) {
        this.executor = executor;
        this.repo = repo;
    }

    public String processOrderLegacy(Long productId, int qty) throws InterruptedException {
        Thread.sleep(500);
        return "Legacy Order processed for product " + productId;
    }

    public Future<String> processOrderWithPool(Long productId, int qty) {

        return executor.submit(() -> {

            long start = System.currentTimeMillis();

            Thread.sleep(500);

            long duration = System.currentTimeMillis() - start;

            String performance = duration < 100  ? " FAST"
                    : duration < 1000 ? " MEDIUM"
                    : " SLOW";

            System.out.println("╔══ WORKER MONITOR ═══════════════════════");
            System.out.println("║ Method  : processOrderWithPool (INSIDE)");
            System.out.println("║ Duration: " + duration + " ms  " + performance);
            System.out.println("║ Thread  : " + Thread.currentThread().getName());
            System.out.println("╚════════════════════════════════════════");

            return "Pool Order processed for product " + productId
                    + " by " + Thread.currentThread().getName();
        });
    }
    public ThreadPoolStats getPoolStats() {
        return new ThreadPoolStats(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getActiveCount(),
                executor.getCompletedTaskCount(),
                ((ArrayBlockingQueue<?>) executor.getQueue()).size()
        );
    }

    public record ThreadPoolStats(
            int coreSize,
            int maxSize,
            int activeThreads,
            long completedTasks,
            int queuedTasks
    ) {}
}