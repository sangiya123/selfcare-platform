package com.omobio.ai.gateway.service;

import com.omobio.ai.domain.AiKillSwitch;
import com.omobio.ai.domain.AiUseCase;
import com.omobio.ai.repository.AiKillSwitchRepository;
import com.omobio.ai.repository.AiUseCaseRepository;
import com.omobio.ai.service.AiGovernanceService;
import com.omobio.ai.service.TokenUsageService;
import com.omobio.platform.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGovernanceServiceTest {

    @Mock private AiUseCaseRepository useCaseRepository;
    @Mock private AiKillSwitchRepository killSwitchRepository;
    @Mock private TokenUsageService tokenUsageService;

    private AiGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new AiGovernanceService(useCaseRepository, killSwitchRepository, tokenUsageService);
        TenantContext.current().setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("registerUseCase rejects unknown provider")
    void register_unknownProvider() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("rogue-provider")
                .build();

        assertThatThrownBy(() -> service.registerUseCase(uc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rogue-provider");
    }

    @Test
    @DisplayName("registerUseCase rejects invalid risk tier")
    void register_invalidRiskTier() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("CRITICAL")
                .approvedProviders("anthropic")
                .build();

        assertThatThrownBy(() -> service.registerUseCase(uc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riskTier");
    }

    @Test
    @DisplayName("registerUseCase rejects retention > 365 days")
    void register_invalidRetention() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic")
                .retentionDays(1000)
                .build();

        assertThatThrownBy(() -> service.registerUseCase(uc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retentionDays");
    }

    @Test
    @DisplayName("registerUseCase accepts valid LOW use case")
    void register_validLow() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic,openai")
                .retentionDays(30)
                .build();
        when(useCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiUseCase saved = service.registerUseCase(uc);
        assertThat(saved.getUseCaseId()).isEqualTo("uc-1");
        assertThat(saved.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("enforcePolicy rejects when use case is disabled")
    void enforcePolicy_disabled() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic")
                .enabled(false)
                .build();
        when(useCaseRepository.findEffective("t1", "uc-1")).thenReturn(Optional.of(uc));

        assertThatThrownBy(() -> service.enforcePolicy("uc-1", "anthropic"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("enforcePolicy rejects when kill switch is active")
    void enforcePolicy_killSwitch() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic")
                .enabled(true)
                .build();
        AiKillSwitch ks = AiKillSwitch.builder()
                .killSwitchId("ks-1")
                .scope("USE_CASE")
                .useCaseId("uc-1")
                .active(true)
                .reason("Testing in progress")
                .build();
        when(useCaseRepository.findEffective("t1", "uc-1")).thenReturn(Optional.of(uc));
        when(killSwitchRepository.findActiveForUseCase("t1", "uc-1")).thenReturn(List.of(ks));

        assertThatThrownBy(() -> service.enforcePolicy("uc-1", "anthropic"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("enforcePolicy rejects when provider not approved")
    void enforcePolicy_providerNotApproved() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic")
                .enabled(true)
                .build();
        when(useCaseRepository.findEffective("t1", "uc-1")).thenReturn(Optional.of(uc));
        when(killSwitchRepository.findActiveForUseCase("t1", "uc-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.enforcePolicy("uc-1", "openai"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");
    }

    @Test
    @DisplayName("enforcePolicy rejects when daily budget exceeded")
    void enforcePolicy_budgetExceeded() {
        AiUseCase uc = AiUseCase.builder()
                .useCaseId("uc-1")
                .riskTier("LOW")
                .approvedProviders("anthropic")
                .enabled(true)
                .dailyBudgetUsd(new BigDecimal("100.00"))
                .build();
        when(useCaseRepository.findEffective("t1", "uc-1")).thenReturn(Optional.of(uc));
        when(killSwitchRepository.findActiveForUseCase("t1", "uc-1")).thenReturn(List.of());
        when(tokenUsageService.todaySpend("t1")).thenReturn(new BigDecimal("150.00"));

        assertThatThrownBy(() -> service.enforcePolicy("uc-1", "anthropic"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("budget");
    }

    @Test
    @DisplayName("activateKillSwitch validates scope")
    void activateKillSwitch_invalidScope() {
        assertThatThrownBy(() ->
                service.activateKillSwitch("INVALID", null, null, "reason", "admin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("activateKillSwitch creates USE_CASE switch")
    void activateKillSwitch_useCase() {
        when(killSwitchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AiKillSwitch ks = service.activateKillSwitch(
                "USE_CASE", "uc-1", null, "Testing", "admin", null);
        assertThat(ks.getScope()).isEqualTo("USE_CASE");
        assertThat(ks.getUseCaseId()).isEqualTo("uc-1");
        assertThat(ks.getActive()).isTrue();
    }

    @Test
    @DisplayName("activateKillSwitch creates PROVIDER switch")
    void activateKillSwitch_provider() {
        when(killSwitchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AiKillSwitch ks = service.activateKillSwitch(
                "PROVIDER", null, "openai", "Outage", "admin",
                Instant.now().plusSeconds(3600));
        assertThat(ks.getScope()).isEqualTo("PROVIDER");
        assertThat(ks.getProvider()).isEqualTo("openai");
        assertThat(ks.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("deactivateKillSwitch returns false when not found")
    void deactivateKillSwitch_notFound() {
        when(killSwitchRepository.findById("nope")).thenReturn(Optional.empty());
        assertThat(service.deactivateKillSwitch("nope")).isFalse();
    }

    @Test
    @DisplayName("deactivateKillSwitch returns true when found")
    void deactivateKillSwitch_found() {
        AiKillSwitch ks = AiKillSwitch.builder()
                .killSwitchId("ks-1")
                .active(true)
                .build();
        when(killSwitchRepository.findById("ks-1")).thenReturn(Optional.of(ks));
        when(killSwitchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.deactivateKillSwitch("ks-1")).isTrue();
        verify(killSwitchRepository).save(argThat(k -> !k.getActive()));
    }
}
