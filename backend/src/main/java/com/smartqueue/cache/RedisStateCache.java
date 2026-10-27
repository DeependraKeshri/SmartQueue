package com.smartqueue.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Redis-backed cache. Any Redis failure degrades to a cache miss: the database stays authoritative. */
@Component
@ConditionalOnProperty(name = "smartqueue.redis.enabled", havingValue = "true")
public class RedisStateCache implements StateCache {
    private static final Logger log = LoggerFactory.getLogger(RedisStateCache.class);
    private final StringRedisTemplate redis;

    public RedisStateCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get("sq:" + key));
        } catch (Exception e) {
            log.warn("Redis get failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set("sq:" + key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis put failed: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void evict(String key) {
        try {
            redis.delete("sq:" + key);
        } catch (Exception e) {
            log.warn("Redis evict failed: {}", e.getClass().getSimpleName());
        }
    }
}
