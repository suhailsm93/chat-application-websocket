package com.chat.auth;

import com.chat.auth.dto.AuthDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("chatdb")
            .withUsername("chatuser")
            .withPassword("chatpassword");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testRegisterAndLoginUser() {
        RegisterRequest registerReq = new RegisterRequest(
                "alice", "alice@example.com", "password123", "Alice Smith", "iPhone 15", "MOBILE"
        );

        ResponseEntity<AuthResponse> registerRes = restTemplate.postForEntity("/api/auth/register", registerReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, registerRes.getStatusCode());
        assertNotNull(registerRes.getBody());
        assertNotNull(registerRes.getBody().accessToken());

        LoginRequest loginReq = new LoginRequest("alice", "password123", "iPhone 15", "MOBILE");
        ResponseEntity<AuthResponse> loginRes = restTemplate.postForEntity("/api/auth/login", loginReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        assertNotNull(loginRes.getBody().accessToken());
    }
}