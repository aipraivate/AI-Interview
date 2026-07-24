package com.interview.platform.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component @Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastRedisWarningMinute = new AtomicLong(-1);
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    public RateLimitFilter(ObjectProvider<StringRedisTemplate> redis,
                           @Value("${app.rate-limit.redis-enabled:false}") boolean redisEnabled) {
        this.redis = redis.getIfAvailable();
        this.redisEnabled = redisEnabled;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        int limit = path.startsWith("/api/v1/auth/") ? 30 : 360;
        long minute = Instant.now().getEpochSecond() / 60;
        String key = request.getRemoteAddr() + ':' + (path.startsWith("/api/v1/auth/") ? "auth" : "api");
        int count = increment(key, minute);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
        if (count > limit) {
            response.setStatus(429); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后重试\"}");
            return;
        }
        if (windows.size() > 20_000) windows.entrySet().removeIf(value -> value.getValue().minute < minute - 2);
        chain.doFilter(request, response);
    }

    private int increment(String key, long minute) {
        if (redisEnabled && redis != null) {
            try {
                String redisKey = "rate-limit:" + minute + ':' + key;
                Long count = redis.opsForValue().increment(redisKey);
                if (count != null && count == 1) redis.expire(redisKey, Duration.ofMinutes(2));
                if (count != null) return Math.toIntExact(Math.min(count, Integer.MAX_VALUE));
            } catch (RuntimeException exception) {
                // Redis 故障时退化到单实例限流，避免认证和主流程一起失效。
                if (lastRedisWarningMinute.getAndSet(minute) != minute) {
                    log.warn("Redis rate limiter unavailable; using local fallback", exception);
                }
            }
        }
        Window window = windows.compute(key, (ignored, old) -> old == null || old.minute != minute
                ? new Window(minute) : old);
        return window.count.incrementAndGet();
    }
    private static final class Window {
        private final long minute; private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}
