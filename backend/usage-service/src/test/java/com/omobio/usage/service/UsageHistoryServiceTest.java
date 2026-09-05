package com.omobio.usage.service;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.usage.adapter.BalanceProvider;
import com.omobio.usage.domain.UsageRecord;
import com.omobio.usage.repository.UsageRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class UsageHistoryServiceTest {

    @Mock private UsageRecordRepository repository;
    private UsageHistoryService service;

    @BeforeEach
    void setUp() {
        service = new UsageHistoryService(repository);
        TenantContext.current().setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record persists all balance and summary fields")
    void record_persistsAllFields() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BalanceProvider.Balance balance = BalanceProvider.Balance.builder()
                .connectionId("conn-1")
                .amount(BigDecimal.valueOf(1500))
                .currency("LKR")
                .balanceType("PREPAID")
                .expiryDate(Instant.now().plusSeconds(86400 * 30))
                .timestamp(Instant.now())
                .isPrimary(true)
                .build();

        BalanceProvider.DataUsage data = BalanceProvider.DataUsage.builder()
                .totalBytes(5_000_000_000L)
                .remainingBytes(3_000_000_000L)
                .allowanceBytes(5_000_000_000L)
                .resetDate(Instant.now().plusSeconds(86400 * 7))
                .isUnlimited(false)
                .build();

        BalanceProvider.UsageSummary summary = BalanceProvider.UsageSummary.builder()
                .connectionId("conn-1")
                .periodStart(Instant.now().minusSeconds(86400 * 30))
                .periodEnd(Instant.now())
                .data(data)
                .build();

        UsageRecord saved = service.record("conn-1", balance, summary, "dialog-balance");

        assertThat(saved.getTenantId()).isEqualTo("t1");
        assertThat(saved.getConnectionId()).isEqualTo("conn-1");
        assertThat(saved.getBalanceAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        assertThat(saved.getBalanceCurrency()).isEqualTo("LKR");
        assertThat(saved.getDataTotalBytes()).isEqualTo(5_000_000_000L);
        assertThat(saved.getDataRemainingBytes()).isEqualTo(3_000_000_000L);
        assertThat(saved.getSourceProvider()).isEqualTo("dialog-balance");
        assertThat(saved.getIsStale()).isFalse();
    }

    @Test
    @DisplayName("record handles null balance and summary gracefully")
    void record_handlesNulls() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsageRecord saved = service.record("conn-1", null, null, "test-provider");

        assertThat(saved.getTenantId()).isEqualTo("t1");
        assertThat(saved.getConnectionId()).isEqualTo("conn-1");
        assertThat(saved.getBalanceAmount()).isNull();
        assertThat(saved.getDataTotalBytes()).isNull();
    }

    @Test
    @DisplayName("recentHistory calls repository with correct tenant and cutoff")
    void recentHistory_callsWithCorrectParams() {
        when(repository.findRecentForConnection(eq("t1"), eq("conn-1"), any()))
                .thenReturn(List.of());

        service.recentHistory("conn-1", 30);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findRecentForConnection(eq("t1"), eq("conn-1"), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("lastKnown returns empty when no record exists")
    void lastKnown_emptyWhenNoRecord() {
        when(repository.findFirstByTenantIdAndConnectionIdOrderByRecordedAtDesc("t1", "conn-1"))
                .thenReturn(Optional.empty());

        UsageRecord result = service.lastKnown("conn-1");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("pruneOlderThan returns deleted count")
    void pruneOlderThan_returnsDeletedCount() {
        when(repository.deleteOlderThan(any())).thenReturn(42);

        int result = service.pruneOlderThan(90);

        assertThat(result).isEqualTo(42);
        verify(repository).deleteOlderThan(any(Instant.class));
    }
}
