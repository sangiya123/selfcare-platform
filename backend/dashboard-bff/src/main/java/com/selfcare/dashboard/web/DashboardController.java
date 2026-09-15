package com.selfcare.dashboard.web;

import com.selfcare.dashboard.service.DashboardOrchestratorService;
import com.selfcare.dashboard.web.dto.DashboardResponse;
import com.selfcare.dashboard.web.dto.WidgetResult;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Dashboard BFF REST controller.
 *
 * Endpoints:
 *   GET  /api/v1/dashboard/home        — Full dashboard with all widgets
 *   POST /api/v1/dashboard/home/refresh — Refresh specific widgets
 *   GET  /api/v1/dashboard/widgets/{id} — Single widget (for partial refresh)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Resilient dashboard orchestration with partial widget response")
public class DashboardController {

    private final DashboardOrchestratorService orchestrator;

    @GetMapping("/home")
    @Operation(summary = "Get dashboard", description = "Returns dashboard with all configured widgets, executed concurrently with partial response")
    public Mono<ResponseEntity<ApiResponse<DashboardResponse>>> getDashboard(
            @Parameter(hidden = true) @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Parameter(description = "Connection ID context") @RequestParam(required = false) String connectionId,
            @Parameter(description = "Profile key for experience resolution") @RequestParam(required = false) String profileKey,
            @Parameter(description = "Specific widget IDs to execute (empty = all)") @RequestParam(required = false) List<String> widgets) {

        String resolvedTenant = tenantId != null ? tenantId : TenantContext.get().getTenantId();
        String correlationId = TenantContext.get().getCorrelationId();

        log.info("Dashboard request: tenant={}, connection={}, profile={}, widgets={}, correlation={}",
                resolvedTenant, connectionId, profileKey, widgets, correlationId);

        return orchestrator.orchestrateDashboard(resolvedTenant, connectionId, profileKey, widgets)
                .map(response -> ResponseEntity.ok(ApiResponse.of(response, correlationId)))
                .onErrorResume(e -> {
                    log.error("Dashboard orchestration failed: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.<DashboardResponse>builder()
                                    .data(null)
                                    .meta(com.selfcare.platform.common.web.ResponseMeta.builder().build())
                                    .build()));
                });
    }

    @GetMapping("/widgets/{widgetId}")
    @Operation(summary = "Refresh a single widget", description = "Refresh one specific widget by ID (for targeted retry)")
    public Mono<ResponseEntity<ApiResponse<WidgetResult>>> refreshWidget(
            @Parameter(hidden = true) @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable String widgetId,
            @RequestParam(required = false) String connectionId,
            @RequestParam(required = false) String profileKey) {

        String resolvedTenant = tenantId != null ? tenantId : TenantContext.get().getTenantId();
        String correlationId = TenantContext.get().getCorrelationId();

        return orchestrator.refreshWidget(widgetId, resolvedTenant, connectionId, profileKey)
                .map(result -> ResponseEntity.ok(ApiResponse.of(result, correlationId)));
    }

    @PostMapping("/home/refresh")
    @Operation(summary = "Refresh specific widgets", description = "POST widget IDs to refresh individual widgets")
    public Mono<ResponseEntity<ApiResponse<DashboardResponse>>> refreshDashboard(
            @Parameter(hidden = true) @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(required = false) String connectionId,
            @RequestParam(required = false) String profileKey,
            @RequestBody List<String> widgetIds) {

        String resolvedTenant = tenantId != null ? tenantId : TenantContext.get().getTenantId();
        String correlationId = TenantContext.get().getCorrelationId();

        return orchestrator.orchestrateDashboard(resolvedTenant, connectionId, profileKey, widgetIds)
                .map(response -> ResponseEntity.ok(ApiResponse.of(response, correlationId)));
    }
}