package com.chat.presence.service;

import com.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String PRESENCE_KEY_PREFIX = "presence:user:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    public void heartbeat(UUID userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "ONLINE", PRESENCE_TTL);
    }

    public void setOffline(UUID userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        updateUserLastSeen(userId);
    }

    public boolean isUserOnline(UUID userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Optional<Instant> getLastSeen(UUID userId) {
        if (isUserOnline(userId)) {
            return Optional.of(Instant.now());
        }
        return userRepository.findById(userId).map(u -> u.getLastSeen());
    }

    public void updateUserLastSeen(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeen(Instant.now());
            userRepository.save(user);
        });
    }
}