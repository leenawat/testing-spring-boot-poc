package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class UserApiClientService {

  private static final Logger logger = LoggerFactory.getLogger(UserApiClientService.class);

  @Autowired
  private WebClient webClient;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AuthApiClientService authService;

  public Mono<List<User>> fetchAndSaveUsers() {
    return fetchUsersFromApi(authService.getAccessToken())
        .collectList()
        .flatMap(this::saveUsers);
  }

  public Flux<User> fetchUsersFromApi(String accessToken) {
    return webClient.get()
        .uri("/users")
        .headers(headers -> headers.setBearerAuth(accessToken))
        .retrieve()
        .bodyToFlux(User.class)
        .doOnNext(user -> logger.info("Fetched user: {}", user))
        .doOnError(error -> logger.error("Error fetching users: ", error));
  }

  // Keep the old method for backward compatibility (mainly for tests)
  public Flux<User> fetchUsersFromApi() {
    return fetchUsersFromApi(authService.getAccessToken());
  }

  public Mono<List<User>> saveUsers(List<User> users) {
    return Mono.fromCallable(() -> {
      List<User> savedUsers = userRepository.saveAll(users);
      logger.info("Saved {} users to database", savedUsers.size());
      return savedUsers;
    });
  }

  public List<User> getAllUsers() {
    return userRepository.findAll();
  }

  public User getUserById(Long id) {
    return userRepository.findById(id).orElse(null);
  }
}