package com.example.demo2.model;

import jakarta.persistence.*;
import java.math.BigDecimal;


@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "wallet_balance", nullable = false)
    private BigDecimal walletBalance;

    // ── Constructors ───────
    public User() {}

    public User(String username, BigDecimal walletBalance) {
        this.username = username;
        this.walletBalance = walletBalance;
    }

    // ── Getters & Setters ────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username
                + "', walletBalance=" + walletBalance + "}";
    }
}
