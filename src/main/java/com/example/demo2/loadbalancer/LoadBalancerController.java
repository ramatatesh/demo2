package com.example.demo2.loadbalancer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lb")
public class LoadBalancerController {

    private final LoadBalancerService loadBalancerService;

    public LoadBalancerController(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }

    // قبل الحل
    @PostMapping("/without")
    public ResponseEntity<Map<String, Object>> withoutLoadBalancing() {
        long start  = System.currentTimeMillis();
        String result = loadBalancerService.handleWithoutLoadBalancing();
        long time   = System.currentTimeMillis() - start;

        Map<String, Object> response = new HashMap<>();
        response.put("mode",          "WITHOUT Load Balancing");
        response.put("result",        result);
        response.put("responseTimeMs", time);
        return ResponseEntity.ok(response);
    }

    // بعد الحل
    @PostMapping("/with")
    public ResponseEntity<Map<String, Object>> withLoadBalancing() {
        long start  = System.currentTimeMillis();
        String result = loadBalancerService.handleWithLoadBalancing();
        long time   = System.currentTimeMillis() - start;

        Map<String, Object> response = new HashMap<>();
        response.put("mode",          "WITH Load Balancing - Round Robin");
        response.put("result",        result);
        response.put("responseTimeMs", time);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<LoadBalancerService.ServerStats> stats =
                loadBalancerService.getStats();

        Map<String, Object> response = new HashMap<>();
        response.put("totalRequests", loadBalancerService.getTotalRequests());
        response.put("servers",       stats);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        loadBalancerService.resetAll();
        return ResponseEntity.ok("Reset done");
    }


    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare() {
        List<LoadBalancerService.ServerStats> stats =
                loadBalancerService.getStats();

        Map<String, Object> response = new HashMap<>();
        for (LoadBalancerService.ServerStats s : stats) {
            response.put(s.serverName, s.requestsHandled + " requests");
        }

        if (stats.size() >= 3) {
            boolean isBalanced =
                    Math.abs(stats.get(0).requestsHandled -
                            stats.get(1).requestsHandled) <= 2 &&
                            Math.abs(stats.get(1).requestsHandled -
                                    stats.get(2).requestsHandled) <= 2;

            response.put("isBalanced", isBalanced);
            response.put("conclusion",
                    isBalanced
                            ? "Load Balancing works: distribution is balanced"
                            : "Load Balancing not tested yet");
        }
        return ResponseEntity.ok(response);
    }
}
