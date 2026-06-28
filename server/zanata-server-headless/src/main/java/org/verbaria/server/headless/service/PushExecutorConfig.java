package org.verbaria.server.headless.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PushExecutorConfig {

    @Bean(name = "pushOrchestratorExecutor", destroyMethod = "shutdown")
    public AsyncTaskExecutor pushOrchestratorExecutor(
            @Value("${verbaria.push.orchestrators:8}") int max) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-orchestrator-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(Math.max(1, max));
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }

    @Bean(name = "pushWorkerExecutor", destroyMethod = "shutdown")
    public AsyncTaskExecutor pushWorkerExecutor(
            @Value("${verbaria.push.threads:4}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-worker-");
        int size = Math.max(1, threads);
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.initialize();
        return executor;
    }
}
