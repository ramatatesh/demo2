package com.example.demo2;

import jakarta.persistence.*;

@Entity

public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int stockQuantity;
    public int getQuantity() {
        return stockQuantity;
    }

    public void setQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
}