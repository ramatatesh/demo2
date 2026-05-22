package com.example.demo2.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private int quantity;
    private LocalDateTime saleDate;
    private boolean processed = false;
    private double revenue;
    public Sale() {}

    public Sale(Long productId, int quantity, double revenue) {
        this.productId = productId;
        this.quantity = quantity;
        this.revenue = revenue;
        this.saleDate = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
    public double getRevenue() { return revenue; }
    public LocalDateTime getSaleDate() { return saleDate; }
}