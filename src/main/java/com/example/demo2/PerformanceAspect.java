package com.example.demo2;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger =
            LoggerFactory.getLogger(PerformanceAspect.class);

    @Around("execution(* com.example.demo2..*(..))")
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {

        long start = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long end = System.currentTimeMillis();

        logger.info("AOP -> {} executed in {} ms",
                joinPoint.getSignature(),
                (end - start));

        return result;
    }
}