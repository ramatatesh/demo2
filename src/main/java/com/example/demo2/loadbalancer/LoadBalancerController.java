package com.example.demo2.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/lb")
public class LoadBalancerController {

    private static final Logger logger =
            LoggerFactory.getLogger(LoadBalancerController.class);

    private final LoadBalancerService loadBalancerService;

    public LoadBalancerController(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }


    @PostMapping("/without")
    public ResponseEntity<Map<String, Object>> withoutLoadBalancing(
            @RequestParam(defaultValue = "200") int delay) {

        logger.info(" طلب وصل إلى /lb/without بتأخير {}ms", delay);

        long startTime = System.currentTimeMillis();

        String result = loadBalancerService.handleWithoutLoadBalancing(delay);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("mode", "WITHOUT Load Balancing ");
        response.put("result", result);
        response.put("responseTimeMs", responseTime);
        response.put("note", "كل الطلبات تذهب لـ Server-1 فقط!");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/with")
    public ResponseEntity<Map<String, Object>> withLoadBalancing(
            @RequestParam(defaultValue = "200") int delay) {

        logger.info(" طلب وصل إلى /lb/with بتأخير {}ms", delay);

        long startTime = System.currentTimeMillis();

        String result = loadBalancerService.handleWithLoadBalancing(delay);

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        Map<String, Object> response = new HashMap<>();
        response.put("mode", "WITH Load Balancing ");
        response.put("result", result);
        response.put("responseTimeMs", responseTime);
        response.put("note", "الطلبات موزّعة بالتساوي على 3 Servers");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
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

    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        loadBalancerService.resetAll();
        return ResponseEntity.ok(" تم إعادة ضبط كل السيرفرات");
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare() {

        Map<String, Object> response = new HashMap<>();

        List<LoadBalancerService.ServerStats> stats =
                loadBalancerService.getStats();

        if (stats.size() >= 3) {
            int server1Requests = stats.get(0).requestsHandled;
            int server2Requests = stats.get(1).requestsHandled;
            int server3Requests = stats.get(2).requestsHandled;

            response.put("Server-1 requests", server1Requests);
            response.put("Server-2 requests", server2Requests);
            response.put("Server-3 requests", server3Requests);

            boolean isBalanced =
                    Math.abs(server1Requests - server2Requests) <= 2 &&
                            Math.abs(server2Requests - server3Requests) <= 2;

            response.put("isBalanced", isBalanced);
            response.put("conclusion",
                    isBalanced
                            ? " Load Balancing يعمل: التوزيع متوازن"
                            : " Load Balancing لا يعمل أو لم يُختبر بعد");
        }

        return ResponseEntity.ok(response);
    }
}
