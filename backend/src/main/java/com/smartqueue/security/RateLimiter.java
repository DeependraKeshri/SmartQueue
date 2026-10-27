package com.smartqueue.security;

import java.time.Duration;

public interface RateLimiter {
    /** @return true if the call is allowed within the window. */
    boolean tryAcquire(String key, int limit, Duration window);
}
