package com.example.demo2.service;


import com.example.demo2.model.NotificationTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AsyncQueueService {

    private static final Logger log = LoggerFactory.getLogger(AsyncQueueService.class);

    // هذه الـ Queue هي "صندوق البريد" بين الـ Producer والـ Consumer
    // BlockingQueue = تُجمّد الـ thread إذا كانت فارغة (انتظار رسالة)
    // سعة 500 رسالة كحد أقصى
    private final BlockingQueue<NotificationTask> taskQueue =
            new ArrayBlockingQueue<>(500);

    // Worker thread يعالج الرسائل في الخلفية
    private Thread workerThread;

    // volatile = كل thread ترى القيمة الحقيقية (لا cache)
    private volatile boolean running = true;

    // إحصائيات
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    // @PostConstruct = يُنفَّذ بعد إنشاء الـ Bean مباشرة (عند بدء التطبيق)
    @PostConstruct
    public void startWorker() {
        // ننشئ thread واحد يعمل في الخلفية بشكل دائم
        workerThread = new Thread(this::processQueue, "notification-worker");
        workerThread.setDaemon(true); // daemon = ينتهي عندما ينتهي التطبيق
        workerThread.start();
        log.info("✅ Async Queue Worker started");
    }

    // هذا الـ method هو "Producer" - يُضيف مهام إلى القائمة
    public boolean enqueue(NotificationTask task) {
        // offer() يُضيف إلى الـ Queue، يُعيد false إذا كانت ممتلئة
        boolean added = taskQueue.offer(task);
        if (added) {
            log.info("📨 Task enqueued: {} for order #{} | Queue size: {}",
                    task.taskType(), task.orderId(), taskQueue.size());
        } else {
            // الـ Queue ممتلئة → نسجل الخطأ (Dead Letter situation)
            log.warn("⚠️ Queue FULL! Task dropped: {} for order #{}",
                    task.taskType(), task.orderId());
            failedCount.incrementAndGet();
        }
        return added;
    }

    // هذا هو الـ Consumer - يعمل في loop بشكل دائم
    private void processQueue() {
        while (running) {
            try {
                // take() تنتظر (blocking) حتى توجد رسالة في الـ Queue
                NotificationTask task = taskQueue.take();
                processTask(task);
                processedCount.incrementAndGet();

            } catch (InterruptedException e) {
                // إذا تم interrupt الـ thread (عند إيقاف التطبيق)
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // معالجة كل مهمة حسب نوعها
    private void processTask(NotificationTask task) {
        try {
            log.info("⚙️ Processing: {} for order #{}", task.taskType(), task.orderId());

            switch (task.taskType()) {
                case "EMAIL" -> {
                    Thread.sleep(1500); // محاكاة إرسال إيميل (بطيء)
                    log.info("📧 Email sent to {} for order #{}", task.customerEmail(), task.orderId());
                }
                case "INVOICE" -> {
                    Thread.sleep(2000); // محاكاة إنشاء PDF (أبطأ)
                    log.info("🧾 Invoice generated for order #{}", task.orderId());
                }
                case "WAREHOUSE" -> {
                    Thread.sleep(800);  // محاكاة إشعار المستودع
                    log.info("🏭 Warehouse notified for order #{}", task.orderId());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // إحصائيات الـ Queue (للعرض أمام المعيدة)
    public QueueStats getStats() {
        return new QueueStats(
                taskQueue.size(),         // مهام في الانتظار
                processedCount.get(),     // مهام أُنجزت
                failedCount.get(),        // مهام فشلت (Queue امتلأت)
                taskQueue.remainingCapacity() // مساحة متبقية
        );
    }

    // @PreDestroy = يُنفَّذ قبل إيقاف التطبيق (graceful shutdown)
    @PreDestroy
    public void stopWorker() {
        running = false;
        workerThread.interrupt();
        log.info("🛑 Async Queue Worker stopped. Processed: {}", processedCount.get());
    }

    public record QueueStats(
            int pendingTasks,
            long processedTasks,
            long failedTasks,
            int remainingCapacity
    ) {}
}