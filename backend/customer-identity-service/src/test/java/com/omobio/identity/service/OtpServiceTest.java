package com.omobio.identity.service;

import com.omobio.identity.domain.OtpCode;
import com.omobio.identity.repository.OtpRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private OtpRepository otpRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private WebClient.Builder webClientBuilder;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(otpRepository, redisTemplate, webClientBuilder);
        ReflectionTestUtils.setField(service, "otpTtlSeconds", 300);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "lockoutSeconds", 1800);
        ReflectionTestUtils.setField(service, "codeLength", 6);
        ReflectionTestUtils.setField(service, "notificationServiceUrl", "http://test:8090");

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- generateOtp ---

    @Test
    @DisplayName("generateOtp creates a new OTP and returns correlationId")
    void generateOtp_success() {
        when(otpRepository.expireActiveByTenantAndIdentifier(anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OtpService.OtpGenerateResult result = service.generateOtp(
                "dialog-lk", "+94771234567", "SMS");

        assertThat(result.correlationId()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(result.expiresAt()).isAfter(Instant.now().plusSeconds(200));
        verify(otpRepository).save(any(OtpCode.class));
    }

    @Test
    @DisplayName("generateOtp throws when identifier is in lockout")
    void generateOtp_lockedOut() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.generateOtp("dialog-lk", "+94771234567", "SMS"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many failed attempts");
    }

    @Test
    @DisplayName("generateOtp rejects invalid channel")
    void generateOtp_invalidChannel() {
        assertThatThrownBy(() -> service.generateOtp("dialog-lk", "+94771234567", "FOO"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported channel");
    }

    @Test
    @DisplayName("generateOtp uses TenantContext when tenantId arg is null")
    void generateOtp_fallsBackToTenantContext() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(otpRepository.expireActiveByTenantAndIdentifier(anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OtpService.OtpGenerateResult result = service.generateOtp(null, "+94771234567", "SMS");
        assertThat(result).isNotNull();
    }

    // --- verifyOtp ---

    @Test
    @DisplayName("verifyOtp with correct code marks USED and returns the record")
    void verifyOtp_success() {
        String code = "123456";
        String hash = hash(code);
        OtpCode otp = activeOtp(hash);
        when(otpRepository.findByCorrelationId(otp.getCorrelationId())).thenReturn(Optional.of(otp));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        OtpCode result = service.verifyOtp("dialog-lk", "+94771234567", code, otp.getCorrelationId());

        assertThat(result.getStatus()).isEqualTo(OtpCode.STATUS_USED);
        assertThat(result.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("verifyOtp with wrong code increments attempts")
    void verifyOtp_wrongCode() {
        OtpCode otp = activeOtp(hash("123456"));
        when(otpRepository.findByCorrelationId(otp.getCorrelationId())).thenReturn(Optional.of(otp));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verifyOtp("dialog-lk", "+94771234567", "999999", otp.getCorrelationId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid OTP");

        assertThat(otp.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("verifyOtp locks the OTP after 5 failed attempts")
    void verifyOtp_locksAfterMaxAttempts() {
        OtpCode otp = activeOtp(hash("123456"));
        otp.setAttemptCount(4); // 1 more failure = lockout
        when(otpRepository.findByCorrelationId(otp.getCorrelationId())).thenReturn(Optional.of(otp));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verifyOtp("dialog-lk", "+94771234567", "000000", otp.getCorrelationId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Too many failed attempts");

        assertThat(otp.getStatus()).isEqualTo(OtpCode.STATUS_LOCKED);
    }

    @Test
    @DisplayName("verifyOtp rejects expired OTP")
    void verifyOtp_expired() {
        OtpCode otp = activeOtp(hash("123456"));
        otp.setExpiresAt(Instant.now().minusSeconds(60));
        when(otpRepository.findByCorrelationId(otp.getCorrelationId())).thenReturn(Optional.of(otp));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.verifyOtp("dialog-lk", "+94771234567", "123456", otp.getCorrelationId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");

        assertThat(otp.getStatus()).isEqualTo(OtpCode.STATUS_EXPIRED);
    }

    @Test
    @DisplayName("verifyOtp with unknown correlationId throws")
    void verifyOtp_unknownCorrelationId() {
        when(otpRepository.findByCorrelationId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp("dialog-lk", "+94771234567", "123456", "unknown"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid correlation");
    }

    // --- helpers ---

    private OtpCode activeOtp(String codeHash) {
        return OtpCode.builder()
                .otpId("otp-1")
                .correlationId("corr-1")
                .tenantId("dialog-lk")
                .identifier("+94771234567")
                .codeHash(codeHash)
                .channel("SMS")
                .status(OtpCode.STATUS_ACTIVE)
                .attemptCount(0)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .build();
    }

    private String hash(String code) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(code.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
