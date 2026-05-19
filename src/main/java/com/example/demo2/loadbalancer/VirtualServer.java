// المسار: src/main/java/com/example/demo2/loadbalancer/VirtualServer.java

package com.example.demo2.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * VirtualServer: يمثّل خادماً افتراضياً (محاكاة Server حقيقي)
 * كل Object من هذا الكلاس = سيرفر مستقل بإحصائياته الخاصة
 */
public class VirtualServer {

    // اسم السيرفر مثل: "Server-1", "Server-2"
    private final String name;

    // عدد الطلبات التي استقبلها هذا السيرفر
    // AtomicInteger: آمن في بيئة Multi-Threading (لا يحدث Race Condition)
    private final AtomicInteger requestCount = new AtomicInteger(0);

    // إجمالي وقت المعالجة بالميلي ثانية لحساب المتوسط
    private final AtomicInteger totalProcessingTime = new AtomicInteger(0);

    // Constructor: يأخذ اسم السيرفر عند الإنشاء
    public VirtualServer(String name) {
        this.name = name;
    }

    /**
     * handleRequest: يعالج طلباً واحداً
     * @param processingTimeMs وقت المعالجة المصطنع بالميلي ثانية
     * @return نتيجة المعالجة
     */
    public String handleRequest(int processingTimeMs) {

        // نزيد عداد الطلبات بمقدار 1 بشكل آمن (Thread-Safe)
        int count = requestCount.incrementAndGet();

        // نضيف وقت المعالجة للإجمالي
        totalProcessingTime.addAndGet(processingTimeMs);

        // محاكاة وقت المعالجة الفعلي
        try {
            Thread.sleep(processingTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // نرجع رسالة توضح أي سيرفر عالج الطلب ورقم الطلب
        return String.format(
                "✅ [%s] عالج الطلب رقم #%d في %d ms",
                name, count, processingTimeMs
        );
    }

    // Getters للحصول على البيانات
    public String getName() { return name; }

    public int getRequestCount() { return requestCount.get(); }

    // حساب متوسط وقت المعالجة
    public double getAverageProcessingTime() {
        int count = requestCount.get();
        if (count == 0) return 0;
        return (double) totalProcessingTime.get() / count;
    }

    // إعادة ضبط الإحصائيات (للاختبار المتكرر)
    public void reset() {
        requestCount.set(0);
        totalProcessingTime.set(0);
    }
}
