package com.chat.message.controller;

import com.chat.auth.security.JwtTokenProvider;
import com.chat.message.dto.SyncDtos.SyncResponse;
import com.chat.message.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/sync")
    public ResponseEntity<SyncResponse> syncMessages(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID afterMessageId,
            @RequestParam(defaultValue = "50") int limit) {

        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        UUID deviceId = jwtTokenProvider.getDeviceIdFromToken(token);

        SyncResponse response = syncService.syncMissedMessages(userId, deviceId, afterMessageId, limit);
        return ResponseEntity.ok(response);
    }
}