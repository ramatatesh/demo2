package com.example.demo2.model;

public record NotificationTask(
        Long orderId,
        String customerEmail,
        String productName,
        String taskType
) {}