package com.chat.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.List;

@Configuration
@Slf4j
public class S3Config {

    @Value("${app.s3.endpoint}")
    private String endpoint;

    @Value("${app.s3.region}")
    private String region;

    @Value("${app.s3.access-key}")
    private String accessKey;

    @Value("${app.s3.secret-key}")
    private String secretKey;

    @Value("${app.s3.bucket-name}")
    private String bucketName;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // Required for MinIO
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build())        
                .build();
    }

    // Auto-create bucket & configure Browser CORS on application startup
    @PostConstruct
    public void initializeBucket() {
        try {
            S3Client client = s3Client();
            
            // 1. Create Bucket if not exists
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                log.info("S3 Bucket '{}' already exists.", bucketName);
            } catch (NoSuchBucketException e) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Successfully created S3 Bucket '{}'.", bucketName);
            }

            // 2. Configure Permissive CORS Rules for direct browser uploads
            CORSRule corsRule = CORSRule.builder()
                    .allowedOrigins(List.of("*")) // Allow React on 5000/3000 to upload
                    .allowedMethods(List.of("PUT", "GET", "POST", "HEAD", "DELETE"))
                    .allowedHeaders(List.of("*"))
                    .maxAgeSeconds(3000)
                    .build();

            CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                    .corsRules(corsRule)
                    .build();

            client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .corsConfiguration(corsConfiguration)
                    .build());

            log.info("Applied permissive CORS policy on S3 Bucket '{}'", bucketName);
        } catch (Exception e) {
            log.error("Failed to auto-configure S3 bucket: ", e);
        }
    }
}