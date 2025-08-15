package com.example.controller;

import com.example.entity.User;
import com.example.service.UserApiClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserApiClientService userApiClientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testFetchAndSaveUsers_Success() throws Exception {
        // Given
        List<User> users = Arrays.asList(
                new User(1L, "John Doe", "johndoe", "john@example.com"),
                new User(2L, "Jane Smith", "janesmith", "jane@example.com")
        );

        when(userApiClientService.fetchAndSaveUsers()).thenReturn(Mono.just(users));

        // When & Then - Test async processing is started and completes successfully
        mockMvc.perform(post("/api/users/fetch-and-save")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void testGetAllUsers_Success() throws Exception {
        // Given
        List<User> users = Arrays.asList(
                new User(1L, "John Doe", "johndoe", "john@example.com")
        );

        when(userApiClientService.getAllUsers()).thenReturn(users);

        // When & Then
        mockMvc.perform(get("/api/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("John Doe")));
    }

    @Test
    void testGetUserById_Found() throws Exception {
        // Given
        User user = new User(1L, "John Doe", "johndoe", "john@example.com");
        when(userApiClientService.getUserById(1L)).thenReturn(user);

        // When & Then
        mockMvc.perform(get("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.username", is("johndoe")));
    }

    @Test
    void testGetUserById_NotFound() throws Exception {
        // Given
        when(userApiClientService.getUserById(999L)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/users/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}