package com.example.demo2.service;

import com.example.demo2.model.Product;
import com.example.demo2.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProductService1 {

    // كائن غير ساكن ومحمي بـ final لمنع الأخطاء
    private final ProductRepository repo;

    // الباني لضمان حقن الاعتماديات (Dependency Injection) من Spring
    public ProductService1(ProductRepository repo) {
        this.repo = repo;
    }

    public Product addProduct(Product p) {
        return repo.save(p);
    }

    public List<Product> getAll() {
        return repo.findAll();
    }

    // ─── المسار الأول: (المشكلة قبل الحل) ❌ ───
    public String buyProductLegacy(Long id, int qty) {
        // تعديل: استخدام this.repo للوصول إلى الكائن المُدار
        Product p = this.repo.findById(id).orElse(null);
        if (p == null) return "المنتج غير موجود ❌";

        if (p.getStockQuantity() >= qty) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            p.setStockQuantity(p.getStockQuantity() - qty);
            Product savedProduct = this.repo.save(p);

            System.out.println("✨ نجاح التحديث في قاعدة البيانات. الكمية المتبقية الحالية: " + savedProduct.getStockQuantity());

            return "تم شراء " + qty + " بنجاح (Legacy) ✅";
        }
        return "الكمية غير متوفرة ❌";
    }

    // ─── المسار الثاني: (الحل بعد التطبيق)  ───
    @Transactional
    public String buyProductOptimized(Long id, int qty) {

        Product p = this.repo.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("المنتج غير موجود "));

        if (p.getStockQuantity() >= qty) {
            p.setStockQuantity(p.getStockQuantity() - qty);
            Product savedProduct = this.repo.save(p);

            System.out.println(" [مع LOCK]: نجاح التحديث. الكمية المتبقية الحالية: " + savedProduct.getStockQuantity());

            return "تم الشراء بنجاح (Locked) ";
        }
        return "الكمية غير متوفرة ";
    }
}