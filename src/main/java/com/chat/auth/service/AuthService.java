package com.chat.auth.service;

import com.chat.auth.dto.AuthDtos.*;
import com.chat.auth.model.UserDeviceEntity;
import com.chat.auth.repository.UserDeviceRepository;
import com.chat.auth.security.JwtTokenProvider;
import com.chat.common.exception.BadRequestException;
import com.chat.common.exception.UnauthorizedException;
import com.chat.user.model.UserEntity;
import com.chat.user.model.UserPrivacySettings;
import com.chat.user.repository.UserPrivacySettingsRepository;
import com.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered");
        }

        UserEntity user = UserEntity.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .about("Hey there! I am using Chat.")
                .build();

        user = userRepository.save(user);

        UserPrivacySettings privacy = UserPrivacySettings.builder()
                .userId(user.getId())
                .lastSeenVisibility(UserPrivacySettings.Visibility.EVERYONE)
                .onlineStatusVisibility(UserPrivacySettings.Visibility.EVERYONE)
                .readReceiptsEnabled(true)
                .build();
        privacySettingsRepository.save(privacy);

        UserDeviceEntity device = UserDeviceEntity.builder()
                .userId(user.getId())
                .deviceName(request.deviceName())
                .deviceType(request.deviceType())
                .isActive(true)
                .lastActiveAt(Instant.now())
                .build();

        device = userDeviceRepository.save(device);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), device.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), device.getId());

        return new AuthResponse(accessToken, refreshToken, user.getId(), device.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        UserDeviceEntity device = userDeviceRepository.findByUserIdAndDeviceName(user.getId(), request.deviceName())
                .orElseGet(() -> UserDeviceEntity.builder()
                        .userId(user.getId())
                        .deviceName(request.deviceName())
                        .deviceType(request.deviceType())
                        .build());

        device.setActive(true);
        device.setLastActiveAt(Instant.now());
        device = userDeviceRepository.save(device);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), device.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), device.getId());

        return new AuthResponse(accessToken, refreshToken, user.getId(), device.getId());
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        UUID deviceId = jwtTokenProvider.getDeviceIdFromToken(request.refreshToken());

        UserDeviceEntity device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new UnauthorizedException("Device not found"));

        if (!device.isActive()) {
            throw new UnauthorizedException("Device session revoked");
        }

        device.setLastActiveAt(Instant.now());
        userDeviceRepository.save(device);

        String accessToken = jwtTokenProvider.generateAccessToken(userId, deviceId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, deviceId);

        return new AuthResponse(accessToken, refreshToken, userId, deviceId);
    }
}