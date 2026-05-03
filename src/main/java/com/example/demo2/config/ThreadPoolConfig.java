package com.example.demo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

// @Configuration = هذا الكلاس يحتوي على إعدادات للمشروع
@Configuration
public class ThreadPoolConfig {

    // @Bean = Spring سيُنشئ هذا الـ object وحده ويضعه للاستخدام في كل مكان
    @Bean(name = "orderExecutor")
    public ThreadPoolExecutor orderThreadPool() {

        return new ThreadPoolExecutor(
                5,                              // corePoolSize: الحد الأدنى من threads دائماً شغالة
                10,                             // maximumPoolSize: الحد الأقصى من threads
                60L,                            // keepAliveTime: الوقت قبل حذف thread زائدة
                TimeUnit.SECONDS,               // وحدة الوقت = ثانية
                new ArrayBlockingQueue<>(100),  // Queue: تسع 100 طلب في الانتظار
                new ThreadPoolExecutor.CallerRunsPolicy() // إذا امتلأت القائمة: نفذ الطلب في thread المُرسل
        );
    }
}