package com.chat.websocket.service;

import com.chat.websocket.registry.RedisConnectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TypingIndicatorService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String TYPING_KEY_PREFIX = "typing:conv:";

    public void setTyping(UUID conversationId, UUID userId, boolean isTyping) {
        String key = TYPING_KEY_PREFIX + conversationId + ":user:" + userId;
        if (isTyping) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(5));
        } else {
            redisTemplate.delete(key);
        }
    }

    public boolean isTyping(UUID conversationId, UUID userId) {
        String key = TYPING_KEY_PREFIX + conversationId + ":user:" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}