package com.chat.common.ratelimit;

import com.chat.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter rateLimiter;
    private final int authLimit;
    private final int mediaLimit;

    public RateLimitInterceptor(
            RedisRateLimiter rateLimiter,
            @Value("${app.rate-limit.auth-limit:100}") int authLimit,
            @Value("${app.rate-limit.media-limit:50}") int mediaLimit) {
        this.rateLimiter = rateLimiter;
        this.authLimit = authLimit;
        this.mediaLimit = mediaLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String clientIp = getClientIP(request);

        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/register")) {
            String key = "rate:auth:" + clientIp;
            if (!rateLimiter.isAllowed(key, authLimit, Duration.ofMinutes(1))) {
                throw new BadRequestException("Too many authentication requests. Please try again in a minute.");
            }
        }

        if (uri.startsWith("/api/media/upload-url")) {
            String key = "rate:media:" + clientIp;
            if (!rateLimiter.isAllowed(key, mediaLimit, Duration.ofMinutes(1))) {
                throw new BadRequestException("Media upload limit reached. Please wait before uploading more files.");
            }
        }

        return true;
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}