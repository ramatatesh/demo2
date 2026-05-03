package com.example.demo2;

import com.example.demo2.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

@SpringBootTest
public class ConcurrentLoadTest {

    @Autowired
    private CheckoutService checkoutService;

    @Test
    public void testBeforeVsAfter() throws Exception {
        int users = 20; // عدد المستخدمين المتزامنين

        // ══ BEFORE: Synchronous ══
        System.out.println("\n🔴 ===== BEFORE (Synchronous) =====");
        long beforeStart = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch latch = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final int userId = i;
            pool.submit(() -> {
                try {
                    // كل "مستخدم" يعمل في thread منفصل
                    checkoutService.checkoutLegacy((long) userId, "user" + userId + "@test.com");
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    latch.countDown(); // إشارة "انتهيت"
                }
            });
        }

        latch.await(); // انتظار حتى ينتهي الجميع
        long beforeTotal = System.currentTimeMillis() - beforeStart;
        System.out.println("🔴 BEFORE Total time for " + users + " users: " + beforeTotal + "ms");
        pool.shutdown();

        // ══ AFTER: Asynchronous ══
        System.out.println("\n🟢 ===== AFTER (Asynchronous) =====");
        long afterStart = System.currentTimeMillis();

        ExecutorService pool2 = Executors.newFixedThreadPool(users);
        CountDownLatch latch2 = new CountDownLatch(users);

        for (int i = 0; i < users; i++) {
            final int userId = i;
            pool2.submit(() -> {
                try {
                    checkoutService.checkoutAsync((long) userId, "user" + userId + "@test.com");
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    latch2.countDown();
                }
            });
        }

        latch2.await();
        long afterTotal = System.currentTimeMillis() - afterStart;
        System.out.println("🟢 AFTER Total time for " + users + " users: " + afterTotal + "ms");
        pool2.shutdown();

        System.out.println("\n📊 IMPROVEMENT: " +
                Math.round((1.0 - (double)afterTotal/beforeTotal) * 100) + "% faster");
    }
}