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


    private final BlockingQueue<NotificationTask> taskQueue =
            new ArrayBlockingQueue<>(500);

    private Thread workerThread;

    private volatile boolean running = true;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    @PostConstruct
    public void startWorker() {
        workerThread = new Thread(this::processQueue, "notification-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info(" Async Queue Worker started");
    }

    public boolean enqueue(NotificationTask task) {
        boolean added = taskQueue.offer(task);
        if (added) {
            log.info(" Task enqueued: {} for order #{} | Queue size: {}",
                    task.taskType(), task.orderId(), taskQueue.size());
        } else {
            log.warn(" Queue FULL! Task dropped: {} for order #{}",
                    task.taskType(), task.orderId());
            failedCount.incrementAndGet();
        }
        return added;
    }

    private void processQueue() {
        while (running) {
            try {
                NotificationTask task = taskQueue.take();
                processTask(task);
                processedCount.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void processTask(NotificationTask task) {
        try {
            log.info(" Processing: {} for order #{}", task.taskType(), task.orderId());

            switch (task.taskType()) {
                case "EMAIL" -> {
                    Thread.sleep(1500);
                    log.info(" Email sent to {} for order #{}", task.customerEmail(), task.orderId());
                }
                case "INVOICE" -> {
                    Thread.sleep(2000);
                    log.info(" Invoice generated for order #{}", task.orderId());
                }
                case "WAREHOUSE" -> {
                    Thread.sleep(800);
                    log.info(" Warehouse notified for order #{}", task.orderId());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public QueueStats getStats() {
        return new QueueStats(
                taskQueue.size(),
                processedCount.get(),
                failedCount.get(),
                taskQueue.remainingCapacity()
        );
    }

    @PreDestroy
    public void stopWorker() {
        running = false;
        workerThread.interrupt();
        log.info(" Async Queue Worker stopped. Processed: {}", processedCount.get());
    }

    public record QueueStats(
            int pendingTasks,
            long processedTasks,
            long failedTasks,
            int remainingCapacity
    ) {}
}