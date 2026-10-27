package com.smartqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.common.ApiError;
import com.smartqueue.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Protects authentication endpoints and public write endpoints from brute force and abuse. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, AppProperties props, ObjectMapper mapper) {
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (props.getRateLimit().isEnabled()) {
            String path = req.getRequestURI();
            String ip = req.getRemoteAddr();
            Integer limit = null;
            String group = null;
            if (path.startsWith("/api/auth/")) {
                limit = props.getRateLimit().getAuthPerMinute();
                group = "auth";
            } else if (path.startsWith("/api/public/") && !"GET".equals(req.getMethod())) {
                limit = props.getRateLimit().getPublicPerMinute();
                group = "public";
            }
            if (limit != null && !limiter.tryAcquire(group + ":" + ip, limit, Duration.ofMinutes(1))) {
                res.setStatus(429);
                res.setHeader("Retry-After", "60");
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(res.getOutputStream(),
                        ApiError.of(429, "Too Many Requests", "Too many requests. Please slow down.", path));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
