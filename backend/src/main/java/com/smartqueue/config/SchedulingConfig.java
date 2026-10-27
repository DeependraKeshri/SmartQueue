package com.smartqueue.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

@Configuration
public class SchedulingConfig {

    /** Named taskScheduler so Spring uses it for @Scheduled and WebSocket heartbeats. */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("sq-sched-");
        s.setRemoveOnCancelPolicy(true);
        return s;
    }

    /** Executor for outbound notifications (email / push) so they never block request threads. */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(500);
        e.setThreadNamePrefix("sq-notify-");
        e.initialize();
        return e;
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "smartqueue.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class EnableSchedulingConfig {
    }
}
