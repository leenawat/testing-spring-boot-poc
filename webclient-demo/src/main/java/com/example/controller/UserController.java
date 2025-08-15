package com.example.controller;

import com.example.entity.User;
import com.example.service.UserApiClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

  @Autowired
  private UserApiClientService userApiClientService;

  @PostMapping("/fetch-and-save")
  public Mono<ResponseEntity<List<User>>> fetchAndSaveUsers() {
    return userApiClientService.fetchAndSaveUsers()
        .map(users -> ResponseEntity.ok(users))
        .onErrorReturn(ResponseEntity.internalServerError().build());
  }

  @GetMapping
  public ResponseEntity<List<User>> getAllUsers() {
    List<User> users = userApiClientService.getAllUsers();
    return ResponseEntity.ok(users);
  }

  @GetMapping("/{id}")
  public ResponseEntity<User> getUserById(@PathVariable Long id) {
    User user = userApiClientService.getUserById(id);
    if (user != null) {
      return ResponseEntity.ok(user);
    }
    return ResponseEntity.notFound().build();
  }
}