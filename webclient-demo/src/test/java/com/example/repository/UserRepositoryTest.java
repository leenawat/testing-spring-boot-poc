package com.example.repository;

import com.example.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindUser() {
        // Given
        User user = new User(1L, "John Doe", "johndoe", "john@example.com");

        // When
        User savedUser = userRepository.save(user);

        // Then
        assertThat(savedUser.getId()).isEqualTo(1L);
        assertThat(savedUser.getName()).isEqualTo("John Doe");

        Optional<User> foundUser = userRepository.findById(1L);
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void testFindByUsername() {
        // Given
        User user = new User(1L, "John Doe", "johndoe", "john@example.com");
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByUsername("johndoe");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("John Doe");
    }

    @Test
    void testExistsByUsername() {
        // Given
        User user = new User(1L, "John Doe", "johndoe", "john@example.com");
        userRepository.save(user);

        // When & Then
        assertThat(userRepository.existsByUsername("johndoe")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void testExistsByEmail() {
        // Given
        User user = new User(1L, "John Doe", "johndoe", "john@example.com");
        userRepository.save(user);

        // When & Then
        assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }
}