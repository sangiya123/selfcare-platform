package com.omobio.identity.service;

import com.omobio.identity.domain.Session;
import com.omobio.identity.repository.SessionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerSessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private CustomerSessionService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new CustomerSessionService(sessionRepository, redisTemplate);
        ReflectionTestUtils.setField(service, "refreshTtlSeconds", 2_592_000L);
        ReflectionTestUtils.setField(service, "inactivityTimeoutSeconds", 1800L);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("u-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- createSession ---

    @Test
    @DisplayName("createSession returns ACTIVE session with 30-day expiry")
    void createSession() {
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session session = service.createSession("u-1", "dev-1", "iPhone 15", "10.0.0.1");

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getTokenFamilyId()).isNotBlank();
        assertThat(session.getStatus()).isEqualTo("ACTIVE");
        assertThat(session.getExpiresAt()).isAfter(Instant.now().plusSeconds(2_500_000L));
        verify(valueOps).set(anyString(), eq("u-1"), any());
    }

    // --- rotateRefreshToken ---

    @Test
    @DisplayName("rotateRefreshToken shifts current → previous and accepts the new one")
    void rotateRefreshToken_success() {
        Session session = activeSession("sess-1");
        session.setCurrentTokenHash("hash-current");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session updated = service.rotateRefreshToken(
                "sess-1",
                "new-refresh-token",
                "previous-refresh-token");

        // After rotation, what was 'current' is now 'previous'
        assertThat(updated.getPreviousTokenHash()).isEqualTo("hash-current");
        // And the new token becomes current
        assertThat(updated.getCurrentTokenHash()).isNotEqualTo("hash-current");
    }

    @Test
    @DisplayName("rotateRefreshToken detects replay: same previous token used twice → family revoked")
    void rotateRefreshToken_replayDetected() {
        Session session = activeSession("sess-1");
        session.setCurrentTokenHash("hash-current");
        session.setPreviousTokenHash("hash-prev"); // Same as the one we'll send
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.findByTokenFamilyId(session.getTokenFamilyId())).thenReturn(List.of(session));

        // Send "previous-refresh-token" whose hash equals hash-prev
        assertThatThrownBy(() -> service.rotateRefreshToken(
                "sess-1",
                "new-refresh-token",
                "previous-refresh-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("replay");

        // Session should be revoked
        assertThat(session.getStatus()).isEqualTo("REVOKED");
        assertThat(session.getRevokedReason()).isEqualTo("REPLAY_DETECTED");
    }

    @Test
    @DisplayName("rotateRefreshToken throws on expired session")
    void rotateRefreshToken_expiredSession() {
        Session session = activeSession("sess-1");
        session.setExpiresAt(Instant.now().minusSeconds(10));
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.rotateRefreshToken(
                "sess-1", "new", "prev"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");

        assertThat(session.getStatus()).isEqualTo("EXPIRED");
    }

    // --- revokeSession ---

    @Test
    @DisplayName("revokeSession sets status REVOKED with reason")
    void revokeSession() {
        Session session = activeSession("sess-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeSession("sess-1", "USER_LOGOUT");

        assertThat(session.getStatus()).isEqualTo("REVOKED");
        assertThat(session.getRevokedReason()).isEqualTo("USER_LOGOUT");
        verify(redisTemplate).delete(anyString());
    }

    // --- getActiveSessions ---

    @Test
    @DisplayName("getActiveSessions returns only ACTIVE sessions for user")
    void getActiveSessions() {
        Session s1 = activeSession("s1");
        Session s2 = activeSession("s2");
        when(sessionRepository.findByUserIdAndStatus("u-1", "ACTIVE"))
                .thenReturn(List.of(s1, s2));

        List<Session> active = service.getActiveSessions("u-1");
        assertThat(active).hasSize(2);
    }

    // --- helpers ---

    private Session activeSession(String id) {
        return Session.builder()
                .sessionId(id)
                .tenantId("dialog-lk")
                .userId("u-1")
                .tokenFamilyId("fam-" + id)
                .deviceId("dev-1")
                .status("ACTIVE")
                .issuedAt(Instant.now().minusSeconds(60))
                .lastUsedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(2_500_000L))
                .build();
    }
}
