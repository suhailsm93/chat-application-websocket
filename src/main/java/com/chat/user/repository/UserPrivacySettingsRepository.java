package com.chat.user.repository;

import com.chat.user.model.UserPrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserPrivacySettingsRepository extends JpaRepository<UserPrivacySettings, UUID> {
}