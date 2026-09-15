package com.chat.auth.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActiveAt = now;
        this.isActive = true;
    }
}