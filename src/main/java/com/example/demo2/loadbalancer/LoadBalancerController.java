// المسار: src/main/java/com/example/demo2/loadbalancer/LoadBalancerController.java

package com.example.demo2.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LoadBalancerController: يكشف الـ APIs للاختبار
 * يحتوي على مسارين:
 * - /lb/without : بدون Load Balancing (المشكلة)
 * - /lb/with    : مع Load Balancing (الحل)
 */
@RestController
// @RestController: يجعل كل الـ Methods ترجع JSON تلقائياً
@RequestMapping("/lb")
// @RequestMapping("/lb"): كل الـ endpoints تبدأ بـ /lb
public class LoadBalancerController {

    private static final Logger logger =
            LoggerFactory.getLogger(LoadBalancerController.class);

    // Injection: Spring يحقن الـ Service تلقائياً
    private final LoadBalancerService loadBalancerService;

    // Constructor Injection: أفضل طريقة لـ Dependency Injection
    public LoadBalancerController(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }

    // ========================================================
    // Endpoint 1: المسار القديم بدون Load Balancing (Before)
    // ========================================================

    /**
     * POST /lb/without
     * يرسل الطلب لسيرفر واحد فقط دائماً
     * @param delay وقت التأخير بالميلي ثانية (اختياري، افتراضي 200ms)
     */
    @PostMapping("/without")
    // @PostMapping: يقبل طلبات HTTP POST
    public ResponseEntity<Map<String, Object>> withoutLoadBalancing(
            @RequestParam(defaultValue = "200") int delay) {
        // @RequestParam: يقرأ قيمة من URL مثل: /lb/without?delay=300

        logger.info("📨 طلب وصل إلى /lb/without بتأخير {}ms", delay);

        long startTime = System.currentTimeMillis();

        // نرسل الطلب بدون Load Balancing
        String result = loadBalancerService.handleWithoutLoadBalancing(delay);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // بناء الـ Response
        Map<String, Object> response = new HashMap<>();
        response.put("mode", "WITHOUT Load Balancing ❌");
        response.put("result", result);
        response.put("responseTimeMs", responseTime);
        response.put("note", "كل الطلبات تذهب لـ Server-1 فقط!");

        return ResponseEntity.ok(response);
    }

    // ========================================================
    // Endpoint 2: المسار الجديد مع Load Balancing (After)
    // ========================================================

    /**
     * POST /lb/with
     * يوزّع الطلبات على 3 سيرفرات باستخدام Round Robin
     * @param delay وقت التأخير بالميلي ثانية (اختياري، افتراضي 200ms)
     */
    @PostMapping("/with")
    public ResponseEntity<Map<String, Object>> withLoadBalancing(
            @RequestParam(defaultValue = "200") int delay) {

        logger.info("📨 طلب وصل إلى /lb/with بتأخير {}ms", delay);

        long startTime = System.currentTimeMillis();

        // نرسل الطلب مع Load Balancing
        String result = loadBalancerService.handleWithLoadBalancing(delay);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // بناء الـ Response
        Map<String, Object> response = new HashMap<>();
        response.put("mode", "WITH Load Balancing ✅");
        response.put("result", result);
        response.put("responseTimeMs", responseTime);
        response.put("note", "الطلبات موزّعة بالتساوي على 3 Servers");

        return ResponseEntity.ok(response);
    }

    // ========================================================
    // Endpoint 3: عرض إحصائيات كل السيرفرات
    // ========================================================

    /**
     * GET /lb/stats
     * يرجع إحصائيات كل سيرفر لإثبات التوزيع
     */
    @GetMapping("/stats")
    // @GetMapping: يقبل طلبات HTTP GET
    public ResponseEntity<Map<String, Object>> getStats() {

        List<LoadBalancerService.ServerStats> stats =
                loadBalancerService.getStats();

        Map<String, Object> response = new HashMap<>();
        response.put("totalRequests", loadBalancerService.getTotalRequests());
        response.put("servers", stats);
        response.put("explanation",
                "قارن requestsHandled بين السيرفرات لترى التوزيع");

        return ResponseEntity.ok(response);
    }

    // ========================================================
    // Endpoint 4: إعادة ضبط الإحصائيات
    // ========================================================

    /**
     * POST /lb/reset
     * يعيد ضبط كل الإحصائيات للاختبار من جديد
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        loadBalancerService.resetAll();
        return ResponseEntity.ok("✅ تم إعادة ضبط كل السيرفرات");
    }

    // ========================================================
    // Endpoint 5: مقارنة Before vs After في طلب واحد
    // ========================================================

    /**
     * GET /lb/compare
     * يظهر الفرق بين الوضعين بشكل واضح
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare() {

        Map<String, Object> response = new HashMap<>();

        // إحصائيات بعد إرسال طلبات
        List<LoadBalancerService.ServerStats> stats =
                loadBalancerService.getStats();

        // حساب: هل السيرفر الأول محمّل أكثر من الباقين؟
        if (stats.size() >= 3) {
            int server1Requests = stats.get(0).requestsHandled;
            int server2Requests = stats.get(1).requestsHandled;
            int server3Requests = stats.get(2).requestsHandled;

            response.put("Server-1 requests", server1Requests);
            response.put("Server-2 requests", server2Requests);
            response.put("Server-3 requests", server3Requests);

            // هل التوزيع متوازن؟
            boolean isBalanced =
                    Math.abs(server1Requests - server2Requests) <= 2 &&
                            Math.abs(server2Requests - server3Requests) <= 2;

            response.put("isBalanced", isBalanced);
            response.put("conclusion",
                    isBalanced
                            ? "✅ Load Balancing يعمل: التوزيع متوازن"
                            : "❌ Load Balancing لا يعمل أو لم يُختبر بعد");
        }

        return ResponseEntity.ok(response);
    }
}
