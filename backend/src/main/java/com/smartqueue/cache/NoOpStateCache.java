package com.smartqueue.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "smartqueue.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpStateCache implements StateCache {
    @Override public Optional<String> get(String key) { return Optional.empty(); }
    @Override public void put(String key, String value, Duration ttl) { }
    @Override public void evict(String key) { }
}
