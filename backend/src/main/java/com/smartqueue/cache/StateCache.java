package com.smartqueue.cache;

import java.time.Duration;
import java.util.Optional;

/** Small string cache port. Redis implementation when enabled, no-op otherwise. Never the source of truth. */
public interface StateCache {
    Optional<String> get(String key);
    void put(String key, String value, Duration ttl);
    void evict(String key);
}
