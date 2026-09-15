package com.chat.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class MediaDtos {

    public record UploadUrlRequest(
            @NotBlank String fileName,
            @NotBlank String contentType,
            @Min(1) long fileSize
    ) {}

    public record UploadUrlResponse(
            String uploadUrl,
            String downloadUrl,
            String objectKey
    ) {}
}