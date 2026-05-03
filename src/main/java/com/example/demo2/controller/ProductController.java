package com.example.demo2.controller;

import com.example.demo2.model.Product;
import com.example.demo2.service.AsyncQueueService;
import com.example.demo2.service.CheckoutService;
import com.example.demo2.service.OrderService;
import com.example.demo2.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final OrderService orderService;
    private final CheckoutService checkoutService;

    public ProductController(
            ProductService productService,
            OrderService orderService,
            CheckoutService checkoutService
    ) {
        this.productService = productService;
        this.orderService = orderService;
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public Product add(@RequestBody Product p) {
        return productService.addProduct(p);
    }

    @GetMapping
    public List<Product> all() {
        return productService.getAllProducts();
    }

    // Toggle للمتطلب الأول (موجود عندك)
    @PostMapping("/buy/{id}")
    public String buy(@PathVariable Long id,
                      @RequestBody Product request,
                      @RequestParam(defaultValue = "false") boolean useFix) {
        if (useFix) {
            return productService.buyProductOptimized(id, request.getStockQuantity());
        } else {
            return productService.buyProductLegacy(id, request.getStockQuantity());
        }
    }

    // ══════════════════════════════════════════
    // Toggle للمتطلب الثاني: Thread Pool
    // ══════════════════════════════════════════

    // ?useFix=false → Legacy (بدون Thread Pool)
    // ?useFix=true  → مع Thread Pool
    @PostMapping("/order/{id}")
    public String placeOrder(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int qty,
            @RequestParam(defaultValue = "false") boolean useFix
    ) throws Exception {

        if (useFix) {
            // الحل: نُرسل الطلب إلى Thread Pool
            Future<String> future = orderService.processOrderWithPool(id, qty);
            // future.get() ينتظر النتيجة (blocking للتبسيط)
            return future.get();
        } else {
            // المشكلة: ينفذ مباشرة في thread الـ HTTP request
            return orderService.processOrderLegacy(id, qty);
        }
    }

    // Endpoint لعرض إحصائيات الـ Thread Pool (مهم جداً للعرض)
    @GetMapping("/pool/stats")
    public OrderService.ThreadPoolStats poolStats() {
        return orderService.getPoolStats();
    }

    // ══════════════════════════════════════
    // Toggle للمتطلب الثالث: Async Queue
    // ══════════════════════════════════════

    // ?useFix=false → Synchronous (المشكلة: انتظار ~4350ms)
    // ?useFix=true  → Asynchronous (الحل: رد فوري ~55ms)
    @PostMapping("/checkout/{productId}")
    public CheckoutService.CheckoutResult checkout(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "customer@example.com") String email,
            @RequestParam(defaultValue = "false") boolean useFix
    ) throws Exception {

        if (useFix) {
            return checkoutService.checkoutAsync(productId, email);
        } else {
            return checkoutService.checkoutLegacy(productId, email);
        }
    }

    // إحصائيات الـ Queue (للعرض أمام المعيدة)
    @GetMapping("/queue/stats")
    public AsyncQueueService.QueueStats queueStats() {
        return checkoutService.getQueueStats();
    }
}