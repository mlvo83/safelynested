package com.learning.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool that runs the help assistant's streaming calls to the Claude API. A model
 * reply can take tens of seconds, so it must never run on a servlet request thread; the
 * controller hands the work to this executor and streams the result back through an
 * {@code SseEmitter}. Kept separate from {@code taskExecutor} (email) so a burst of chat
 * traffic cannot delay outgoing mail, or the other way round.
 */
@Configuration
public class AssistantConfig {

    @Bean(name = "assistantExecutor")
    public Executor assistantExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Assistant-");
        executor.initialize();
        return executor;
    }
}
