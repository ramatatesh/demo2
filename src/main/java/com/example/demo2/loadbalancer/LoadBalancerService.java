// المسار: src/main/java/com/example/demo2/loadbalancer/LoadBalancerService.java

package com.example.demo2.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LoadBalancerService: يحتوي على منطق Load Balancing
 * يوزّع الطلبات على عدة Servers باستخدام Round Robin
 */
@Service
// @Service: يخبر Spring أن هذا الكلاس هو Service ويتم إنشاؤه تلقائياً
public class LoadBalancerService {

    // Logger: لطباعة معلومات تفصيلية في الـ Console وملف اللوق
    private static final Logger logger =
            LoggerFactory.getLogger(LoadBalancerService.class);

    // ========== إعداد السيرفرات الافتراضية ==========

    // قائمة السيرفرات الافتراضية التي سنوزّع عليها
    // List<VirtualServer>: قائمة من VirtualServer Objects
    private final List<VirtualServer> servers = new ArrayList<>();

    // عداد Round Robin: يحدد أي سيرفر يستقبل الطلب التالي
    // AtomicInteger: آمن في Multi-Threading
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    // عداد إجمالي الطلبات لإظهار في الإحصائيات
    private final AtomicInteger totalRequests = new AtomicInteger(0);

    // Constructor: يُشغَّل عند إنشاء الكلاس لأول مرة
    // هنا ننشئ 3 سيرفرات افتراضية
    public LoadBalancerService() {
        servers.add(new VirtualServer("Server-1"));
        servers.add(new VirtualServer("Server-2"));
        servers.add(new VirtualServer("Server-3"));

        logger.info("🚀 Load Balancer initialized with {} virtual servers",
                servers.size());
    }

    // ========== المسار القديم: بدون Load Balancing ==========

    /**
     * handleWithoutLoadBalancing: يرسل كل الطلبات لسيرفر واحد فقط
     * هذا يمثّل المشكلة (Before)
     * @param processingTimeMs وقت المعالجة المصطنع
     */
    public String handleWithoutLoadBalancing(int processingTimeMs) {

        // نزيد العداد
        totalRequests.incrementAndGet();

        // نختار السيرفر الأول دائماً (المشكلة!)
        VirtualServer singleServer = servers.get(0);

        logger.info("❌ [NO Load Balancing] كل الطلبات تذهب إلى: {}",
                singleServer.getName());

        // نمرر الطلب للسيرفر الأول فقط
        String result = singleServer.handleRequest(processingTimeMs);

        logger.info("❌ [NO Load Balancing] نتيجة: {}", result);

        return result;
    }

    // ========== المسار الجديد: مع Load Balancing ==========

    /**
     * handleWithLoadBalancing: يوزّع الطلبات على كل السيرفرات
     * هذا يمثّل الحل (After)
     * @param processingTimeMs وقت المعالجة المصطنع
     */
    public String handleWithLoadBalancing(int processingTimeMs) {

        // نزيد العداد
        totalRequests.incrementAndGet();

        // ========== Round Robin Algorithm ==========
        // getAndIncrement(): يأخذ القيمة الحالية ثم يزيدها
        // % servers.size(): يرجع للأول بعد آخر سيرفر
        // مثال: 0%3=0, 1%3=1, 2%3=2, 3%3=0, 4%3=1 ...
        int serverIndex = roundRobinCounter.getAndIncrement() % servers.size();

        // نختار السيرفر المحدد بناءً على الـ Index
        VirtualServer selectedServer = servers.get(serverIndex);

        logger.info("✅ [Load Balancer] وجّه الطلب إلى: {} (Index: {})",
                selectedServer.getName(), serverIndex);

        // نمرر الطلب للسيرفر المختار
        String result = selectedServer.handleRequest(processingTimeMs);

        logger.info("✅ [Load Balancer] نتيجة: {}", result);

        return result;
    }

    // ========== إحصائيات السيرفرات ==========

    /**
     * getStats: يرجع إحصائيات كل سيرفر
     * نستخدمها لإثبات أن التوزيع حدث فعلاً
     */
    public List<ServerStats> getStats() {

        List<ServerStats> stats = new ArrayList<>();

        for (VirtualServer server : servers) {
            stats.add(new ServerStats(
                    server.getName(),
                    server.getRequestCount(),
                    server.getAverageProcessingTime()
            ));
        }

        return stats;
    }

    /**
     * resetAll: إعادة ضبط كل السيرفرات للاختبار من جديد
     */
    public void resetAll() {
        servers.forEach(VirtualServer::reset);
        roundRobinCounter.set(0);
        totalRequests.set(0);
        logger.info("🔄 تم إعادة ضبط كل السيرفرات");
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    // ========== Inner Class للإحصائيات ==========

    /**
     * ServerStats: كلاس بسيط لحمل إحصائيات سيرفر واحد
     * Inner Class: موجود داخل LoadBalancerService لأنه مرتبط به
     */
    public static class ServerStats {
        public String serverName;
        public int requestsHandled;
        public double avgProcessingTimeMs;

        public ServerStats(String name, int requests, double avgTime) {
            this.serverName = name;
            this.requestsHandled = requests;
            this.avgProcessingTimeMs = avgTime;
        }
    }
}
