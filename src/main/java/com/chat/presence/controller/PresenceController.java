package com.chat.presence.controller;

import com.chat.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/{userId}/presence")
    public ResponseEntity<Map<String, Object>> getUserPresence(@PathVariable UUID userId) {
        boolean isOnline = presenceService.isUserOnline(userId);
        Instant lastSeen = presenceService.getLastSeen(userId).orElse(null);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "isOnline", isOnline,
                "lastSeen", lastSeen != null ? lastSeen.toString() : "UNKNOWN"
        ));
    }
}