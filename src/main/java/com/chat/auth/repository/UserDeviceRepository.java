package com.chat.auth.repository;

import com.chat.auth.model.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {
    Optional<UserDeviceEntity> findByUserIdAndDeviceName(UUID userId, String deviceName);
}