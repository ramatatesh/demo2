package com.example.demo2;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.*;

@SpringBootApplication
// @EnableScheduling: يُفعّل @Scheduled
// بدون هذا السطر لن يعمل الـ Batch Job التلقائي!
@EnableScheduling
public class Demo2Application {
    public static void main(String[] args) {
        SpringApplication.run(Demo2Application.class, args);
    }
}
