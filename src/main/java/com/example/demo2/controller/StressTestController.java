package com.example.demo2.controller;

import com.example.demo2.service.StressTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

//    POST /stress/run?useFix=false&users=100  → BEFORE (بدون Barrier)
//    POST /stress/run?useFix=true&users=100   → AFTER  (مع CountDownLatch)
//    POST /stress/buy-test?useFix=false&users=100&productId=1 → Race Condition
//    POST /stress/buy-test?useFix=true&users=100&productId=1  → محمي
@RestController
@RequestMapping("/stress")
public class StressTestController {

    private final StressTestService stressTestService;

    public StressTestController(StressTestService stressTestService) {
        this.stressTestService = stressTestService;
    }


    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runStressTest(
            @RequestParam(defaultValue = "false") boolean useFix,
            @RequestParam(defaultValue = "100")   int users
    ) throws InterruptedException {

        Map<String, Object> result = useFix
                ? stressTestService.runWithCountDownLatch(users)      // AFTER
                : stressTestService.runWithoutSynchronization(users); // BEFORE

        return ResponseEntity.ok(result);
    }

    @PostMapping("/buy-test")
    public ResponseEntity<Map<String, Object>> buyStressTest(
            @RequestParam(defaultValue = "false") boolean useFix,
            @RequestParam(defaultValue = "100")   int users,
            @RequestParam(defaultValue = "1")     Long productId
    ) throws InterruptedException {

        Map<String, Object> result = useFix
                ? stressTestService.stressTestBuyOptimized(users, productId) //
                : stressTestService.stressTestBuyLegacy(users, productId);   //

        return ResponseEntity.ok(result);
    }
}