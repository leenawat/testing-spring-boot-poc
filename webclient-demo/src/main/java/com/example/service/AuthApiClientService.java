package com.example.service;

import com.example.config.RetryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.rmi.ServerException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
@Service
public class AuthApiClientService {

  private final WebClient webClient;
  private final String loginEndpoint;
  private final AtomicReference<TokenInfo> currentToken = new AtomicReference<>();
  private final RetryProperties retryProperties;

  public AuthApiClientService(WebClient.Builder webClientBuilder,
                              @Value("${auth.base-url}") String baseUrl,
                              @Value("${auth.login-endpoint:/auth/login}") String loginEndpoint,
                              RetryProperties retryProperties
  ) {
    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
    this.loginEndpoint = loginEndpoint;
    this.retryProperties = retryProperties;
  }

  /**
   * Login with username and password
   */
  public Mono<String> login(String username, String password) {
    LoginRequest loginRequest = new LoginRequest(username, password);

    return webClient.post()
            .uri(loginEndpoint)
            .bodyValue(loginRequest)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response -> {
              // ✅ ถูกต้อง - return Mono.error() โดยตรง
              return response.bodyToMono(String.class)
                      .map(errorBody -> new AuthenticationException("Authentication failed: " + errorBody))
                      .cast(Throwable.class) // Cast เป็น Throwable
                      .flatMap(Mono::error); // แล้วค่อย error
            })
            .onStatus(HttpStatusCode::is5xxServerError, response -> {
              // ✅ ถูกต้อง - return Mono.error() โดยตรง
              return Mono.error(new ServerException("Server error"));
            })
            .bodyToMono(LoginResponse.class)
            .retryWhen(Retry.backoff(retryProperties.getMaxAttempts(), retryProperties.getDelay())
                    .filter(throwable -> {
                      return !(throwable instanceof AuthenticationException) &&
                              !(throwable instanceof WebClientResponseException.Unauthorized);
                    }))
            .doOnSuccess(this::storeToken)
            .map(LoginResponse::getAccessToken);
  }

  /**
   * Get current access token
   */
  public String getAccessToken() {
    TokenInfo token = currentToken.get();
    if (token == null || token.isExpired()) {
      throw new AuthenticationException("No valid access token available. Please login first.");
    }
    return token.getAccessToken();
  }

  /**
   * Check if currently authenticated
   */
  public boolean isAuthenticated() {
    TokenInfo token = currentToken.get();
    return token != null && !token.isExpired();
  }

  /**
   * Logout - clear stored token
   */
  public void logout() {
    currentToken.set(null);
  }

  /**
   * Create WebClient with authentication header
   */
  public WebClient createAuthenticatedWebClient() {
    return webClient.mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
        .build();
  }

  private void storeToken(LoginResponse response) {
    TokenInfo tokenInfo = new TokenInfo(
        response.getAccessToken(),
        response.getRefreshToken(),
        LocalDateTime.now().plusSeconds(response.getExpiresIn()));
    currentToken.set(tokenInfo);
  }

  // DTOs
  public static class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
      this.username = username;
      this.password = password;
    }

    // Getters and Setters
    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;

    public LoginResponse() {
    }

    // Getters and Setters
    public String getAccessToken() {
      return accessToken;
    }

    public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
    }

    public String getTokenType() {
      return tokenType;
    }

    public void setTokenType(String tokenType) {
      this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
      return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
      this.expiresIn = expiresIn;
    }
  }

  // Token Info class
  private static class TokenInfo {
    private final String accessToken;
    private final String refreshToken;
    private final LocalDateTime expiresAt;

    public TokenInfo(String accessToken, String refreshToken, LocalDateTime expiresAt) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      this.expiresAt = expiresAt;
    }

    public String getAccessToken() {
      return accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public boolean isExpired() {
      return LocalDateTime.now().isAfter(expiresAt.minusMinutes(5)); // 5 minutes buffer
    }
  }
}