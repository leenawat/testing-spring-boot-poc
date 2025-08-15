package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Configuration
public class WebClientConfig {

  @Value("${external.api.base-url:https://jsonplaceholder.typicode.com}")
  private String baseUrl;

  @Bean
  public WebClient webClient() {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .build();
  }

  @Bean
  public Retry retryConfig(RetryProperties properties) {
    return Retry.backoff(properties.getMaxAttempts(), properties.getDelay());
  }
}