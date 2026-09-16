package com.chat.websocket.registry;

import com.chat.common.config.RedisConfig;
import com.chat.websocket.dto.NodeRoutingEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    private final Map<String, Consumer<NodeRoutingEnvelope>> envelopeSubscribers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initNodeSubscription() {
        String myNodeChannel = NODE_CHANNEL_PREFIX + redisConfig.getNodeId();
        MessageListener listener = (message, pattern) -> {
            try {
                byte[] body = message.getBody();
                // Remove raw serializer quotes if present, then deserialize envelope
                NodeRoutingEnvelope envelope = objectMapper.readValue(body, NodeRoutingEnvelope.class);
                
                envelopeSubscribers.values().forEach(subscriber -> subscriber.accept(envelope));
            } catch (Exception e) {
                log.error("Error handling cross-node Redis envelope: ", e);
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

    public void routeEnvelopeToNode(String targetNodeId, NodeRoutingEnvelope envelope) {
        if (targetNodeId.equals(redisConfig.getNodeId())) {
            // Deliver locally immediately
            envelopeSubscribers.values().forEach(subscriber -> subscriber.accept(envelope));
        } else {
            // Deliver across nodes via Redis Pub/Sub channel
            String targetChannel = NODE_CHANNEL_PREFIX + targetNodeId;
            redisTemplate.convertAndSend(targetChannel, envelope);
        }
    }

    public void registerEnvelopeSubscriber(String subscriberId, Consumer<NodeRoutingEnvelope> handler) {
        envelopeSubscribers.put(subscriberId, handler);
    }
}