package com.example.demo2.controller;

import com.example.demo2.model.Sale;
import com.example.demo2.repository.SaleRepository;
import com.example.demo2.service.BatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final BatchService batchService;
    private final SaleRepository saleRepo;

    public BatchController(BatchService batchService, SaleRepository saleRepo) {
        this.batchService = batchService;
        this.saleRepo = saleRepo;
    }

    @PostMapping("/process")
    public ResponseEntity<BatchService.BatchResult> process(
            @RequestParam(defaultValue = "batch") String mode
    ) throws InterruptedException {

        BatchService.BatchResult result;

        switch (mode.toLowerCase()) {

            case "realtime":
                result = batchService.processRealTime();
                break;

            case "parallel":
                result = batchService.processBatchParallel();
                break;

            default:
                result = batchService.processBatchBatch();
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seed(
            @RequestParam(defaultValue = "10") int count
    ) {
        for (int i = 0; i < count; i++) {
            saleRepo.save(new Sale((long)(i % 5 + 1), i + 1, (i + 1) * 15.0));
        }
        return ResponseEntity.ok(" أُضيفت " + count + " مبيعة جديدة للاختبار");
    }

    @PostMapping("/stress-test")
    public ResponseEntity<Map<String, Object>> stressTest(
            @RequestParam(defaultValue = "10") int count
    ) throws InterruptedException {

        Map<String, Object> res = new HashMap<>();

        for (int i = 0; i < count; i++) {
            saleRepo.save(new Sale((long)(i % 5 + 1), 1, 10.0));
        }
        long t3 = System.currentTimeMillis();
        batchService.processRealTime();
        long realtimeMs = System.currentTimeMillis() - t3;


        for (int i = 0; i < count; i++) {
            saleRepo.save(new Sale((long)(i % 5 + 1), 1, 10.0));
        }

        long t1 = System.currentTimeMillis();
        batchService.processBatchBatch();
        long sequentialMs = System.currentTimeMillis() - t1;

        for (int i = 0; i < count; i++) {
            saleRepo.save(new Sale((long)(i % 5 + 1), 1, 10.0));
        }
        long t2 = System.currentTimeMillis();
        batchService.processBatchParallel();
        long parallelMs = System.currentTimeMillis() - t2;


        res.put("salesPerTest", count);

        res.put("realtimeMs", realtimeMs);
        res.put("sequentialMs", sequentialMs);
        res.put("parallelMs", parallelMs);

        res.put(
                "parallelImprovement",
                String.format("%.1fx", (double) sequentialMs / parallelMs)
        );

        res.put(
                "realtimeImprovement",
                String.format("%.1fx", (double) sequentialMs / realtimeMs)
        );

        return ResponseEntity.ok(res);
    }
}