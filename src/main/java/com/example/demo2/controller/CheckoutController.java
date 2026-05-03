package com.example.demo2.controller;

import com.example.demo2.service.CheckoutService;
import com.example.demo2.service.OrderService;
import com.example.demo2.service.AsyncQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.Future;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    @Autowired
    public CheckoutController(CheckoutService checkoutService, OrderService orderService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
    }

    // BEFORE: Synchronous Processing
    @PostMapping("/legacy/{productId}")
    public ResponseEntity<CheckoutService.CheckoutResult> checkoutLegacy(
            @PathVariable Long productId,
            @RequestParam String email) throws InterruptedException {
        
        CheckoutService.CheckoutResult result = checkoutService.checkoutLegacy(productId, email);
        return ResponseEntity.ok(result);
    }

    // AFTER: Asynchronous Processing
    @PostMapping("/async/{productId}")
    public ResponseEntity<CheckoutService.CheckoutResult> checkoutAsync(
            @PathVariable Long productId,
            @RequestParam String email) throws InterruptedException {
        
        CheckoutService.CheckoutResult result = checkoutService.checkoutAsync(productId, email);
        return ResponseEntity.ok(result);
    }

    // Thread Pool Comparison
    @PostMapping("/order/legacy/{productId}")
    public ResponseEntity<String> orderLegacy(
            @PathVariable Long productId,
            @RequestParam int quantity) throws InterruptedException {
        
        String result = orderService.processOrderLegacy(productId, quantity);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/order/pool/{productId}")
    public ResponseEntity<Future<String>> orderWithPool(
            @PathVariable Long productId,
            @RequestParam int quantity) {
        
        Future<String> result = orderService.processOrderWithPool(productId, quantity);
        return ResponseEntity.ok(result);
    }

    // Statistics Endpoints
    @GetMapping("/stats/pool")
    public ResponseEntity<OrderService.ThreadPoolStats> getPoolStats() {
        return ResponseEntity.ok(orderService.getPoolStats());
    }

    @GetMapping("/stats/queue")
    public ResponseEntity<AsyncQueueService.QueueStats> getQueueStats() {
        return ResponseEntity.ok(checkoutService.getQueueStats());
    }
}
