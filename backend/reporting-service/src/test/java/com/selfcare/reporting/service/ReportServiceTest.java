package com.selfcare.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.reporting.domain.ReportDefinition;
import com.selfcare.reporting.domain.ReportExecution;
import com.selfcare.reporting.repository.ReportDefinitionRepository;
import com.selfcare.reporting.repository.ReportExecutionRepository;
import com.selfcare.reporting.service.ReportRenderer.RenderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReportService}.
 *
 * Verifies:
 * - Report definition CRUD (list, get, create, update, activate, disable)
 * - Execution lifecycle (trigger, get, list)
 * - Error cases: not found, inactive definition
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportDefinitionRepository definitionRepository;
    @Mock private ReportExecutionRepository executionRepository;
    @Mock private ReportRenderer renderer;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(definitionRepository, executionRepository,
                renderer, new ObjectMapper());

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("admin-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReportDefinition definition(String id, String name, String status) {
        return ReportDefinition.builder()
                .id(id)
                .tenantId("dialog-lk")
                .name(name)
                .query("SELECT * FROM usage")
                .outputFormat("CSV")
                .status(status)
                .build();
    }

    // ======================================================================
    // Definition CRUD
    // ======================================================================

    @Test
    @DisplayName("listDefinitions returns all definitions for tenant")
    void listDefinitions_returnsAll() {
        when(definitionRepository.findByTenantId("dialog-lk"))
                .thenReturn(List.of(
                        definition("rd-1", "Billing Summary", "ACTIVE"),
                        definition("rd-2", "Usage Report", "DRAFT")));

        List<ReportDefinition> result = service.listDefinitions();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getDefinition returns definition when found")
    void getDefinition_found() {
        when(definitionRepository.findById("rd-1"))
                .thenReturn(Optional.of(definition("rd-1", "Billing Summary", "ACTIVE")));

        ReportDefinition result = service.getDefinition("rd-1");

        assertThat(result.getId()).isEqualTo("rd-1");
    }

    @Test
    @DisplayName("getDefinition throws NotFoundException when missing")
    void getDefinition_notFound() {
        when(definitionRepository.findById("rd-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefinition("rd-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getDefinition rejects definition from another tenant")
    void getDefinition_wrongTenant() {
        ReportDefinition otherTenant = definition("rd-9", "Foreign", "ACTIVE");
        otherTenant.setTenantId("other-lk");
        when(definitionRepository.findById("rd-9")).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> service.getDefinition("rd-9"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("createDefinition assigns id, tenant, creator and DRAFT status")
    void createDefinition_setsDefaults() {
        ReportDefinition input = ReportDefinition.builder()
                .name("New Report")
                .query("SELECT 1")
                .outputFormat("JSON")
                .build();
        when(definitionRepository.save(any(ReportDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReportDefinition result = service.createDefinition(input, "admin-1");

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getTenantId()).isEqualTo("dialog-lk");
        assertThat(result.getCreatedBy()).isEqualTo("admin-1");
        assertThat(result.getStatus()).isEqualTo(ReportDefinition.Status.DRAFT.name());
    }

    @Test
    @DisplayName("activateDefinition transitions DRAFT to ACTIVE")
    void activateDefinition_draftToActive() {
        when(definitionRepository.findById("rd-1"))
                .thenReturn(Optional.of(definition("rd-1", "Billing", "DRAFT")));
        when(definitionRepository.save(any(ReportDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReportDefinition result = service.activateDefinition("rd-1");

        assertThat(result.getStatus()).isEqualTo(ReportDefinition.Status.ACTIVE.name());
    }

    @Test
    @DisplayName("disableDefinition transitions definition to DISABLED")
    void disableDefinition_setsDisabled() {
        when(definitionRepository.findById("rd-1"))
                .thenReturn(Optional.of(definition("rd-1", "Billing", "ACTIVE")));
        when(definitionRepository.save(any(ReportDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReportDefinition result = service.disableDefinition("rd-1");

        assertThat(result.getStatus()).isEqualTo(ReportDefinition.Status.DISABLED.name());
    }

    // ======================================================================
    // Execution lifecycle
    // ======================================================================

    @Test
    @DisplayName("triggerExecution queues and runs an execution for an active definition")
    void triggerExecution_queuesAndRuns() throws Exception {
        reportDefActive();
        ReportExecution[] savedHolder = new ReportExecution[1];
        when(executionRepository.save(any(ReportExecution.class))).thenAnswer(inv -> {
            savedHolder[0] = inv.getArgument(0);
            return savedHolder[0];
        });
        when(executionRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.of(savedHolder[0]));
        when(renderer.render(any(), any(), anyList()))
                .thenReturn(RenderResult.builder()
                        .resultUrl("/api/v1/reports/executions/ex-1/download")
                        .resultSizeBytes(10L)
                        .rowCount(0L)
                        .build());

        ReportExecution result = service.triggerExecution("rd-1", "{\"month\":\"2026-08\"}", "admin-1");

        assertThat(result.getReportId()).isEqualTo("rd-1");
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getTriggeredBy()).isEqualTo("admin-1");
        assertThat(result.getStatus()).isEqualTo(ReportExecution.Status.COMPLETED.name());
        assertThat(result.getResultUrl()).isEqualTo("/api/v1/reports/executions/ex-1/download");
        verify(renderer).render(any(), any(), anyList());
    }

    @Test
    @DisplayName("triggerExecution throws BadRequestException for inactive definition")
    void triggerExecution_notActive() {
        when(definitionRepository.findById("rd-1"))
                .thenReturn(Optional.of(definition("rd-1", "Billing", "DRAFT")));

        assertThatThrownBy(() -> service.triggerExecution("rd-1", null, "admin-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("getExecution returns execution when found")
    void getExecution_found() {
        ReportExecution execution = execution("ex-1");
        when(executionRepository.findById("ex-1")).thenReturn(Optional.of(execution));

        ReportExecution result = service.getExecution("ex-1");

        assertThat(result.getId()).isEqualTo("ex-1");
    }

    @Test
    @DisplayName("getExecution throws NotFoundException when missing")
    void getExecution_notFound() {
        when(executionRepository.findById("ex-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExecution("ex-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listExecutions returns paginated executions for a report")
    void listExecutions_returnsPage() {
        ReportExecution execution = execution("ex-1");
        when(executionRepository.findByTenantIdAndReportIdOrderByCreatedAtDesc(
                eq("dialog-lk"), eq("rd-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(execution)));

        var result = service.listExecutions("rd-1", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReportId()).isEqualTo("rd-1");
    }

    private void reportDefActive() {
        when(definitionRepository.findById("rd-1"))
                .thenReturn(Optional.of(definition("rd-1", "Billing", "ACTIVE")));
    }

    private ReportExecution execution(String id) {
        return ReportExecution.builder()
                .id(id)
                .tenantId("dialog-lk")
                .reportId("rd-1")
                .status(ReportExecution.Status.PENDING.name())
                .parameters("{\"month\":\"2026-08\"}")
                .triggeredBy("admin-1")
                .build();
    }
}