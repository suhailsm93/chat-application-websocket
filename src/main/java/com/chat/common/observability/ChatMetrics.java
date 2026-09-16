package com.chat.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ChatMetrics {

    private final Counter messagesSentCounter;
    private final Counter messagesDeliveredCounter;
    private final Timer messageDeliveryTimer;

    public ChatMetrics(MeterRegistry registry) {
        this.messagesSentCounter = Counter.builder("chat.messages.sent.total")
                .description("Total messages sent through WebSocket gateway")
                .register(registry);

        this.messagesDeliveredCounter = Counter.builder("chat.messages.delivered.total")
                .description("Total messages acknowledged as delivered")
                .register(registry);

        this.messageDeliveryTimer = Timer.builder("chat.message.delivery.time")
                .description("Time taken from message creation to recipient delivery ack")
                .register(registry);
    }

    public void incrementMessagesSent() {
        messagesSentCounter.increment();
    }

    public void incrementMessagesDelivered() {
        messagesDeliveredCounter.increment();
    }

    public void recordDeliveryLatency(long durationMs) {
        messageDeliveryTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}