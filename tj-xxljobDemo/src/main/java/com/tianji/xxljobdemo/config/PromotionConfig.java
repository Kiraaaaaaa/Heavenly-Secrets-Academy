package com.tianji.xxljobdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class PromotionConfig {

    @Bean //这里可以写@Bean("线程池id")，可以不加id，那么该线程池默认的名称就是方法名称: generateExchangeCodeExecutor
    public Executor generateExchangeCodeExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1.核心线程池大小
        executor.setCorePoolSize(2);
        // 2.最大线程池大小
        executor.setMaxPoolSize(5);
        // 3.队列大小
        executor.setQueueCapacity(200);
        // 4.线程名称
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 5.拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}