package com.chat.message.controller;

import com.chat.auth.security.JwtTokenProvider;
import com.chat.message.model.MessageEntity;
import com.chat.message.service.MessageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class MessageController {

    private final MessageQueryService queryService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageEntity>> getMessages(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int limit) {

        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);

        List<MessageEntity> messages = queryService.getConversationMessages(userId, conversationId, before, limit);
        return ResponseEntity.ok(messages);
    }
}