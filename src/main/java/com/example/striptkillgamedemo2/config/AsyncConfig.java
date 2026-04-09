package com.example.striptkillgamedemo2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器配置。
 * <p>
 * 开启 Spring 的 {@code @EnableAsync} 支持，并提供名为 {@code aiExecutor} 的线程池，
 * 专用于 AI 代理/DM 的 LLM 调用，避免阻塞请求处理线程。
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 构造 AI 引擎专用线程池。
     *
     * @return 供 {@code @Async("aiExecutor")} 使用的线程池执行器
     */
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-engine-");
        executor.initialize();
        return executor;
    }
}
