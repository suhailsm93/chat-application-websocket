package com.chat.conversation.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ConversationMember.ConversationMemberId.class)
public class ConversationMember {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    public enum MemberRole {
        ADMIN, MEMBER
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMemberId implements Serializable {
        private UUID conversationId;
        private UUID userId;
    }

    @PrePersist
    public void prePersist() {
        if (joinedAt == null) joinedAt = Instant.now();
        if (role == null) role = MemberRole.MEMBER;
    }
}