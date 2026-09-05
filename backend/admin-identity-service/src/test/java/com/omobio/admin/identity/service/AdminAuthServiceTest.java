package com.omobio.admin.identity.service;

import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminSessionRepository;
import com.omobio.admin.repository.AdminUserRepository;
import com.omobio.admin.security.AdminJwtIssuer;
import com.omobio.admin.security.AdminPasswordEncoder;
import com.omobio.admin.service.AdminAuthService;
import com.omobio.admin.service.AdminSessionService;
import com.omobio.platform.common.web.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AdminAuthService}.
 *
 * Verifies:
 * - Password validation
 * - Account lockout after max failed attempts
 * - Successful login with MFA pending
 * - Invalid credentials rejection
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private AdminUserRepository userRepository;
    @Mock private AdminSessionRepository sessionRepository;
    @Mock private AdminPasswordEncoder passwordEncoder;
    @Mock private AdminJwtIssuer jwtIssuer;
    @Mock private AdminSessionService sessionService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AdminAuthService(
                userRepository, sessionRepository,
                passwordEncoder, jwtIssuer, sessionService, redisTemplate);
    }

    @Test
    @DisplayName("Login succeeds with correct email and password")
    void login_success() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .passwordHash("hashedpw")
                .fullName("Admin User")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .mfaEnabled(false)
                .failedLoginCount(0)
                .build();

        when(userRepository.findByEmail("admin@omobio.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedpw")).thenReturn(true);
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtIssuer.issueAccessToken(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtIssuer.issueRefreshToken(anyString(), any(), anyString()))
                .thenReturn("refresh-token");

        AdminAuthService.LoginResult result = service.login(
                "admin@omobio.com", "password123", null, "192.168.1.1");

        assertThat(result.getEmail()).isEqualTo("admin@omobio.com");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.isMfaPending()).isFalse();
    }

    @Test
    @DisplayName("Login throws UnauthorizedException for unknown user")
    void login_unknownUser() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "pw", null, "1.1.1.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("Login throws UnauthorizedException for wrong password")
    void login_wrongPassword() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .passwordHash("hashedpw")
                .status("ACTIVE")
                .failedLoginCount(0)
                .build();

        when(userRepository.findByEmail("admin@omobio.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpw", "hashedpw")).thenReturn(false);
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.login("admin@omobio.com", "wrongpw", null, "1.1.1.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("Account is locked after MAX_FAILED_ATTEMPTS")
    void login_accountLocked() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .passwordHash("hashedpw")
                .status("ACTIVE")
                .failedLoginCount(4) // one more = 5 (lockout threshold)
                .build();

        when(userRepository.findByEmail("admin@omobio.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpw", "hashedpw")).thenReturn(false);
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.login("admin@omobio.com", "wrongpw", null, "1.1.1.1"))
                .isInstanceOf(UnauthorizedException.class);

        // Verify lockout was set
        verify(userRepository, times(2)).save(argThat(u ->
                u.getStatus().equals("LOCKED") && u.getFailedLoginCount() == 5
        ));
    }

    @Test
    @DisplayName("Login throws UnauthorizedException for locked account")
    void login_lockedAccount() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .status("LOCKED")
                .lockedUntil(java.time.Instant.now().plusSeconds(300))
                .build();

        when(userRepository.findByEmail("admin@omobio.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("admin@omobio.com", "pw", null, "1.1.1.1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("Login returns mfaPending = true when MFA is enabled but not yet verified")
    void login_mfaPending() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .passwordHash("hashedpw")
                .fullName("Admin User")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .mfaEnabled(true)
                .mfaType("TOTP")
                .mfaSecret("encrypted-secret")
                .failedLoginCount(0)
                .build();

        when(userRepository.findByEmail("admin@omobio.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedpw")).thenReturn(true);
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // MFA is pending — no tokens issued yet
        AdminAuthService.LoginResult result = service.login(
                "admin@omobio.com", "password123", null, "192.168.1.1");

        assertThat(result.isMfaPending()).isTrue();
        assertThat(result.getAccessToken()).isNull();
        assertThat(result.getRefreshToken()).isNull();
        // Tokens should NOT be issued when MFA is pending
        verify(jwtIssuer, never()).issueAccessToken(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("changePassword verifies old password before updating")
    void changePassword_verifiesOld() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .passwordHash("old-hash")
                .build();

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-old", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("user-1", "wrong-old", "newpass123"))
                .isInstanceOf(com.omobio.platform.common.web.BadRequestException.class)
                .hasMessageContaining("incorrect");
    }
}
