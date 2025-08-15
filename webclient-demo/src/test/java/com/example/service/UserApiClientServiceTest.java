package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApiClientServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthApiClientService authService;

    @InjectMocks
    private UserApiClientService userApiClientService;

    private List<User> mockUsers;

    @BeforeEach
    void setUp() {
        mockUsers = Arrays.asList(
                new User(1L, "John Doe", "johndoe", "john@example.com"),
                new User(2L, "Jane Smith", "janesmith", "jane@example.com")
        );
    }

    @Test
    void testFetchUsersFromApi_Success() {
        // Given
        when(authService.getAccessToken()).thenReturn("test-access-token");
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/users")).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(User.class)).thenReturn(Flux.fromIterable(mockUsers));

        // When & Then
        StepVerifier.create(userApiClientService.fetchUsersFromApi())
                .expectNext(mockUsers.get(0))
                .expectNext(mockUsers.get(1))
                .verifyComplete();

        verify(authService).getAccessToken();
        verify(webClient).get();
        verify(requestHeadersUriSpec).uri("/users");
        verify(requestHeadersUriSpec).headers(any());
        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).bodyToFlux(User.class);
    }

    @Test
    void testSaveUsers_Success() {
        // Given
        when(userRepository.saveAll(anyList())).thenReturn(mockUsers);

        // When & Then
        StepVerifier.create(userApiClientService.saveUsers(mockUsers))
                .expectNextMatches(users -> {
                    assertThat(users).hasSize(2);
                    assertThat(users.get(0).getName()).isEqualTo("John Doe");
                    return true;
                })
                .verifyComplete();

        verify(userRepository).saveAll(mockUsers);
    }

    @Test
    void testGetAllUsers() {
        // Given
        when(userRepository.findAll()).thenReturn(mockUsers);

        // When
        List<User> result = userApiClientService.getAllUsers();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("John Doe");
        verify(userRepository).findAll();
    }

    @Test
    void testGetUserById_Found() {
        // Given
        User user = mockUsers.get(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // When
        User result = userApiClientService.getUserById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("John Doe");
        verify(userRepository).findById(1L);
    }

    @Test
    void testGetUserById_NotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        User result = userApiClientService.getUserById(999L);

        // Then
        assertThat(result).isNull();
        verify(userRepository).findById(999L);
    }
}