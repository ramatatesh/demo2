package com.example.demo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "purchaseExecutor")
    public Executor purchaseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // التحكم في الموارد:
        executor.setCorePoolSize(2); // المسار بعد الحل: قصرنا التنفيذ على خيطين فقط لإدارة الموارد
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("PurchaseThread-");
        executor.initialize();
        return executor;
    }
}