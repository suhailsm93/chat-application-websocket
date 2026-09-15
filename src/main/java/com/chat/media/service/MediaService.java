package com.chat.media.service;

import com.chat.common.exception.BadRequestException;
import com.chat.media.dto.MediaDtos.UploadUrlRequest;
import com.chat.media.dto.MediaDtos.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MediaService {

    private final S3Presigner s3Presigner;

    @Value("${app.s3.bucket-name}")
    private String bucketName;

    @Value("${app.s3.endpoint}")
    private String endpoint;

    // Strict validation lists
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/mpeg", "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf", "application/zip", "text/plain"
    );

    private static final long MAX_FILE_SIZE = 104857600L; // 100 MB Limit

    public UploadUrlResponse generateUploadUrl(UUID userId, UploadUrlRequest request) {
        // 1. Validations
        if (request.fileSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds limit of 100MB");
        }
        if (!ALLOWED_MIME_TYPES.contains(request.contentType())) {
            throw new BadRequestException("Mime-Type is not supported: " + request.contentType());
        }

        // 2. Generate isolation key path
        String fileExtension = getFileExtension(request.fileName());
        String objectKey = String.format("uploads/%s/%s%s", userId, UUID.randomUUID(), fileExtension);

        // 3. Create pre-signed upload URL (S3 PUT)
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPut = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedPut.url().toString();
        
        // Static download endpoint matching pattern: endpoint/bucket/objectKey
        String downloadUrl = String.format("%s/%s/%s", endpoint, bucketName, objectKey);

        log.info("Generated S3 presigned URL for User: {} File: {}", userId, objectKey);

        return new UploadUrlResponse(uploadUrl, downloadUrl, objectKey);
    }

    private String getFileExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 ? "" : filename.substring(index);
    }
}