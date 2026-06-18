package com.example.demo2.service;

import com.example.demo2.model.Product;
import com.example.demo2.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;


@Service
public class StressTestService {

    private static final Logger log = LoggerFactory.getLogger(StressTestService.class);

    private final ProductService productService;
    private final ProductRepository productRepository;

    public StressTestService(ProductService productService,
                             ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
    }

    public Map<String, Object> runWithoutSynchronization(int userCount)
            throws InterruptedException {

        log.warn(" [BEFORE] {} مستخدم بدون CountDownLatch", userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong    totalTime    = new AtomicLong(0);
        AtomicLong    minTime      = new AtomicLong(Long.MAX_VALUE);
        AtomicLong    maxTime      = new AtomicLong(0);

        long testStart = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(userCount);

        for (int i = 0; i < userCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    productService.getAllProducts(false); // simulate real request
                    long dur = System.currentTimeMillis() - start;

                    successCount.incrementAndGet();
                    totalTime.addAndGet(dur);
                    minTime.updateAndGet(p -> Math.min(p, dur));
                    maxTime.updateAndGet(p -> Math.max(p, dur));

                    log.info("  User-{} انتهى في {}ms", userId, dur);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("  User-{} فشل: {}", userId, e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        long totalTestTime = System.currentTimeMillis() - testStart;
        int s = successCount.get();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode",            "WITHOUT CountDownLatch ");
        result.put("totalUsers",      userCount);
        result.put("successCount",    s);
        result.put("failureCount",    failureCount.get());
        result.put("errorRate",       String.format("%.1f%%", failureCount.get() * 100.0 / userCount));
        result.put("totalTestTimeMs", totalTestTime);
        result.put("avgResponseMs",   s > 0 ? totalTime.get() / s : 0);
        result.put("minResponseMs",   minTime.get() == Long.MAX_VALUE ? 0 : minTime.get());
        result.put("maxResponseMs",   maxTime.get());
        result.put("problem",         "الـ threads لم تبدأ في نفس اللحظة — ليس ضغطاً حقيقياً");
        return result;
    }

    public Map<String, Object> runWithCountDownLatch(int userCount)
            throws InterruptedException {

        log.info(" [AFTER] {} مستخدم مع CountDownLatch (Barrier Sync)", userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong    totalTime    = new AtomicLong(0);
        AtomicLong    minTime      = new AtomicLong(Long.MAX_VALUE);
        AtomicLong    maxTime      = new AtomicLong(0);


        CountDownLatch startGate = new CountDownLatch(1);


        CountDownLatch endGate = new CountDownLatch(userCount);

        ExecutorService executor = Executors.newFixedThreadPool(userCount);


        for (int i = 0; i < userCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {

                    startGate.await();

                    long start = System.currentTimeMillis();
                    productService.getAllProducts(false);
                    long dur = System.currentTimeMillis() - start;

                    successCount.incrementAndGet();
                    totalTime.addAndGet(dur);
                    minTime.updateAndGet(p -> Math.min(p, dur));
                    maxTime.updateAndGet(p -> Math.max(p, dur));

                    log.info(" User-{} انتهى في {}ms", userId, dur);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error(" User-{} فشل: {}", userId, e.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        log.info(" {} thread جاهزة عند البوابة — على وشك الانطلاق!", userCount);
        long testStart = System.currentTimeMillis();


        startGate.countDown();


        endGate.await(5, TimeUnit.MINUTES);
        long totalTestTime = System.currentTimeMillis() - testStart;

        executor.shutdown();
        int s = successCount.get();
        boolean stable = failureCount.get() == 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode",            "WITH CountDownLatch  (Barrier Sync )");
        result.put("totalUsers",      userCount);
        result.put("successCount",    s);
        result.put("failureCount",    failureCount.get());
        result.put("errorRate",       String.format("%.1f%%", failureCount.get() * 100.0 / userCount));
        result.put("totalTestTimeMs", totalTestTime);
        result.put("avgResponseMs",   s > 0 ? totalTime.get() / s : 0);
        result.put("minResponseMs",   minTime.get() == Long.MAX_VALUE ? 0 : minTime.get());
        result.put("maxResponseMs",   maxTime.get());
        result.put("systemStable",    stable
                ? " النظام مستقر — تخديم " + userCount + " مستخدم متزامن بدون انهيار"
                : " النظام غير مستقر");
        result.put("note",            "كل الـ threads انطلقت في نفس اللحظة — ضغط حقيقي!");
        return result;
    }


    public Map<String, Object> stressTestBuyLegacy(int userCount, Long productId)
            throws InterruptedException {

        log.warn(" [BUY-BEFORE] {} مستخدم يشترون بدون Pessimistic Lock", userCount);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail    = new AtomicInteger(0);


        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(userCount);
        ExecutorService executor =
                Executors.newFixedThreadPool(
                        Math.min(userCount, 50)
                );
        for (int i = 0; i < userCount; i++) {
            executor.submit(() -> {
                try {

                    startGate.await();

                    boolean res =
                            productService.buyProduct(productId, 1, false);

                    if (res)
                        success.incrementAndGet();
                    else
                        fail.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(2, TimeUnit.MINUTES);
        executor.shutdown();

        Optional<Product> product = productRepository.findById(productId);
        int finalStock = product.map(Product::getStockQuantity).orElse(-999);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode",        "BUY Legacy — NO Lock ");
        result.put("totalUsers",  userCount);
        result.put("success",     success.get());
        result.put("fail",        fail.get());
        result.put("finalStock",  finalStock);
        result.put("invariant",   finalStock < 0
                ? " INVARIANT VIOLATED! Stock سالب = Race Condition!"
                : "Stock طبيعي");
        return result;
    }


    public Map<String, Object> stressTestBuyOptimized(int userCount, Long productId)
            throws InterruptedException {

        log.info(" [BUY-AFTER] {} مستخدم يشترون مع Pessimistic Lock", userCount);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail    = new AtomicInteger(0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate   = new CountDownLatch(userCount);
        ExecutorService executor = Executors.newFixedThreadPool(userCount);

        for (int i = 0; i < userCount; i++) {
            executor.submit(() -> {
                try {

                    startGate.await();

                    boolean res =
                            productService.buyProduct(productId, 1, true);

                    if (res)
                        success.incrementAndGet();
                    else
                        fail.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(2, TimeUnit.MINUTES);
        executor.shutdown();

        Optional<Product> product = productRepository.findById(productId);
        int finalStock = product.map(Product::getStockQuantity).orElse(-999);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode",       "BUY Optimized — Pessimistic Lock ");
        result.put("totalUsers", userCount);
        result.put("success",    success.get());
        result.put("fail",       fail.get());
        result.put("finalStock", finalStock);
        result.put("invariant",  finalStock >= 0
                ? " INVARIANT HOLDS — Stock محمي، لا Race Condition"
                : " خطأ غير متوقع");
        return result;
    }
}