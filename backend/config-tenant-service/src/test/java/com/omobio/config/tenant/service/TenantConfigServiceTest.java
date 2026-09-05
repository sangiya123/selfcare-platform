package com.omobio.config.tenant.service;

import com.omobio.config.tenant.domain.TenantConfig;
import com.omobio.config.tenant.repository.TenantConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock private TenantConfigRepository repository;

    private TenantConfigService service;

    @BeforeEach
    void setUp() {
        service = new TenantConfigService(repository);
    }

    @Test
    @DisplayName("getConfig returns tenant config when found")
    void getConfig_found() {
        TenantConfig config = TenantConfig.builder()
                .id("dialog-lk")
                .tenantId("dialog-lk")
                .industry("TELCO")
                .status("ACTIVE")
                .build();
        when(repository.findById("dialog-lk")).thenReturn(Optional.of(config));

        TenantConfig result = service.getConfig("dialog-lk");

        assertThat(result.getId()).isEqualTo("dialog-lk");
        assertThat(result.getIndustry()).isEqualTo("TELCO");
    }

    @Test
    @DisplayName("getConfig returns null when tenant not found")
    void getConfig_notFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        TenantConfig result = service.getConfig("unknown");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getConfig resolves industry pack correctly for AIA")
    void getConfig_insuranceTenant() {
        TenantConfig config = TenantConfig.builder()
                .id("aia-multi")
                .tenantId("aia-multi")
                .industry("INSURANCE")
                .status("ACTIVE")
                .build();
        when(repository.findById("aia-multi")).thenReturn(Optional.of(config));

        TenantConfig result = service.getConfig("aia-multi");

        assertThat(result.getIndustry()).isEqualTo("INSURANCE");
    }

    @Test
    @DisplayName("inactive tenant returns null even if record exists")
    void getConfig_inactiveTenant() {
        TenantConfig config = TenantConfig.builder()
                .id("dialog-lk")
                .tenantId("dialog-lk")
                .status("SUSPENDED")
                .build();
        when(repository.findById("dialog-lk")).thenReturn(Optional.of(config));

        TenantConfig result = service.getConfig("dialog-lk");

        assertThat(result).isNull();
    }
}
