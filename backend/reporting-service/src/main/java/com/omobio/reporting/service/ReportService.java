package com.omobio.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.reporting.domain.ReportDefinition;
import com.omobio.reporting.domain.ReportExecution;
import com.omobio.reporting.repository.ReportDefinitionRepository;
import com.omobio.reporting.repository.ReportExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Report service — manages report definitions and executes reports.
 *
 * Supports ad-hoc execution and scheduled execution (via {@link ReportScheduler}).
 *
 * @see ReportScheduler
 * @see ReportRenderer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportDefinitionRepository definitionRepository;
    private final ReportExecutionRepository executionRepository;
    private final ReportRenderer renderer;
    private final ObjectMapper objectMapper;

    @Value("${omobio.reporting.output-dir:/tmp/reports}")
    private String outputDir;

    // -------------------------------------------------------------------------
    // Definition CRUD (admin)
    // -------------------------------------------------------------------------

    /**
     * List all report definitions for the current tenant.
     *
     * @return list of report definitions
     */
    public List<ReportDefinition> listDefinitions() {
        String tenantId = TenantContext.get().getTenantId();
        return definitionRepository.findByTenantId(tenantId);
    }

    /**
     * Get a report definition by ID.
     *
     * @param id the report definition ID
     * @return the definition
     * @throws NotFoundException if not found
     */
    public ReportDefinition getDefinition(String id) {
        String tenantId = TenantContext.get().getTenantId();
        return definitionRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("ReportDefinition", id));
    }

    /**
     * Create a new report definition.
     *
     * @param definition the definition (status defaults to DRAFT if not set)
     * @param createdBy  admin user creating the definition
     * @return the saved definition
     */
    @Transactional
    public ReportDefinition createDefinition(ReportDefinition definition, String createdBy) {
        String tenantId = TenantContext.get().getTenantId();
        definition.setId(UUID.randomUUID().toString());
        definition.setTenantId(tenantId);
        definition.setCreatedBy(createdBy);
        if (definition.getStatus() == null) {
            definition.setStatus(ReportDefinition.Status.DRAFT.name());
        }
        log.info("Creating report definition: tenant={}, name={}", tenantId, definition.getName());
        return definitionRepository.save(definition);
    }

    /**
     * Update a report definition.
     *
     * @param id       the definition ID
     * @param updated  the updated definition
     * @param updatedBy admin making the update
     * @return the saved definition
     */
    @Transactional
    public ReportDefinition updateDefinition(String id, ReportDefinition updated, String updatedBy) {
        String tenantId = TenantContext.get().getTenantId();
        ReportDefinition existing = getDefinition(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setQuery(updated.getQuery());
        existing.setParameters(updated.getParameters());
        existing.setSchedule(updated.getSchedule());
        existing.setRecipients(updated.getRecipients());
        existing.setOutputFormat(updated.getOutputFormat());
        existing.setUpdatedBy(updatedBy);
        log.info("Updated report definition: tenant={}, id={}", tenantId, id);
        return definitionRepository.save(existing);
    }

    /**
     * Activate a report definition (DRAFT -> ACTIVE).
     *
     * @param id the definition ID
     * @return the activated definition
     */
    @Transactional
    public ReportDefinition activateDefinition(String id) {
        ReportDefinition def = getDefinition(id);
        if (ReportDefinition.Status.DRAFT.name().equals(def.getStatus())) {
            def.setStatus(ReportDefinition.Status.ACTIVE.name());
        }
        return definitionRepository.save(def);
    }

    /**
     * Disable a report definition.
     *
     * @param id the definition ID
     * @return the disabled definition
     */
    @Transactional
    public ReportDefinition disableDefinition(String id) {
        ReportDefinition def = getDefinition(id);
        def.setStatus(ReportDefinition.Status.DISABLED.name());
        return definitionRepository.save(def);
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    /**
     * Trigger an ad-hoc report execution.
     *
     * @param reportId  the report definition ID
     * @param parameters JSON string of parameters for this run
     * @param triggeredBy who triggered this (admin user or "scheduler")
     * @return the created execution record
     */
    @Transactional
    public ReportExecution triggerExecution(String reportId, String parameters, String triggeredBy) {
        String tenantId = TenantContext.get().getTenantId();
        ReportDefinition definition = getDefinition(reportId);

        if (!ReportDefinition.Status.ACTIVE.name().equals(definition.getStatus())) {
            throw new BadRequestException("Report definition is not active: " + reportId);
        }

        ReportExecution execution = ReportExecution.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .reportId(reportId)
                .status(ReportExecution.Status.PENDING.name())
                .startedAt(Instant.now())
                .parameters(parameters)
                .triggeredBy(triggeredBy)
                .build();

        execution = executionRepository.save(execution);
        log.info("Report execution queued: tenant={}, reportId={}, executionId={}",
                tenantId, reportId, execution.getId());

        // Execute asynchronously
        executeReportAsync(execution.getId(), definition.getId());

        return execution;
    }

    /**
     * Get a specific execution by ID.
     *
     * @param executionId the execution ID
     * @return the execution record
     */
    public ReportExecution getExecution(String executionId) {
        String tenantId = TenantContext.get().getTenantId();
        return executionRepository.findById(executionId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("ReportExecution", executionId));
    }

    /**
     * List executions for a report, paginated.
     *
     * @param reportId the report definition ID
     * @param page     page number (0-indexed)
     * @param size     page size
     * @return page of executions
     */
    public Page<ReportExecution> listExecutions(String reportId, int page, int size) {
        String tenantId = TenantContext.get().getTenantId();
        return executionRepository.findByTenantIdAndReportIdOrderByCreatedAtDesc(
                tenantId, reportId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /**
     * Execute a report asynchronously.
     *
     * @param executionId the execution ID
     * @param definitionId the report definition ID
     */
    @Async("reportExecutor")
    @Transactional
    public void executeReportAsync(String executionId, String definitionId) {
        // Bind tenant context for async thread
        String tenantId = TenantContext.get().getTenantId();
        com.omobio.platform.common.tenant.TenantContext.get().setTenantId(tenantId);

        ReportExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("ReportExecution", executionId));
        ReportDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new NotFoundException("ReportDefinition", definitionId));

        execution.setStatus(ReportExecution.Status.RUNNING.name());
        executionRepository.save(execution);

        try {
            // Resolve parameters
            Map<String, Object> params = parseParameters(execution.getParameters());

            // Execute query (stub: generate sample data)
            // In production this executes the SQL via JdbcTemplate or similar
            List<Map<String, Object>> rows = executeQuery(definition, params);

            // Render result
            ReportRenderer.RenderResult result = renderer.render(execution, definition, rows);

            // Update execution with result
            execution.setStatus(ReportExecution.Status.COMPLETED.name());
            execution.setCompletedAt(Instant.now());
            execution.setResultUrl(result.getResultUrl());
            execution.setResultSizeBytes(result.getResultSizeBytes());
            execution.setRowCount(result.getRowCount());
            executionRepository.save(execution);

            log.info("Report execution completed: executionId={}, rows={}", executionId, result.getRowCount());

        } catch (Exception e) {
            log.error("Report execution failed: executionId={}", executionId, e);
            execution.setStatus(ReportExecution.Status.FAILED.name());
            execution.setCompletedAt(Instant.now());
            execution.setError(e.getMessage());
            executionRepository.save(execution);
        }
    }

    /**
     * Execute the report query and return rows.
     *
     * Stub implementation: generates sample data.
     * In production: substitute parameters into the SQL template and execute via JdbcTemplate.
     */
    private List<Map<String, Object>> executeQuery(ReportDefinition definition,
                                                    Map<String, Object> parameters) {
        log.debug("Executing report query: reportId={}, params={}", definition.getId(), parameters);
        // Stub: return empty list. Real implementation executes definition.getQuery()
        // with parameter substitution.
        return List.of();
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse parameters JSON: {}", parametersJson);
            return Map.of();
        }
    }
}
