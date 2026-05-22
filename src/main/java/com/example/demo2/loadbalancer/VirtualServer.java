package com.example.demo2.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

public class VirtualServer {

    private final String name;

    private final AtomicInteger requestCount = new AtomicInteger(0);

    private final AtomicInteger totalProcessingTime = new AtomicInteger(0);

    public VirtualServer(String name) {
        this.name = name;
    }

    public String handleRequest(int processingTimeMs) {

        int count = requestCount.incrementAndGet();

        totalProcessingTime.addAndGet(processingTimeMs);

        try {
            Thread.sleep(processingTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return String.format(
                " [%s] عالج الطلب رقم #%d في %d ms",
                name, count, processingTimeMs
        );
    }

    public String getName() { return name; }

    public int getRequestCount() { return requestCount.get(); }

    public double getAverageProcessingTime() {
        int count = requestCount.get();
        if (count == 0) return 0;
        return (double) totalProcessingTime.get() / count;
    }

    public void reset() {
        requestCount.set(0);
        totalProcessingTime.set(0);
    }
}
