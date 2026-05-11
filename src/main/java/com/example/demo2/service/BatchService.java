package com.example.demo2.service;

import com.example.demo2.model.Sale;
import com.example.demo2.repository.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// @Service = Spring يعرف أن هذا الكلاس يحتوي Business Logic
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final SaleRepository saleRepo;

    // من S4-Slide8: Fixed-Size Chunking
    // نقسم البيانات إلى أجزاء ثابتة الحجم
    private static final int CHUNK_SIZE = 3;

    // من S2-Slide8: Fixed Thread Pool
    // عدد threads ثابت = عدد الـ CPU cores (أو أكثر للـ I/O-bound tasks)
    private static final int THREAD_POOL_SIZE = 3;

    public BatchService(SaleRepository saleRepo) {
        this.saleRepo = saleRepo;
    }


    // ═══════════════════════════════════════════
// REALTIME PROCESSING
// كل request يُعالج مباشرة فور وصوله
// ═══════════════════════════════════════════
    public BatchResult processRealTime() throws InterruptedException {

        long start = System.currentTimeMillis();

        log.warn("🔵 [REALTIME] بدء المعالجة الفورية...");

        List<Sale> sales = saleRepo.findByProcessedFalse();

        int count = 0;

        for (Sale sale : sales) {

            log.info("📥 Request Arrived -> Sale {}", sale.getId());

            processSingleSale(sale);

            log.info("✅ Request Finished -> Sale {}", sale.getId());

            count++;
        }

        long duration = System.currentTimeMillis() - start;

        log.warn("🔵 [REALTIME] انتهت في {}ms", duration);

        return new BatchResult(
                "REALTIME",
                count,
                duration,
                1
        );
    }


    // ═══════════════════════════════════════════
    // ❌ BEFORE: Sequential Processing (المشكلة)
    // ?useFix=false
    // ═══════════════════════════════════════════
    public BatchResult processBatchBatch() throws InterruptedException {
        long start = System.currentTimeMillis();
        log.warn("🔴 [LEGACY] بدء المعالجة التسلسلية...");

        // COLLECT PHASE (من batchProcessing.java بطريقة الدكتور)
        // نجلب المبيعات غير المعالجة من قاعدة البيانات
        List<Sale> sales = saleRepo.findByProcessedFalse();
        log.info("📦 [COLLECT] {} مبيعة في الانتظار", sales.size());

        // SEQUENTIAL PROCESS PHASE (المشكلة!)
        // نعالج واحدة تلو الأخرى - بطيء!
        int count = 0;
        for (Sale sale : sales) {
            processSingleSale(sale); // كل واحدة 300ms
            count++;
        }

        long dur = System.currentTimeMillis() - start;
        log.warn("🔴 [LEGACY] انتهت في {}ms | Thread: {}",
                dur, Thread.currentThread().getName());

        return new BatchResult("SEQUENTIAL", count, dur, 1);
    }

    // ═══════════════════════════════════════════
    // ✅ AFTER: Parallel Batch Processing (الحل)
    // ?useFix=true
    // مبني على: S4-Slide4 (Partitioning+Distribution+Execution)
    //            S4-Slide8 (Fixed-Size Chunking)
    //            S2-Slide4 (Thread Pool)
    // ═══════════════════════════════════════════
    public BatchResult processBatchParallel() throws InterruptedException {
        long start = System.currentTimeMillis();
        log.info("🟢 [PARALLEL] بدء المعالجة المتوازية...");

        // ─── PHASE 1: COLLECT ───
        // نجلب فقط المبيعات غير المعالجة (Idempotency من S4-Slide10)
        List<Sale> sales = saleRepo.findByProcessedFalse();
        log.info("📦 [COLLECT] {} مبيعة في الانتظار", sales.size());

        if (sales.isEmpty()) {
            return new BatchResult("PARALLEL", 0, 0, THREAD_POOL_SIZE);
        }

        // ─── PHASE 2: CHUNKING ───
        // S4-Slide4: "The large dataset is split into smaller, independent chunks"
        // S4-Slide8: Fixed-Size Chunking
        List<List<Sale>> chunks = createChunks(sales, CHUNK_SIZE);
        log.info("✂️ [CHUNK] {} chunk بحجم {}", chunks.size(), CHUNK_SIZE);

        // ─── PHASE 3: PARALLEL EXECUTION ───
        // S4-Slide4: "Workers process their assigned chunks in parallel"
        // S2-Slide8: Fixed Thread Pool = "Predictable, steady workloads"
        // AtomicInteger = thread-safe counter (من Session 1)
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger processedCount = new AtomicInteger(0);
        int chunkNum = 0;

        for (List<Sale> chunk : chunks) {
            final int cn = ++chunkNum;

            // pool.submit() = S4-Slide4: "Chunks are distributed to worker threads"
            // كل chunk يذهب لـ thread متاح في الـ pool
            pool.submit(() -> {
                log.info("⚙️ [CHUNK-{}] Thread: {} يعالج {} مبيعات",
                        cn, Thread.currentThread().getName(), chunk.size());

                for (Sale sale : chunk) {
                    try {
                        processSingleSale(sale);
                        processedCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.info("✅ [CHUNK-{}] انتهت على Thread: {}",
                        cn, Thread.currentThread().getName());
            });
        }

        // S2-Slide11: "Shutdown Gracefully - allow existing tasks to finish"
        // pool.shutdown() = لا تقبل tasks جديدة
        // awaitTermination() = انتظر حتى تنتهي كل الـ tasks
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);

        long dur = System.currentTimeMillis() - start;
        log.info("🟢 [PARALLEL] انتهت في {}ms | {} thread",
                dur, THREAD_POOL_SIZE);

        return new BatchResult("PARALLEL", processedCount.get(), dur, THREAD_POOL_SIZE);
    }

    // ─── @Scheduled: Background Job (من S4-Slide2 "Scheduled") ───
    // cron = "ثانية دقيقة ساعة يوم شهر يوم_أسبوع"
    // "0 0 23 * * *" = كل يوم الساعة 11 مساءً (ساعة هادئة)
    // هذا هو مفهوم "Scheduled" من الـ Slide مباشرة!
    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledDailyBatch() throws InterruptedException {
        log.info("⏰ [SCHEDULED] الـ Daily Batch Job يبدأ تلقائياً...");
        processBatchParallel();
    }

    // ─── HELPER: معالجة مبيعة واحدة ───
    // يحاكي: حساب الإيراد + إنشاء فاتورة + حفظ في DB
    private void processSingleSale(Sale sale) throws InterruptedException {
        Thread.sleep(1000); // محاكاة عمل حقيقي (DB + calculation)
        sale.setProcessed(true); // علّم على أنها عُولجت (Idempotency!)
        saleRepo.save(sale);     // احفظ في قاعدة البيانات
    }

    // ─── HELPER: تقسيم القائمة إلى chunks ───
    // S4-Slide8: Fixed-Size Chunking
    // مثل: [0,1,2,3,4,5,6] بحجم 3 → [[0,1,2],[3,4,5],[6]]
    private <T> List<List<T>> createChunks(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            // subList: نأخذ slice من القائمة
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return chunks;
    }

    // ─── Record: يجمع نتائج الـ Batch ───
    // record = كلاس بسيط للبيانات (Java 16+)
    public record BatchResult(
            String mode,           // "SEQUENTIAL" أو "PARALLEL"
            int processedCount,    // عدد المبيعات المعالجة
            long durationMs,       // الوقت الكلي بالـ milliseconds
            int threadsUsed        // عدد الـ threads المستخدمة
    ) {}
}
