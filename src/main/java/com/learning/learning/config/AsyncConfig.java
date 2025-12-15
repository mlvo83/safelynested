package com.learning.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration to enable async processing for email notifications
 * This allows emails to be sent in the background without blocking the main request
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    /**
     * Custom executor for async tasks
     * This ensures we have a proper thread pool for email sending
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("EmailAsync-");
        executor.initialize();
        return executor;
    }
}