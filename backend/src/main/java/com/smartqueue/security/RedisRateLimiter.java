package com.smartqueue.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Distributed fixed-window limiter (INCR + EXPIRE). Fails open if Redis is unreachable. */
@Component
@ConditionalOnProperty(name = "smartqueue.redis.enabled", havingValue = "true")
public class RedisRateLimiter implements RateLimiter {
    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            String k = "rl:" + key;
            Long n = redis.opsForValue().increment(k);
            if (n != null && n == 1L) {
                redis.expire(k, window);
            }
            return n == null || n <= limit;
        } catch (Exception e) {
            return true;
        }
    }
}
