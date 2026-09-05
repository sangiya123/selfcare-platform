package com.omobio.audit.service;

import com.omobio.audit.domain.AuditEvent;
import com.omobio.audit.repository.AuditEventRepository;
import com.omobio.platform.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditSearchService.
 *
 * Verifies:
 * - search: filtered query with pagination (uses TenantContext for tenant isolation)
 * - sessionTrail: filters events by session ID from full tenant results
 * - hasRepeatedFailures: counts FAILURE events in a time window
 * - countByAction: aggregates events by action type
 *
 * All methods use TenantContext internally for tenant isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuditSearchServiceTest {

    @Mock private AuditEventRepository repository;
    private AuditSearchService service;

    @BeforeEach
    void setUp() {
        service = new AuditSearchService(repository);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // search
    // ======================================================================

    @Test
    @DisplayName("search returns filtered audit events for current tenant")
    void search_returnsFilteredResults() {
        AuditEvent event = AuditEvent.builder()
                .id("ev-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .action("LOGIN")
                .severity("SUCCESS")
                .build();
        Page<AuditEvent> page = new PageImpl<>(List.of(event));
        when(repository.search(
                eq("dialog-lk"), eq("user-1"), eq("LOGIN"), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditEvent> result = service.search(
                "user-1", "LOGIN", null, null,
                null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("search caps page size at 200")
    void search_capsPageSize() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.search(null, null, null, null, null, null, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(
                eq("dialog-lk"), any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    // ======================================================================
    // sessionTrail
    // ======================================================================

    @Test
    @DisplayName("sessionTrail filters events by session ID")
    void sessionTrail_filtersBySessionId() {
        AuditEvent ev1 = AuditEvent.builder()
                .id("ev-1").sessionId("sess-123").action("LOGIN").build();
        AuditEvent ev2 = AuditEvent.builder()
                .id("ev-2").sessionId("sess-456").action("LOGIN").build();
        AuditEvent ev3 = AuditEvent.builder()
                .id("ev-3").sessionId("sess-123").action("PAYMENT").build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ev1, ev2, ev3)));

        List<AuditEvent> result = service.sessionTrail("sess-123");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AuditEvent::getSessionId)
                .containsOnly("sess-123");
    }

    @Test
    @DisplayName("sessionTrail returns empty list when no matching session")
    void sessionTrail_emptyForUnknownSession() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<AuditEvent> result = service.sessionTrail("unknown-session");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sessionTrail returns events sorted by occurredAt")
    void sessionTrail_sortedByOccurredAt() {
        AuditEvent ev1 = AuditEvent.builder()
                .id("ev-1").sessionId("sess-123").action("LOGIN")
                .occurredAt(Instant.parse("2025-01-01T10:00:00Z")).build();
        AuditEvent ev2 = AuditEvent.builder()
                .id("ev-2").sessionId("sess-123").action("PAYMENT")
                .occurredAt(Instant.parse("2025-01-01T11:00:00Z")).build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ev2, ev1))); // out of order

        List<AuditEvent> result = service.sessionTrail("sess-123");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("ev-1"); // LOGIN first
        assertThat(result.get(1).getId()).isEqualTo("ev-2"); // PAYMENT second
    }

    // ======================================================================
    // hasRepeatedFailures
    // ======================================================================

    @Test
    @DisplayName("hasRepeatedFailures returns true when threshold reached")
    void hasRepeatedFailures_trueWhenReached() {
        AuditEvent failure1 = AuditEvent.builder()
                .id("ev-1").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now().minus(5, ChronoUnit.MINUTES)).build();
        AuditEvent failure2 = AuditEvent.builder()
                .id("ev-2").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now().minus(3, ChronoUnit.MINUTES)).build();
        AuditEvent failure3 = AuditEvent.builder()
                .id("ev-3").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failure1, failure2, failure3)));

        boolean result = service.hasRepeatedFailures("user-1", 3, 30);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasRepeatedFailures returns false when below threshold")
    void hasRepeatedFailures_falseWhenBelow() {
        AuditEvent failure1 = AuditEvent.builder()
                .id("ev-1").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now().minus(5, ChronoUnit.MINUTES)).build();
        AuditEvent failure2 = AuditEvent.builder()
                .id("ev-2").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failure1, failure2)));

        boolean result = service.hasRepeatedFailures("user-1", 5, 30);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasRepeatedFailures counts only FAILURE severity")
    void hasRepeatedFailures_onlyFailures() {
        AuditEvent failure = AuditEvent.builder()
                .id("ev-1").action("LOGIN").severity("FAILURE")
                .occurredAt(Instant.now()).build();
        AuditEvent warning = AuditEvent.builder()
                .id("ev-2").action("LOGIN").severity("WARNING")
                .occurredAt(Instant.now()).build();
        AuditEvent success = AuditEvent.builder()
                .id("ev-3").action("LOGIN").severity("SUCCESS")
                .occurredAt(Instant.now()).build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failure, warning, success)));

        boolean result = service.hasRepeatedFailures("user-1", 2, 30);

        // Only 1 FAILURE, threshold is 2 → false
        assertThat(result).isFalse();
    }

    // ======================================================================
    // countByAction
    // ======================================================================

    @Test
    @DisplayName("countByAction returns action → count map")
    void countByAction_returnsAggregatedMap() {
        AuditEvent ev1 = AuditEvent.builder().id("ev-1").action("LOGIN").build();
        AuditEvent ev2 = AuditEvent.builder().id("ev-2").action("LOGIN").build();
        AuditEvent ev3 = AuditEvent.builder().id("ev-3").action("PAYMENT").build();
        AuditEvent ev4 = AuditEvent.builder().id("ev-4").action("PAYMENT").build();
        AuditEvent ev5 = AuditEvent.builder().id("ev-5").action("PAYMENT").build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ev1, ev2, ev3, ev4, ev5)));

        Map<String, Long> result = service.countByAction(30);

        assertThat(result.get("LOGIN")).isEqualTo(2L);
        assertThat(result.get("PAYMENT")).isEqualTo(3L);
    }

    @Test
    @DisplayName("countByAction returns empty map when no events")
    void countByAction_emptyWhenNoEvents() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Long> result = service.countByAction(30);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByAction counts all distinct action types")
    void countByAction_distinctActions() {
        AuditEvent ev1 = AuditEvent.builder().id("ev-1").action("LOGIN").build();
        AuditEvent ev2 = AuditEvent.builder().id("ev-2").action("VIEW").build();
        AuditEvent ev3 = AuditEvent.builder().id("ev-3").action("LOGOUT").build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ev1, ev2, ev3)));

        Map<String, Long> result = service.countByAction(7);

        assertThat(result).hasSize(3);
        assertThat(result.get("LOGIN")).isEqualTo(1L);
        assertThat(result.get("VIEW")).isEqualTo(1L);
        assertThat(result.get("LOGOUT")).isEqualTo(1L);
    }
}
