package com.example.demo2.repository;

import com.example.demo2.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

// @Repository = Spring يعرف أن هذا الكلاس يتعامل مع قاعدة البيانات
@Repository
// JpaRepository<Sale, Long> = يعطينا save, findAll, deleteById وغيرها مجاناً
public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Spring يُولّد SQL تلقائياً من اسم الـ method:
    // SELECT * FROM sales WHERE processed = false
    // هذا مبدأ Idempotency: نعالج فقط ما لم يُعالَج بعد!
    List<Sale> findByProcessedFalse();
}