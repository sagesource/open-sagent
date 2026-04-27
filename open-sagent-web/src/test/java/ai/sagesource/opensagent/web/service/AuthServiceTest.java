package ai.sagesource.opensagent.web.service;

import ai.sagesource.opensagent.web.entity.User;
import ai.sagesource.opensagent.web.repository.UserRepository;
import ai.sagesource.opensagent.web.security.JwtUtil;
import ai.sagesource.opensagent.web.security.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void testRegister() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = authService.register("test@example.com", "password123");

        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertEquals("test", user.getNickname());
    }

    @Test
    void testRegisterDuplicateEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                authService.register("test@example.com", "password123"));
    }

    @Test
    void testLoginSuccess() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "test@example.com")).thenReturn("token123");

        String token = authService.login("test@example.com", "password123");

        assertEquals("token123", token);
    }

    @Test
    void testLoginWrongPassword() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .passwordHash("hashed")
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                authService.login("test@example.com", "wrong"));
    }

    @Test
    void testUpdateProfile() {
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .nickname("old")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = authService.updateProfile(1L, "newNickname", null);

        assertEquals("newNickname", updated.getNickname());
    }
}
