package com.chat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank String displayName,
            @NotBlank String deviceName,
            @NotBlank String deviceType
    ) {}

    public record LoginRequest(
            @NotBlank String usernameOrEmail,
            @NotBlank String password,
            @NotBlank String deviceName,
            @NotBlank String deviceType
    ) {}

    public record RefreshTokenRequest(
            @NotBlank String refreshToken
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            UUID userId,
            UUID deviceId
    ) {}
}