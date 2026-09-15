package com.selfcare.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.audit.domain.AuditEvent;
import com.selfcare.audit.repository.AuditEventRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 *
 * Verifies:
 * - record: sets id/recordedAt/occurredAt
 * - recordAction: builds event from TenantContext
 * - getById: returns event or throws NotFoundException
 * - query: passes tenant filter to repository
 * - list: paginated listing
 * - exportCsv: CSV file generation with proper escaping
 * - count: repository count
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository repository;
    private AuditService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        service = new AuditService(repository, mapper);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("admin-1");
        ctx.setSessionId("sess-abc");
        ctx.setCorrelationId("corr-123");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // record
    // ======================================================================

    @Test
    @DisplayName("record sets ID if null and recordedAt/occurredAt if null")
    void record_setsIdAndRecordedAt() {
        AuditEvent event = AuditEvent.builder()
                .tenantId("dialog-lk")
                .action("LOGIN")
                .occurredAt(Instant.now())
                .build();

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent saved = service.record(event);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getRecordedAt()).isNotNull();
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("record preserves pre-set ID and timestamps")
    void record_preservesExistingIdAndTimestamps() {
        AuditEvent event = AuditEvent.builder()
                .id("pre-set-id")
                .tenantId("dialog-lk")
                .action("LOGIN")
                .occurredAt(Instant.parse("2025-01-01T00:00:00Z"))
                .recordedAt(Instant.parse("2025-01-01T00:00:01Z"))
                .build();

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent saved = service.record(event);

        assertThat(saved.getId()).isEqualTo("pre-set-id");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(saved.getRecordedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:01Z"));
    }

    @Test
    @DisplayName("record preserves occurredAt when provided")
    void record_preservesOccurredAt() {
        AuditEvent event = AuditEvent.builder()
                .tenantId("t1")
                .action("PAYMENT")
                .occurredAt(Instant.parse("2025-06-15T12:00:00Z"))
                .build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent saved = service.record(event);

        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
        assertThat(saved.getRecordedAt()).isNotNull();
    }

    // ======================================================================
    // recordAction
    // ======================================================================

    @Test
    @DisplayName("recordAction builds event from TenantContext")
    void recordAction_usesTenantContext() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent saved = service.recordAction(
                "ACCOUNT_UPDATE",
                "Account",
                "acc-123",
                AuditEvent.Severity.INFO,
                "Updated email");

        assertThat(saved.getTenantId()).isEqualTo("dialog-lk");
        assertThat(saved.getUserId()).isEqualTo("admin-1");
        assertThat(saved.getSessionId()).isEqualTo("sess-abc");
        assertThat(saved.getAction()).isEqualTo("ACCOUNT_UPDATE");
        assertThat(saved.getResourceType()).isEqualTo("Account");
        assertThat(saved.getResourceId()).isEqualTo("acc-123");
        assertThat(saved.getSeverity()).isEqualTo("INFO");
        assertThat(saved.getMessage()).isEqualTo("Updated email");
        assertThat(saved.getCorrelationId()).isNotNull();
        assertThat(saved.getId()).isNotBlank();
    }

    @Test
    @DisplayName("recordAction defaults severity to INFO when null")
    void recordAction_defaultsSeverity() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent saved = service.recordAction(
                "VIEW", "Page", "page-1", null, null);

        assertThat(saved.getSeverity()).isEqualTo("INFO");
        assertThat(saved.getMessage()).isNull();
    }

    // ======================================================================
    // getById
    // ======================================================================

    @Test
    @DisplayName("getById returns event when found")
    void getById_found() {
        AuditEvent event = AuditEvent.builder()
                .id("ev-1")
                .tenantId("dialog-lk")
                .action("LOGIN")
                .build();
        when(repository.findById("ev-1")).thenReturn(java.util.Optional.of(event));

        AuditEvent result = service.getById("ev-1");

        assertThat(result.getId()).isEqualTo("ev-1");
    }

    @Test
    @DisplayName("getById throws NotFoundException when not found")
    void getById_notFound() {
        when(repository.findById("unknown")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getById("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // query
    // ======================================================================

    @Test
    @DisplayName("query passes tenant filter to repository")
    void query_passesTenantFilter() {
        AuditEvent event = AuditEvent.builder().id("ev-1").build();
        Page<AuditEvent> page = new PageImpl<>(List.of(event));
        when(repository.search(eq("dialog-lk"), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditEvent> result = service.query("user-1", "LOGIN", "Account",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                0, 10);

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(
                eq("dialog-lk"),
                eq("user-1"), eq("LOGIN"), eq("Account"),
                any(Instant.class), any(Instant.class),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("query caps page size at 200")
    void query_capsPageSize() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.query(null, null, null, null, null, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(
                eq("dialog-lk"), any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    // ======================================================================
    // list
    // ======================================================================

    @Test
    @DisplayName("list returns paginated events for tenant")
    void list_paginated() {
        AuditEvent event = AuditEvent.builder().id("ev-1").build();
        Page<AuditEvent> page = new PageImpl<>(List.of(event));
        when(repository.findByTenantIdOrderByOccurredAtDesc(eq("dialog-lk"), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditEvent> result = service.list(0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    // ======================================================================
    // exportCsv
    // ======================================================================

    @Test
    @DisplayName("exportCsv writes a valid CSV file with headers")
    void exportCsv_writesCsvFile() throws IOException {
        AuditEvent e1 = AuditEvent.builder()
                .id("ev-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .action("LOGIN")
                .resourceType("AUTH")
                .severity("SUCCESS")
                .occurredAt(Instant.parse("2025-01-01T10:00:00Z"))
                .recordedAt(Instant.parse("2025-01-01T10:00:01Z"))
                .build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1)));

        String filePath = service.exportCsv(null, null, null, null, null);

        assertThat(filePath).isNotBlank();
        Path path = Path.of(filePath);
        assertThat(Files.exists(path)).isTrue();
        String content = Files.readString(path);
        assertThat(content).contains("ev-1");
        assertThat(content).contains("LOGIN");
        assertThat(content).contains("dialog-lk");
        assertThat(content).contains("id,tenant_id,user_id"); // header
        Files.deleteIfExists(path);
    }

    @Test
    @DisplayName("exportCsv escapes CSV special characters (commas, quotes, newlines)")
    void exportCsv_escapesSpecialChars() throws IOException {
        AuditEvent e = AuditEvent.builder()
                .id("ev-2")
                .tenantId("dialog-lk")
                .action("NOTE")
                .message("Hello, world! \"quoted\" and newline\nhere")
                .occurredAt(Instant.now())
                .recordedAt(Instant.now())
                .build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        String filePath = service.exportCsv(null, null, null, null, null);
        String content = Files.readString(Path.of(filePath));

        // Properly quoted field: "Hello, world! ""quoted"" and newline\nhere"
        assertThat(content).contains("\"Hello, world! \"\"quoted\"\" and newline");
        Files.deleteIfExists(Path.of(filePath));
    }

    @Test
    @DisplayName("exportCsv handles null message field")
    void exportCsv_nullMessage() throws IOException {
        AuditEvent e = AuditEvent.builder()
                .id("ev-3")
                .tenantId("dialog-lk")
                .action("LOGIN")
                .message(null)
                .occurredAt(Instant.now())
                .recordedAt(Instant.now())
                .build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        String filePath = service.exportCsv(null, null, null, null, null);
        String content = Files.readString(Path.of(filePath));
        // Should not throw NPE
        assertThat(content).contains("ev-3");
        Files.deleteIfExists(Path.of(filePath));
    }

    // ======================================================================
    // count
    // ======================================================================

    @Test
    @DisplayName("count returns repository count")
    void count_returnsRepositoryCount() {
        when(repository.count()).thenReturn(42L);

        assertThat(service.count()).isEqualTo(42L);
    }

    // ======================================================================
    // private helpers — exercised via recordAction / exportCsv
    // ======================================================================

    @Test
    @DisplayName("escapeCsv handles simple text without special chars")
    void escapeCsv_simpleText() throws IOException {
        AuditEvent e = AuditEvent.builder()
                .id("ev-4")
                .tenantId("dialog-lk")
                .action("LOGIN")
                .message("Simple text no special chars")
                .occurredAt(Instant.now())
                .recordedAt(Instant.now())
                .build();

        when(repository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        String filePath = service.exportCsv(null, null, null, null, null);
        String content = Files.readString(Path.of(filePath));
        // Simple text should not be quoted
        assertThat(content).contains("Simple text no special chars");
        Files.deleteIfExists(Path.of(filePath));
    }

    @Test
    @DisplayName("record saves event with tenant from TenantContext")
    void record_usesTenantContext() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent event = AuditEvent.builder().build();
        service.record(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        // Tenant is set via TenantContext (set to "dialog-lk" in @BeforeEach)
        assertThat(captor.getValue().getTenantId()).isEqualTo("dialog-lk");
    }
}
