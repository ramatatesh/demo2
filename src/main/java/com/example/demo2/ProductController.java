package com.example.demo2;

import com.example.demo2.Product;
import com.example.demo2.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    public Product add(@RequestBody Product p) {
        return service.addProduct(p); // سيعمل الآن لأن التابع موجود في السيرفس
    }

    @GetMapping
    public List<Product> all() {
        return service.getAll(); // سيعمل الآن لأن التابع موجود في السيرفس
    }

    @PostMapping("/buy/{id}")
    public String buy(@PathVariable Long id,
                      @RequestBody Product request,
                      @RequestParam(defaultValue = "false") boolean useFix) {
        if (useFix) {
            return service.buyProductOptimized(id, request.getQuantity());
        } else {
            return service.buyProductLegacy(id, request.getQuantity());
        }
    }

}