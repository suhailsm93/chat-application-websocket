package com.chat.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "user_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrivacySettings {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_seen_visibility", nullable = false)
    private Visibility lastSeenVisibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_status_visibility", nullable = false)
    private Visibility onlineStatusVisibility;

    @Column(name = "read_receipts_enabled", nullable = false)
    private boolean readReceiptsEnabled;

    public enum Visibility {
        EVERYONE, CONTACTS, NOBODY
    }
}