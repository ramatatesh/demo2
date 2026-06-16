package com.example.demo2.controller;

import com.example.demo2.model.Product;
import com.example.demo2.service.ProductService;
import com.example.demo2.service.ProductService1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService1 productService1;

    public ProductController(ProductService1 productService1) {
        this.productService1 = productService1;
    }
    @Value("${server.port}")
    private String port;

    @Autowired
    private ProductService productService;
    private  ProductService1 service;
    @GetMapping
    public List<Product> getAllProducts(@RequestParam(required = false, defaultValue = "false") boolean useCache) {
        System.out.println("استعلام API: جلب كل المنتجات - useCache=" + useCache);
        return productService.getAllProducts(useCache);
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id,
                                  @RequestParam(required = false, defaultValue = "false") boolean useCache) {
        System.out.println("استعلام API: جلب المنتج برقم " + id + " - useCache=" + useCache);
        Optional<Product> product = productService.getProductById(id, useCache);
        return product.orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @PostMapping
    public Product addProduct(@RequestBody Product product) {
        System.out.println("استعلام API: إضافة منتج جديد");
        return productService.addProduct(product);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        System.out.println("استعلام API: تحديث منتج رقم " + id);
        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) {
        System.out.println("استعلام API: حذف منتج رقم " + id);
        productService.deleteProduct(id);
        return "Product deleted";
    }


    @PostMapping("/buy")
    public ResponseEntity<String> buyProduct(
            @RequestParam Long productId,
            @RequestParam int quantity,
            @RequestParam boolean useLock) {

        boolean success = productService.buyProduct(productId, quantity, useLock);

        return success
                ? ResponseEntity.ok("تمت العملية بنجاح")
                : ResponseEntity.status(423).body("فشلت العملية (بسبب تعارض الأقفال أو نفاد الكمية)");
    }
// للطلب الأول

    @PostMapping("/buy/{id}")
    public ResponseEntity<String> buy(@PathVariable Long id,
                                      @RequestBody Product request,
                                      @RequestParam(defaultValue = "false") boolean useFix) {

        String result;

        if (useFix) {
            result = productService1.buyProductOptimized(id, request.getStockQuantity());
        } else {
            result = productService1.buyProductLegacy(id, request.getStockQuantity());
        }

        if (result.contains("❌") || result.contains("غير متوفرة") || result.contains("فشل")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/instance")
    public String instance() {
        return "Running on port: " + port;
    }
}