package com.omobio.reporting.service;

import com.omobio.reporting.domain.ReportDefinition;
import com.omobio.reporting.domain.ReportJob;
import com.omobio.reporting.repository.ReportDefinitionRepository;
import com.omobio.reporting.repository.ReportJobRepository;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    @Mock private ReportDefinitionRepository definitionRepository;
    @Mock private ReportJobRepository jobRepository;

    private ReportGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ReportGenerationService(definitionRepository, jobRepository);
    }

    @Test
    @DisplayName("scheduleReport creates a job with PENDING status")
    void scheduleReport_createsPendingJob() {
        ReportDefinition def = ReportDefinition.builder()
                .id("rd-1")
                .reportId("monthly-billing")
                .name("Monthly Billing Report")
                .format("PDF")
                .build();

        ReportJob saved = ReportJob.builder()
                .id("rj-1")
                .reportId("monthly-billing")
                .tenantId("dialog-lk")
                .status("PENDING")
                .build();

        when(definitionRepository.findByReportId("monthly-billing"))
                .thenReturn(Optional.of(def));
        when(jobRepository.save(any(ReportJob.class))).thenReturn(saved);

        ReportJob result = service.scheduleReport("monthly-billing", "dialog-lk",
                Map.of("month", "2026-08"));

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getReportId()).isEqualTo("monthly-billing");
    }

    @Test
    @DisplayName("scheduleReport throws NotFoundException for unknown report")
    void scheduleReport_notFound() {
        when(definitionRepository.findByReportId("unknown-report"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scheduleReport("unknown-report",
                "dialog-lk", Map.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getJobStatus returns job by ID")
    void getJobStatus_found() {
        ReportJob job = ReportJob.builder()
                .id("rj-1")
                .reportId("monthly-billing")
                .status("COMPLETED")
                .build();

        when(jobRepository.findById("rj-1")).thenReturn(Optional.of(job));

        ReportJob result = service.getJobStatus("rj-1");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("listReportDefinitions returns all for tenant")
    void listReportDefinitions() {
        ReportDefinition r1 = ReportDefinition.builder()
                .id("rd-1").reportId("billing-summary").name("Billing Summary").build();
        ReportDefinition r2 = ReportDefinition.builder()
                .id("rd-2").reportId("usage-report").name("Usage Report").build();

        when(definitionRepository.findByTenantId("dialog-lk"))
                .thenReturn(List.of(r1, r2));

        List<ReportDefinition> result = service.listReportDefinitions("dialog-lk");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("markJobCompleted sets status and download URL")
    void markJobCompleted() {
        ReportJob job = ReportJob.builder()
                .id("rj-1")
                .reportId("monthly-billing")
                .status("PROCESSING")
                .build();

        when(jobRepository.save(any(ReportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportJob result = service.markJobCompleted("rj-1", "https://cdn/reports/rj-1.pdf");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getDownloadUrl()).isEqualTo("https://cdn/reports/rj-1.pdf");
        assertThat(result.getCompletedAt()).isNotNull();
    }
}
