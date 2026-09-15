package com.chat.media.controller;

import com.chat.auth.security.JwtTokenProvider;
import com.chat.media.dto.MediaDtos.UploadUrlRequest;
import com.chat.media.dto.MediaDtos.UploadUrlResponse;
import com.chat.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> getUploadUrl(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UploadUrlRequest request) {

        String token = authHeader.replace("Bearer ", "");
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);

        UploadUrlResponse response = mediaService.generateUploadUrl(userId, request);
        return ResponseEntity.ok(response);
    }
}