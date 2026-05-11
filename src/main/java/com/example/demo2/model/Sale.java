package com.example.demo2.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// @Entity = Spring يعرف أن هذا الكلاس يمثل جدولاً في قاعدة البيانات
@Entity
// @Table = اسم الجدول في MySQL
@Table(name = "sales")
public class Sale {

    // المفتاح الأساسي، يزيد تلقائياً (1, 2, 3...)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // أي منتج بيع
    private Long productId;

    // الكمية المباعة
    private int quantity;

    // وقت البيع - نستخدمه لفلترة "مبيعات اليوم"
    private LocalDateTime saleDate;

    // هل تمت معالجة هذه المبيعة؟
    // false = في الانتظار، true = تمت معالجتها
    // هذا هو مبدأ Idempotency من السلايد 10!
    private boolean processed = false;

    // الإيراد من هذه المبيعة
    private double revenue;

    // Constructor فارغ (مطلوب من Spring)
    public Sale() {}

    // Constructor للاستخدام في الكود
    public Sale(Long productId, int quantity, double revenue) {
        this.productId = productId;
        this.quantity = quantity;
        this.revenue = revenue;
        this.saleDate = LocalDateTime.now();
        // processed = false تلقائياً (في الانتظار)
    }

    // Getters & Setters (مطلوبة من Spring)
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
    public double getRevenue() { return revenue; }
    public LocalDateTime getSaleDate() { return saleDate; }
}