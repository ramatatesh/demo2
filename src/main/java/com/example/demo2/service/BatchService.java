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

@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final SaleRepository saleRepo;

    private static final int CHUNK_SIZE = 3;

    private static final int THREAD_POOL_SIZE = 3;

    public BatchService(SaleRepository saleRepo) {
        this.saleRepo = saleRepo;
    }


    public BatchResult processRealTime() throws InterruptedException {

        long start = System.currentTimeMillis();
        log.warn(" [REALTIME] بدء المعالجة الفورية");
        List<Sale> sales = saleRepo.findByProcessedFalse();
        int count = 0;
        for (Sale sale : sales) {
            log.info(" Request Arrived -> Sale {}", sale.getId());
            processSingleSale(sale);
            log.info(" Request Finished -> Sale {}", sale.getId());
            count++;
        }

        long duration = System.currentTimeMillis() - start;
        log.warn(" [REALTIME] انتهت في {}ms", duration);
        return new BatchResult(
                "REALTIME",
                count,
                duration,
                1
        );
    }
    public BatchResult processBatchBatch() throws InterruptedException {
        long start = System.currentTimeMillis();
        log.warn(" [LEGACY] بدء المعالجة التسلسلية...");
        List<Sale> sales = saleRepo.findByProcessedFalse();
        log.info(" [COLLECT] {} مبيعة في الانتظار", sales.size());
        int count = 0;
        for (Sale sale : sales) {
            processSingleSale(sale);
            count++;
        }

        long dur = System.currentTimeMillis() - start;
        log.warn(" [LEGACY] انتهت في {}ms | Thread: {}",
                dur, Thread.currentThread().getName());

        return new BatchResult("SEQUENTIAL", count, dur, 1);
    }

    public BatchResult processBatchParallel() throws InterruptedException {
        long start = System.currentTimeMillis();
        log.info(" [PARALLEL] بدء المعالجة المتوازية...");

        List<Sale> sales = saleRepo.findByProcessedFalse();
        log.info(" [COLLECT] {} مبيعة في الانتظار", sales.size());

        if (sales.isEmpty()) {
            return new BatchResult("PARALLEL", 0, 0, THREAD_POOL_SIZE);
        }

        List<List<Sale>> chunks = createChunks(sales, CHUNK_SIZE);
        log.info(" [CHUNK] {} chunk بحجم {}", chunks.size(), CHUNK_SIZE);

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger processedCount = new AtomicInteger(0);
        int chunkNum = 0;

        for (List<Sale> chunk : chunks) {
            final int cn = ++chunkNum;

            pool.submit(() -> {
                log.info(" [CHUNK-{}] Thread: {} يعالج {} مبيعات",
                        cn, Thread.currentThread().getName(), chunk.size());

                for (Sale sale : chunk) {
                    try {
                        processSingleSale(sale);
                        processedCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.info("[CHUNK-{}] انتهت على Thread: {}",
                        cn, Thread.currentThread().getName());
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);

        long dur = System.currentTimeMillis() - start;
        log.info(" [PARALLEL] انتهت في {}ms | {} thread",
                dur, THREAD_POOL_SIZE);

        return new BatchResult("PARALLEL", processedCount.get(), dur, THREAD_POOL_SIZE);
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledDailyBatch() throws InterruptedException {
        log.info(" [SCHEDULED] الـ Daily Batch Job يبدأ تلقائياً...");
        processBatchParallel();
    }

    private void processSingleSale(Sale sale) throws InterruptedException {
        Thread.sleep(1000);
        sale.setProcessed(true);
        saleRepo.save(sale);
    }
    private <T> List<List<T>> createChunks(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return chunks;
    }

    public record BatchResult(
            String mode,
            int processedCount,
            long durationMs,
            int threadsUsed
    ) {}
}
