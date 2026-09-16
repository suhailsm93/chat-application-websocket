package com.chat.conversation.controller;

import com.chat.auth.security.JwtTokenProvider;
import com.chat.conversation.model.ConversationEntity;
import com.chat.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtTokenProvider jwtTokenProvider;

    public record CreateDirectRequest(UUID targetUserId) {}
    public record CreateGroupRequest(String title, List<UUID> memberIds) {}

    @PostMapping("/direct")
    public ResponseEntity<ConversationEntity> createDirectConversation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateDirectRequest request) {

        String token = authHeader.replace("Bearer ", "");
        UUID currentUserId = jwtTokenProvider.getUserIdFromToken(token);

        ConversationEntity conversation = conversationService.createDirectConversation(currentUserId, request.targetUserId());
        return ResponseEntity.ok(conversation);
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationEntity> createGroupConversation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateGroupRequest request) {

        String token = authHeader.replace("Bearer ", "");
        UUID currentUserId = jwtTokenProvider.getUserIdFromToken(token);

        ConversationEntity conversation = conversationService.createGroupConversation(currentUserId, request.title(), request.memberIds());
        return ResponseEntity.ok(conversation);
    }
}