package com.example.demo2;

import com.example.demo2.model.User;
import com.example.demo2.repository.SaleRepository;
import com.example.demo2.repository.UserRepository;
import com.example.demo2.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionStressTest {

    @Autowired private TransactionService transactionService;
    @Autowired private UserRepository     userRepository;
    @Autowired private SaleRepository     saleRepository;


    private static final int    THREADS       = 20;
    private static final double PRICE         = 50.0;
    private static final Long   TEST_USER_ID  = 999L;
    private static final Long   PRODUCT_ID    = 1L;


    @BeforeEach
    void setUp() {

        userRepository.findById(TEST_USER_ID).ifPresent(userRepository::delete);
        User testUser = new User("stress_test_user",
                                  BigDecimal.valueOf(THREADS * PRICE * 2));
        testUser.setId(TEST_USER_ID);


    }


    private Long setupTestUser() {

        List<User> users = userRepository.findAll();
        if (!users.isEmpty()) {
            User user = users.get(0);
            user.setWalletBalance(BigDecimal.valueOf(THREADS * PRICE * 2));
            userRepository.save(user);
            return user.getId();
        } else {
            User user = userRepository.save(
                new User("test_user", BigDecimal.valueOf(THREADS * PRICE * 2)));
            return user.getId();
        }
    }

    private void resetUserBalance(Long userId, double balance) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setWalletBalance(BigDecimal.valueOf(balance));
            userRepository.save(user);
        });
    }




    @Test
    @org.junit.jupiter.api.Order(1)
    void test_WITHOUT_Transaction_Causes_PartialSuccess() throws InterruptedException {
        Long userId = setupTestUser();
        double initialBalance = THREADS * PRICE * 2;
        resetUserBalance(userId, initialBalance);
        long initialOrderCount = saleRepository.count();

        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   اختبار BEFORE: بدون Transaction الموحدة         ║");
        System.out.println("║  " + THREADS + " threads متزامنة، نصفها سيحقن فشلاً في المنتصف  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        double balanceBefore = userRepository.findById(userId)
            .map(u -> u.getWalletBalance().doubleValue()).orElse(0.0);
        System.out.println("   قبل الاختبار: رصيد=" + balanceBefore + "$, طلبات=" + initialOrderCount);


        CountDownLatch startGun    = new CountDownLatch(1);
        CountDownLatch finishLine  = new CountDownLatch(THREADS);
        AtomicInteger  successCount  = new AtomicInteger(0);
        AtomicInteger  failureCount  = new AtomicInteger(0);
        List<String>   errors        = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final boolean shouldFail = (i % 2 == 0); // 50% تفشل في المنتصف
            final Long finalUserId = userId;

            pool.submit(() -> {
                try {
                    startGun.await();
                    transactionService.placeOrderWithoutTransaction(
                        finalUserId, PRODUCT_ID, 1, PRICE, shouldFail);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    errors.add(e.getMessage());
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startGun.countDown();
        finishLine.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        double  balanceAfter  = userRepository.findById(userId)
            .map(u -> u.getWalletBalance().doubleValue()).orElse(0.0);
        long    ordersAfter   = saleRepository.count() - initialOrderCount;
        double  deducted      = balanceBefore - balanceAfter;
        int     failedThreads = THREADS / 2; // نصف الـ threads حقنت فشلاً
        double  expectedDeductedWithPartial = failedThreads * PRICE; // المشكلة: هذا المبلغ خُصم رغم الفشل

        System.out.println("\n   ════ نتائج الاختبار ════");
        System.out.println("   Threads نجحت:         " + successCount.get());
        System.out.println("   Threads فشلت:         " + failureCount.get());
        System.out.println("   طلبات أُنشئت في DB:   " + ordersAfter);
        System.out.println("   رصيد بعد الاختبار:    " + balanceAfter + "$");
        System.out.println("   مجموع ما خُصم:        " + deducted + "$");


        double expectedDeduction = successCount.get() * PRICE;
        boolean partialSuccessOccurred = (deducted > expectedDeduction) ||
                                          (ordersAfter < successCount.get());

        System.out.println("\n   ════ تحليل المشكلة ════");
        if (partialSuccessOccurred) {
            System.out.println("    Partial Success اكتُشف:");
            System.out.println("      المبلغ المخصوم: " + deducted + "$ ← أكثر مما يقابله طلبات");
            System.out.println("      Atomicity مُنتهَكة ");
        } else {
            System.out.println("  ️  لم يظهر Partial Success في هذه الجلسة");
            System.out.println("   (المشكلة غير حتمية - تُظهر مع ضغط أعلى)");
        }
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
    }




    @Test
    @org.junit.jupiter.api.Order(2)
    void test_WITH_Transaction_Guarantees_Atomicity() throws InterruptedException {
        Long userId = setupTestUser();
        double initialBalance = THREADS * PRICE * 2;
        resetUserBalance(userId, initialBalance);
        long initialOrderCount = saleRepository.count();

        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   اختبار AFTER: مع @Transactional الموحدة         ║");
        System.out.println("║  " + THREADS + " threads متزامنة، نصفها سيحقن فشلاً → ROLLBACK  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        double balanceBefore = userRepository.findById(userId)
            .map(u -> u.getWalletBalance().doubleValue()).orElse(0.0);
        System.out.println("   قبل الاختبار: رصيد=" + balanceBefore + "$, طلبات=" + initialOrderCount);

        CountDownLatch startGun   = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(THREADS);
        AtomicInteger  successCount = new AtomicInteger(0);
        AtomicInteger  rollbackCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final boolean shouldFail = (i % 2 == 0);
            final Long finalUserId = userId;

            pool.submit(() -> {
                try {
                    startGun.await();
                    transactionService.placeOrderWithTransaction(
                        finalUserId, PRODUCT_ID, 1, PRICE, shouldFail);
                    successCount.incrementAndGet();
                } catch (Exception e) {

                    rollbackCount.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startGun.countDown();
        finishLine.await(30, TimeUnit.SECONDS);
        pool.shutdown();


        double balanceAfter = userRepository.findById(userId)
            .map(u -> u.getWalletBalance().doubleValue()).orElse(0.0);
        long ordersAfter = saleRepository.count() - initialOrderCount;
        double expectedDeduction = successCount.get() * PRICE;
        double actualDeduction   = balanceBefore - balanceAfter;

        System.out.println("\n   ════ نتائج الاختبار ════");
        System.out.println("   Threads نجحت (COMMIT):     " + successCount.get());
        System.out.println("   Threads رُدَّت (ROLLBACK): " + rollbackCount.get());
        System.out.println("   طلبات في DB:               " + ordersAfter);
        System.out.println("   رصيد بعد الاختبار:         " + balanceAfter + "$");
        System.out.println("   المبلغ المخصوم الفعلي:     " + actualDeduction + "$");
        System.out.println("   المبلغ المتوقع (ناجح×price): " + expectedDeduction + "$");




        assertEquals(successCount.get(), ordersAfter,
            " FAIL: عدد الطلبات (" + ordersAfter + ") لا يتطابق مع الناجحة ("
            + successCount.get() + ") → Partial Success حدث!");


        assertEquals(expectedDeduction, actualDeduction, 1.0, // 1$ هامش للتقريب
            " FAIL: المبلغ المخصوم (" + actualDeduction + "$) لا يتطابق مع المتوقع ("
            + expectedDeduction + "$) → ROLLBACK لم يعمل بشكل صحيح!");

        System.out.println("\n   ════ تحليل النتائج ════");
        System.out.println("   عدد الطلبات = عدد الناجحين بالضبط");
        System.out.println("  الرصيد المخصوم = ناجحة × " + PRICE + "$ بالضبط");
        System.out.println("    ROLLBACK يعمل: " + rollbackCount.get() + " عملية رُدَّت بالكامل");
        System.out.println("    Atomicity مُحقَّقة - لا Partial Success أبداً");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
    }
}
