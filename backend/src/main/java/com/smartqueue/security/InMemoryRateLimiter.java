package com.smartqueue.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window limiter used when Redis is disabled (single instance only). */
@Component
@ConditionalOnProperty(name = "smartqueue.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {
    private record Window(long start, int[] count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long ms = window.toMillis();
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() > ms);
        }
        Window w = windows.compute(key, (k, old) -> (old == null || now - old.start() >= ms)
                ? new Window(now, new int[]{1})
                : bump(old));
        return w.count()[0] <= limit;
    }

    private Window bump(Window w) {
        synchronized (w) {
            w.count()[0]++;
        }
        return w;
    }
}
