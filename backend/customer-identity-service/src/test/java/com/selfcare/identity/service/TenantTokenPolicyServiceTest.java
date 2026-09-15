package com.selfcare.identity.service;

import com.selfcare.identity.domain.TenantTokenPolicy;
import com.selfcare.identity.repository.TenantTokenPolicyRepository;
import com.selfcare.platform.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TenantTokenPolicyService}.
 *
 * Verifies ADR-011 enforcement:
 * - default policy returned when no record exists
 * - per-tenant record returned when present
 * - hard caps enforced (max 42d access, 7mo refresh)
 * - compensating controls required when TTL exceeds defaults
 */
@ExtendWith(MockitoExtension.class)
class TenantTokenPolicyServiceTest {

    @Mock
    private TenantTokenPolicyRepository repository;

    private TenantTokenPolicyService service;

    @BeforeEach
    void setUp() {
        service = new TenantTokenPolicyService(repository);
    }

    @Test
    @DisplayName("Returns default policy when no record exists for tenant")
    void returnsDefaultWhenMissing() {
        when(repository.findById("tenant-token-policy:dialog-lk")).thenReturn(Optional.empty());

        TenantTokenPolicy policy = service.getEffectivePolicy("dialog-lk");

        assertThat(policy.getAccessTokenSeconds()).isEqualTo(TenantTokenPolicy.DEFAULT_ACCESS_TOKEN_SECONDS);
        assertThat(policy.getRefreshTokenSeconds()).isEqualTo(TenantTokenPolicy.DEFAULT_REFRESH_TOKEN_SECONDS);
        assertThat(policy.isDeviceBindingRequired()).isTrue();
    }

    @Test
    @DisplayName("Returns stored policy when present")
    void returnsStoredPolicy() {
        TenantTokenPolicy stored = TenantTokenPolicy.builder()
                .id("tenant-token-policy:aia-lk")
                .tenantId("aia-lk")
                .accessTokenSeconds(86400)
                .refreshTokenSeconds(2592000)
                .deviceBindingRequired(true)
                .build();
        when(repository.findById("tenant-token-policy:aia-lk")).thenReturn(Optional.of(stored));

        TenantTokenPolicy policy = service.getEffectivePolicy("aia-lk");

        assertThat(policy.getAccessTokenSeconds()).isEqualTo(86400);
        assertThat(policy.getRefreshTokenSeconds()).isEqualTo(2592000);
    }

    @Test
    @DisplayName("Allows upsert with default TTL (24h/30d)")
    void allowsDefaultUpsert() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("dialog-lk")
                .accessTokenSeconds(86400)
                .refreshTokenSeconds(2592000)
                .deviceBindingRequired(true)
                .rotationEnforced(true)
                .build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantTokenPolicy saved = service.upsert(input);

        assertThat(saved.getId()).isEqualTo("tenant-token-policy:dialog-lk");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Allows long-lived (42d/7mo) with justification and controls")
    void allowsLongLivedWithJustification() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("long-lived-insurer")
                .accessTokenSeconds(TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS) // 42d
                .refreshTokenSeconds(TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS) // 7mo
                .deviceBindingRequired(true)
                .rotationEnforced(true)
                .justification("Long-lived policy clients expect infrequent re-auth")
                .build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantTokenPolicy saved = service.upsert(input);

        assertThat(saved.getAccessTokenSeconds()).isEqualTo(TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS);
        assertThat(saved.getRefreshTokenSeconds()).isEqualTo(TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS);
    }

    @Test
    @DisplayName("Rejects access TTL above 42d platform maximum")
    void rejectsAccessTooLong() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("greedy")
                .accessTokenSeconds(TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS + 1) // 42d + 1s
                .refreshTokenSeconds(2592000)
                .build();

        assertThatThrownBy(() -> service.upsert(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("accessTokenSeconds");
    }

    @Test
    @DisplayName("Rejects refresh TTL above 7mo platform maximum")
    void rejectsRefreshTooLong() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("greedy")
                .accessTokenSeconds(86400)
                .refreshTokenSeconds(TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS + 1)
                .build();

        assertThatThrownBy(() -> service.upsert(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("refreshTokenSeconds");
    }

    @Test
    @DisplayName("Rejects long TTL without justification")
    void rejectsLongTtlWithoutJustification() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("greedy")
                .accessTokenSeconds(2 * 86400) // 48h, > 24h default
                .refreshTokenSeconds(2592000)
                .deviceBindingRequired(true)
                .justification(null)
                .build();

        assertThatThrownBy(() -> service.upsert(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("justification");
    }

    @Test
    @DisplayName("Rejects long TTL without device binding")
    void rejectsLongTtlWithoutDeviceBinding() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId("insecure")
                .accessTokenSeconds(2 * 86400)
                .refreshTokenSeconds(2592000)
                .deviceBindingRequired(false)
                .justification("I want long tokens")
                .build();

        assertThatThrownBy(() -> service.upsert(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("deviceBindingRequired");
    }

    @Test
    @DisplayName("Rejects upsert with null tenantId")
    void rejectsNullTenantId() {
        TenantTokenPolicy input = TenantTokenPolicy.builder()
                .tenantId(null)
                .accessTokenSeconds(86400)
                .refreshTokenSeconds(2592000)
                .build();

        assertThatThrownBy(() -> service.upsert(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Effective TTL is capped at platform maximum even if DB has higher value")
    void effectiveTtlIsCapped() {
        // Simulate a DB record that was edited directly to exceed caps
        TenantTokenPolicy stored = TenantTokenPolicy.builder()
                .id("tenant-token-policy:bad-record")
                .tenantId("bad-record")
                .accessTokenSeconds(TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS * 2) // doubled
                .refreshTokenSeconds(TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS * 2)
                .build();
        when(repository.findById("tenant-token-policy:bad-record")).thenReturn(Optional.of(stored));

        TenantTokenPolicy policy = service.getEffectivePolicy("bad-record");

        assertThat(policy.effectiveAccessTokenSeconds()).isEqualTo(TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS);
        assertThat(policy.effectiveRefreshTokenSeconds()).isEqualTo(TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS);
    }

    @Test
    @DisplayName("Default policy has expected values")
    void defaultPolicyValues() {
        TenantTokenPolicy defaults = TenantTokenPolicy.defaults("dialog-lk");
        assertThat(defaults.getId()).isEqualTo("tenant-token-policy:dialog-lk");
        assertThat(defaults.getAccessTokenSeconds()).isEqualTo(86_400L);
        assertThat(defaults.getRefreshTokenSeconds()).isEqualTo(2_592_000L);
        assertThat(defaults.isDeviceBindingRequired()).isTrue();
        assertThat(defaults.isRotationEnforced()).isTrue();
        assertThat(defaults.getStepUpThreshold()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("Delete removes the policy record")
    void deleteRemovesPolicy() {
        service.delete("dialog-lk");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).deleteById(captor.capture());
        assertThat(captor.getValue()).isEqualTo("tenant-token-policy:dialog-lk");
    }
}
