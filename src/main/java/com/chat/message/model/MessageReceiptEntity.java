package com.chat.message.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(MessageReceiptEntity.MessageReceiptId.class)
public class MessageReceiptEntity {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReceiptId implements Serializable {
        private UUID messageId;
        private UUID userId;
    }
}