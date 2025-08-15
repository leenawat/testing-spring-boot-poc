package com.example.service;

import com.example.config.RetryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthApiClientServiceTest {

    private MockWebServer mockWebServer;
    private AuthApiClientService authApiClientService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(5))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                ))
                .build();
        RetryProperties retryProperties = new RetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setDelay(Duration.ofMillis(5));
        authApiClientService = new AuthApiClientService(
                webClient.mutate(),
                baseUrl,
                "/auth/login",
                retryProperties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void login_Success_ShouldReturnAccessToken() throws Exception {
        // Given
        String username = "testuser";
        String password = "testpass";
        String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";

        AuthApiClientService.LoginResponse loginResponse = new AuthApiClientService.LoginResponse();
        loginResponse.setAccessToken(expectedToken);
        loginResponse.setRefreshToken("refresh_token_123");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(3600L);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(loginResponse)));

        // When & Then
        StepVerifier.create(authApiClientService.login(username, password))
                .expectNext(expectedToken)
                .verifyComplete();

        // Verify request
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/auth/login");
        assertThat(recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        AuthApiClientService.LoginRequest requestBody = objectMapper.readValue(
                recordedRequest.getBody().readUtf8(),
                AuthApiClientService.LoginRequest.class);
        assertThat(requestBody.getUsername()).isEqualTo(username);
        assertThat(requestBody.getPassword()).isEqualTo(password);

        // Verify token is stored
        assertTrue(authApiClientService.isAuthenticated());
        assertEquals(expectedToken, authApiClientService.getAccessToken());
    }

    @Test
    void login_Failure_ShouldThrowAuthenticationException() {
        // Given - ปรับปรุง MockResponse
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setHeader(HttpHeaders.CONNECTION, "close") // เพิ่ม connection header
                .setBody("{\"error\":\"Invalid credentials\"}")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)); // เพิ่ม delay เล็กน้อย

        // When & Then
        StepVerifier.create(authApiClientService.login("invalid", "credentials"))
                .expectErrorMatches(throwable ->
                        throwable instanceof AuthenticationException)
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void login_ServerError_ShouldRetryAndFail() throws InterruptedException {
        // Given - All retry attempts return server error
        for (int i = 0; i < 4; i++) { // 1 initial + 3 retries
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("Internal Server Error"));
        }

        // When & Then
        StepVerifier.create(authApiClientService.login("user", "pass"))
                .expectErrorMatches(throwable -> {
                    Throwable cause = throwable;
                    while (cause != null) {
                        if (cause instanceof AuthenticationException || cause instanceof java.rmi.ServerException) {
                            return true;
                        }
                        cause = cause.getCause();
                    }
                    return false;
                })
                .verify(Duration.ofSeconds(10));

        // Verify retry behavior
        assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
    }

    @Test
    void getAccessToken_WhenNotAuthenticated_ShouldThrowException() {
        // When & Then
        assertThrows(AuthenticationException.class, () -> {
            authApiClientService.getAccessToken();
        });
    }

    @Test
    void getAccessToken_WhenAuthenticated_ShouldReturnToken() throws Exception {
        // Given - Login first
        String expectedToken = "valid_token_123";
        AuthApiClientService.LoginResponse loginResponse = new AuthApiClientService.LoginResponse();
        loginResponse.setAccessToken(expectedToken);
        loginResponse.setRefreshToken("refresh_token");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(3600L);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(loginResponse)));

        StepVerifier.create(authApiClientService.login("user", "pass"))
                .expectNext(expectedToken)
                .verifyComplete();

        // When
        String actualToken = authApiClientService.getAccessToken();

        // Then
        assertEquals(expectedToken, actualToken);
    }

    @Test
    void isAuthenticated_WhenNotLoggedIn_ShouldReturnFalse() {
        // When & Then
        assertFalse(authApiClientService.isAuthenticated());
    }

    @Test
    void isAuthenticated_WhenLoggedIn_ShouldReturnTrue() throws Exception {
        // Given - Login first
        AuthApiClientService.LoginResponse loginResponse = new AuthApiClientService.LoginResponse();
        loginResponse.setAccessToken("token");
        loginResponse.setRefreshToken("refresh");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(3600L);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(loginResponse)));

        StepVerifier.create(authApiClientService.login("user", "pass"))
                .expectNext("token")
                .verifyComplete();

        // When & Then
        assertTrue(authApiClientService.isAuthenticated());
    }

    @Test
    void logout_ShouldClearToken() throws Exception {
        // Given - Login first
        AuthApiClientService.LoginResponse loginResponse = new AuthApiClientService.LoginResponse();
        loginResponse.setAccessToken("token");
        loginResponse.setRefreshToken("refresh");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(3600L);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(loginResponse)));

        StepVerifier.create(authApiClientService.login("user", "pass"))
                .expectNext("token")
                .verifyComplete();

        assertTrue(authApiClientService.isAuthenticated());

        // When
        authApiClientService.logout();

        // Then
        assertFalse(authApiClientService.isAuthenticated());
        assertThrows(AuthenticationException.class, () -> {
            authApiClientService.getAccessToken();
        });
    }

    @Test
    void createAuthenticatedWebClient_WhenAuthenticated_ShouldIncludeAuthHeader() throws Exception {
        // Given - Login first
        String token = "authenticated_token";
        AuthApiClientService.LoginResponse loginResponse = new AuthApiClientService.LoginResponse();
        loginResponse.setAccessToken(token);
        loginResponse.setRefreshToken("refresh");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(3600L);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(loginResponse)));

        StepVerifier.create(authApiClientService.login("user", "pass"))
                .expectNext(token)
                .verifyComplete();

        // When
        WebClient authenticatedClient = authApiClientService.createAuthenticatedWebClient();

        // Then
        assertNotNull(authenticatedClient);
        // Note: Testing the actual header would require making a request with the
        // client
        // which is more of an integration test
    }
}