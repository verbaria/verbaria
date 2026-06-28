package org.verbaria.server.monolith;

import com.vaadin.flow.spring.annotation.VaadinTaskExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class VaadinExecutorConfig {

    @Bean
    @VaadinTaskExecutor
    public AsyncTaskExecutor vaadinTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("vaadin-task-");
        executor.initialize();
        return executor;
    }
}
