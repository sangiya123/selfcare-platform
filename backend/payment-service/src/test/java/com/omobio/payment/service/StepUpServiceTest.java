package com.omobio.payment.service;

import com.omobio.payment.domain.StepUpRequest;
import com.omobio.payment.domain.StepUpVerification;
import com.omobio.payment.repository.StepUpRequestRepository;
import com.omobio.payment.repository.StepUpVerificationRepository;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpServiceTest {

    @Mock private StepUpRequestRepository requestRepository;
    @Mock private StepUpVerificationRepository verificationRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private StepUpService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new StepUpService(requestRepository, verificationRepository, redisTemplate);
    }

    // --- isStepUpRequired ---

    @Test
    @DisplayName("Cross-connection payment requires step-up")
    void isStepUpRequired_crossConnection() {
        assertThat(service.isStepUpRequired("t1", "PAY",
            BigDecimal.ONE, "conn-A", "conn-B")).isTrue();
    }

    @Test
    @DisplayName("Same-connection payment below threshold does NOT require step-up")
    void isStepUpRequired_sameConnectionBelowThreshold() {
        assertThat(service.isStepUpRequired("t1", "PAY",
            BigDecimal.valueOf(5000), "conn-A", "conn-A")).isFalse();
    }

    @Test
    @DisplayName("High-value payment requires step-up even on same connection")
    void isStepUpRequired_highValue() {
        assertThat(service.isStepUpRequired("t1", "PAY",
            BigDecimal.valueOf(15000), "conn-A", "conn-A")).isTrue();
    }

    // --- initiate ---

    @Test
    @DisplayName("Initiate creates request and stores in Redis")
    void initiate_createsRequestAndStoresInRedis() {
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StepUpService.StepUpInitiation result = service.initiate(
            "t1", "u1", "PAY", BigDecimal.valueOf(500), "idem-key");

        assertThat(result.correlationId()).startsWith("stepup-");
        assertThat(result.expiresIn()).isEqualTo(300L);

        verify(requestRepository).save(any(StepUpRequest.class));
        verify(valueOps, times(2)).set(anyString(), any(), any(Duration.class));
    }

    // --- verify with correct code ---

    @Test
    @DisplayName("Verify with correct code returns token and marks VERIFIED")
    void verify_correctCode_returnsToken() {
        String code = "123456";
        String hash = hash(code);
        StepUpRequest request = pendingRequest(hash);
        when(requestRepository.findByCorrelationId("stepup-abc")).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StepUpService.StepUpToken token = service.verify("stepup-abc", code);

        assertThat(token.token()).startsWith("su-");
        assertThat(token.expiresIn()).isEqualTo(300L);
        assertThat(token.action()).isEqualTo("PAY");

        // Status should be updated to VERIFIED
        ArgumentCaptor<StepUpRequest> captor = ArgumentCaptor.forClass(StepUpRequest.class);
        verify(requestRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("VERIFIED");

        // Verification audit should be recorded
        verify(verificationRepository).save(any(StepUpVerification.class));
    }

    // --- verify with wrong code ---

    @Test
    @DisplayName("Verify with wrong code increments attempt count")
    void verify_wrongCode_incrementsAttempts() {
        String hash = hash("123456");
        StepUpRequest request = pendingRequest(hash);
        when(requestRepository.findByCorrelationId("stepup-abc")).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verify("stepup-abc", "000000"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Invalid step-up code");

        assertThat(request.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify with wrong code 5 times locks the request")
    void verify_maxAttempts_locksRequest() {
        String hash = hash("123456");
        StepUpRequest request = pendingRequest(hash);
        request.setAttemptCount(4); // already 4 failed
        when(requestRepository.findByCorrelationId("stepup-abc")).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verify("stepup-abc", "000000"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Try again in 30 minutes");

        assertThat(request.getStatus()).isEqualTo("LOCKED");
        verify(valueOps).set(contains("locked"), eq("1"), any(Duration.class));
    }

    // --- expired request ---

    @Test
    @DisplayName("Verify on expired request throws BadRequestException")
    void verify_expiredRequest_throwsBadRequest() {
        StepUpRequest request = pendingRequest(hash("123456"));
        request.setExpiresAt(Instant.now().minusSeconds(60));
        when(requestRepository.findByCorrelationId("stepup-abc")).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verify("stepup-abc", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("expired");
    }

    // --- already locked ---

    @Test
    @DisplayName("Verify on already-locked request throws UnauthorizedException")
    void verify_lockedRequest_throwsUnauthorized() {
        StepUpRequest request = pendingRequest(hash("123456"));
        request.setStatus("LOCKED");
        when(requestRepository.findByCorrelationId("stepup-abc")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.verify("stepup-abc", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("no longer valid");
    }

    // --- token validation ---

    @Test
    @DisplayName("validateStepUpToken returns false for null/empty token")
    void validateStepUpToken_nullOrEmpty_returnsFalse() {
        assertThat(service.validateStepUpToken(null, "PAY", BigDecimal.TEN)).isFalse();
        assertThat(service.validateStepUpToken("", "PAY", BigDecimal.TEN)).isFalse();
    }

    @Test
    @DisplayName("validateStepUpToken returns false for unknown token")
    void validateStepUpToken_unknownToken_returnsFalse() {
        when(valueOps.get("omobio:stepup:token:su-unknown")).thenReturn(null);
        assertThat(service.validateStepUpToken("su-unknown", "PAY", BigDecimal.TEN)).isFalse();
    }

    // --- consume ---

    @Test
    @DisplayName("consumeStepUpToken deletes the Redis key")
    void consumeStepUpToken_deletesRedisKey() {
        service.consumeStepUpToken("su-abc-123");
        verify(redisTemplate).delete("omobio:stepup:token:su-abc-123");
    }

    @Test
    @DisplayName("consumeStepUpToken is safe with null")
    void consumeStepUpToken_nullSafe() {
        service.consumeStepUpToken(null);
        verify(redisTemplate, never()).delete(anyString());
    }

    // --- helpers ---

    private StepUpRequest pendingRequest(String codeHash) {
        StepUpRequest r = new StepUpRequest();
        r.setCorrelationId("stepup-abc");
        r.setTenantId("t1");
        r.setUserId("u1");
        r.setAction("PAY");
        r.setAmount(BigDecimal.valueOf(500));
        r.setCodeHash(codeHash);
        r.setStatus("PENDING");
        r.setAttemptCount(0);
        r.setExpiresAt(Instant.now().plusSeconds(300));
        r.setCreatedAt(Instant.now());
        return r;
    }

    private String hash(String code) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(b);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
