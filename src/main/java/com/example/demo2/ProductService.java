package com.example.demo2;

import com.example.demo2.Product;
import com.example.demo2.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    // تأكد من وجود هذا التابع
    public Product addProduct(Product p) {
        return repo.save(p);
    }

    // تأكد من وجود هذا التابع
    public List<Product> getAll() {
        return repo.findAll();
    }

    // المسار الأول: (المشكلة)
    public String buyProductLegacy(Long id, int qty) {
        Product p = repo.findById(id).orElse(null);
        if (p == null) return "المنتج غير موجود ❌";

        if (p.getQuantity() >= qty) {
            // إضافة تأخير اصطناعي لإثبات الـ Race Condition في JMeter
            try { Thread.sleep(100); } catch (InterruptedException e) {}

            p.setQuantity(p.getQuantity() - qty);
            repo.save(p);
            return "تم شراء " + qty + " بنجاح (Legacy) ✅";
        }
        return "الكمية غير متوفرة ❌";
    }

    // المسار الثاني: (الحل)
    @Transactional
    public String buyProductOptimized(Long id, int qty) {
        Product p = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("المنتج غير موجود ❌"));

        if (p.getQuantity() >= qty) {
            p.setQuantity(p.getQuantity() - qty);
            return "تم الشراء بنجاح (Locked) ✅";
        }
        return "الكمية غير متوفرة ❌";
    }

}