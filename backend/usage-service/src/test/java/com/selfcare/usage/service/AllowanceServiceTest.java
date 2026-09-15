package com.selfcare.usage.service;

import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.usage.domain.Allowance;
import com.selfcare.usage.repository.AllowanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllowanceServiceTest {

    @Mock private AllowanceRepository repository;
    private AllowanceService service;

    @BeforeEach
    void setUp() {
        service = new AllowanceService(repository);
        TenantContext.current().setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getActiveAllowances returns ACTIVE allowances ordered by expiresAt ASC")
    void getActiveAllowances() {
        Allowance a = allowance("alw-1", "ACTIVE", 1000L, 300L);
        when(repository.findActiveAllowances("t1", "conn-1")).thenReturn(List.of(a));

        List<Allowance> result = service.getActiveAllowances("conn-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAllowanceId()).isEqualTo("alw-1");
    }

    @Test
    @DisplayName("getAllowance throws NotFoundException when not found")
    void getAllowance_notFound() {
        when(repository.findByTenantIdAndConnectionIdAndAllowanceId("t1", "conn-1", "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAllowance("conn-1", "missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("upsertAllowance sets tenant from context when not provided")
    void upsertAllowance_setsTenantFromContext() {
        Allowance a = Allowance.builder()
                .allowanceId("alw-x")
                .connectionId("conn-1")
                .allowanceType("DATA")
                .totalUnits(5000L)
                .usedUnits(0L)
                .remainingUnits(5000L)
                .status("ACTIVE")
                .build();

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Allowance saved = service.upsertAllowance(a);

        assertThat(saved.getTenantId()).isEqualTo("t1");
        verify(repository).save(a);
    }

    @Test
    @DisplayName("upsertAllowance infers status from expiry/remaining")
    void upsertAllowance_infersStatus() {
        Allowance expired = Allowance.builder()
                .allowanceId("alw-exp")
                .connectionId("conn-1")
                .allowanceType("DATA")
                .totalUnits(1000L)
                .usedUnits(500L)
                .remainingUnits(500L)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Allowance saved = service.upsertAllowance(expired);

        assertThat(saved.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("recordConsumption updates used and remaining correctly")
    void recordConsumption_updatesCorrectly() {
        Allowance a = allowance("alw-1", "ACTIVE", 1000L, 700L);
        when(repository.findByTenantIdAndConnectionIdAndAllowanceId("t1", "conn-1", "alw-1"))
                .thenReturn(Optional.of(a));

        service.recordConsumption("conn-1", "alw-1", 200L);

        verify(repository).updateConsumption("t1", "conn-1", "alw-1", 500L, 500L);
    }

    @Test
    @DisplayName("recordConsumption caps remaining at zero")
    void recordConsumption_capsAtZero() {
        Allowance a = allowance("alw-1", "ACTIVE", 100L, 10L);
        when(repository.findByTenantIdAndConnectionIdAndAllowanceId("t1", "conn-1", "alw-1"))
                .thenReturn(Optional.of(a));

        service.recordConsumption("conn-1", "alw-1", 200L);

        verify(repository).updateConsumption("t1", "conn-1", "alw-1", 290L, 0L);
    }

    @Test
    @DisplayName("markExpired returns count of updated rows")
    void markExpired_returnsCount() {
        when(repository.markExpiredAsOf(any())).thenReturn(5);

        int result = service.markExpired(Instant.now());

        assertThat(result).isEqualTo(5);
    }

    private Allowance allowance(String id, String status, long total, long remaining) {
        return Allowance.builder()
                .allowanceId(id)
                .tenantId("t1")
                .connectionId("conn-1")
                .allowanceType("DATA")
                .totalUnits(total)
                .usedUnits(total - remaining)
                .remainingUnits(remaining)
                .unit("BYTES")
                .status(status)
                .activatedAt(Instant.now().minusSeconds(86400))
                .expiresAt(Instant.now().plusSeconds(86400 * 7))
                .build();
    }
}
