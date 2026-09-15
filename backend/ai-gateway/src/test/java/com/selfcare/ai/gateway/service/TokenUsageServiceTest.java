package com.selfcare.ai.gateway.service;

import com.selfcare.ai.service.AIResponse;
import com.selfcare.ai.service.TokenUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenUsageServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    private TokenUsageService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new TokenUsageService(redisTemplate);
    }

    @Test
    @DisplayName("first request within rate limit")
    void firstRequestAllowed() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        boolean allowed = service.checkTenantRateLimit("dialog-lk");

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("rejects when tenant rate limit exceeded")
    void tenantLimitExceeded() {
        when(valueOps.increment(anyString())).thenReturn(61L);

        boolean allowed = service.checkTenantRateLimit("dialog-lk");

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("rejects when user rate limit exceeded")
    void userLimitExceeded() {
        when(valueOps.increment(anyString())).thenReturn(21L);

        boolean allowed = service.checkUserRateLimit("dialog-lk", "user-1");

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("records usage without throwing")
    void recordsUsage() {
        AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .build();

        service.recordUsage("dialog-lk", "user-1", "claude-sonnet-4-5", usage);

        verify(redisTemplate.opsForHash(), atLeastOnce())
                .increment(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("estimates Claude cost correctly")
    void claudeCostEstimation() {
        AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                .promptTokens(1_000_000) // 1M tokens
                .completionTokens(1_000_000) // 1M tokens
                .totalTokens(2_000_000)
                .build();

        BigDecimal cost = service.estimateCost("claude-sonnet-4-5", usage);

        // $3.00 input + $15.00 output = $18.00
        assertThat(cost).isEqualByComparingTo(new BigDecimal("18.000000"));
    }

    @Test
    @DisplayName("estimates OpenAI cost correctly")
    void openaiCostEstimation() {
        AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                .promptTokens(1_000_000)
                .completionTokens(1_000_000)
                .totalTokens(2_000_000)
                .build();

        BigDecimal cost = service.estimateCost("gpt-4o-mini", usage);

        // $0.15 input + $0.60 output = $0.75
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.750000"));
    }

    @Test
    @DisplayName("returns zero cost for null usage")
    void nullUsage() {
        BigDecimal cost = service.estimateCost("claude-sonnet-4-5", null);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("recordUsage does nothing for null usage")
    void nullUsageRecord() {
        service.recordUsage("dialog-lk", "user-1", "claude-sonnet-4-5", null);
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    @DisplayName("records rate limit violation")
    void recordViolation() {
        service.recordRateLimitViolation("dialog-lk", "user-1", "user_rpm");

        verify(redisTemplate.opsForHash()).increment(anyString(), eq("user_rpm:user-1"), eq(1L));
    }
}
