package com.chat.websocket.registry;

import com.chat.common.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisConnectionRegistry {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final RedisConfig redisConfig;
    private final ObjectMapper objectMapper;

    private static final String USER_DEVICES_KEY = "ws:user:devices:";
    private static final String NODE_CHANNEL_PREFIX = "channel:node:";

    private final Map<String, BiConsumer<String, String>> frameSubscribers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initNodeSubscription() {
        String myNodeChannel = NODE_CHANNEL_PREFIX + redisConfig.getNodeId();
        MessageListener listener = (message, pattern) -> {
            try {
                String payload = new String(message.getBody());
                // Incoming frame delegated to local node session handler
                for (BiConsumer<String, String> subscriber : frameSubscribers.values()) {
                    subscriber.accept(redisConfig.getNodeId(), payload);
                }
            } catch (Exception e) {
                log.error("Error handling cross-node Redis pub/sub frame: ", e);
            }
        };
        listenerContainer.addMessageListener(listener, new ChannelTopic(myNodeChannel));
        log.info("Subscribed to Redis node channel: {}", myNodeChannel);
    }

    public void registerLocalConnection(UUID userId, UUID deviceId) {
        String key = USER_DEVICES_KEY + userId;
        redisTemplate.opsForHash().put(key, deviceId.toString(), redisConfig.getNodeId());
    }

    public void unregisterLocalConnection(UUID userId, UUID deviceId) {
        String key = USER_DEVICES_KEY + userId;
        redisTemplate.opsForHash().delete(key, deviceId.toString());
    }

    public Map<Object, Object> getUserActiveNodes(UUID userId) {
        String key = USER_DEVICES_KEY + userId;
        return redisTemplate.opsForHash().entries(key);
    }

    public void routeFrameToNode(String targetNodeId, String frameJson) {
        if (targetNodeId.equals(redisConfig.getNodeId())) {
            // Self delivery
            for (BiConsumer<String, String> subscriber : frameSubscribers.values()) {
                subscriber.accept(targetNodeId, frameJson);
            }
        } else {
            // Cross-node pub/sub delivery
            String targetChannel = NODE_CHANNEL_PREFIX + targetNodeId;
            redisTemplate.convertAndSend(targetChannel, frameJson);
        }
    }

    public void registerFrameSubscriber(String subscriberId, BiConsumer<String, String> handler) {
        frameSubscribers.put(subscriberId, handler);
    }
}