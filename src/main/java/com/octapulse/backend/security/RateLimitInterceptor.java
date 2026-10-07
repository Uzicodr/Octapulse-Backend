package com.octapulse.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window limit per client IP. In-memory, so it's per instance; fine for a single
 * Render instance. The client IP comes from getRemoteAddr(), which Tomcat resolves from
 * X-Forwarded-For when server.forward-headers-strategy=native.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_TRACKED = 50_000;

    private final int limit;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitInterceptor(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    private record Window(long start, AtomicInteger count) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED) {
            windows.values().removeIf(w -> now - w.start() >= windowMillis);
        }
        Window window = windows.compute(request.getRemoteAddr(), (ip, w) ->
                w == null || now - w.start() >= windowMillis ? new Window(now, new AtomicInteger()) : w);
        if (window.count().incrementAndGet() > limit) {
            long retryAfter = Math.max(1, (window.start() + windowMillis - now) / 1000);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setStatus(429);
            return false;
        }
        return true;
    }
}
