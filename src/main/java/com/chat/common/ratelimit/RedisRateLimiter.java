package com.chat.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean isAllowed(String key, int maxRequests, Duration window) {
        String rateKey = "rate:" + key;
        Long currentRequests = redisTemplate.opsForValue().increment(rateKey);

        if (currentRequests != null && currentRequests == 1) {
            redisTemplate.expire(rateKey, window);
        }

        return currentRequests != null && currentRequests <= maxRequests;
    }
}