package com.example.demo2.model;


// هذا الـ record يمثل "Message" الذي يُرسل إلى الـ Queue
// مثل البطاقة التي تُعطيها لعامل المطعم لتجهيز طلبك
public record NotificationTask(
        Long orderId,         // رقم الطلب
        String customerEmail, // إيميل العميل
        String productName,   // اسم المنتج
        String taskType       // نوع المهمة: "EMAIL", "INVOICE", "WAREHOUSE"
) {}