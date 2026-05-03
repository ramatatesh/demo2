package com.example.demo2.service;

import com.example.demo2.model.Product;
import com.example.demo2.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product addProduct(Product product) {
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    // BEFORE: Race Condition Problem
    public String buyProductLegacy(Long productId, int quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return "المنتج غير موجود ❌";
        }

        Product product = productOpt.get();
        
        // إضافة تأخير اصطناعي لإظهار Race Condition
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (product.getStockQuantity() >= quantity) {
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);
            return "تم شراء " + quantity + " بنجاح (Legacy) ✅";
        } else {
            return "الكمية غير متوفرة ❌";
        }
    }

    // AFTER: Solution with Pessimistic Locking
    @Transactional
    public String buyProductOptimized(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("المنتج غير موجود ❌"));

        if (product.getStockQuantity() >= quantity) {
            product.setStockQuantity(product.getStockQuantity() - quantity);
            return "تم الشراء بنجاح (Optimized) ✅";
        } else {
            return "الكمية غير متوفرة ❌";
        }
    }
}
