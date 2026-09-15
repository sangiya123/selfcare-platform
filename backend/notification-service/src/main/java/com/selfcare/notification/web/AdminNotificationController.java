package com.selfcare.notification.web;

import com.selfcare.notification.service.NotificationTemplate;
import com.selfcare.notification.service.TemplateService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin REST API for notification template management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notifications/templates")
@RequiredArgsConstructor
@Tag(name = "Admin Notification Templates", description = "Manage notification templates per tenant")
public class AdminNotificationController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(summary = "List templates for a tenant")
    public ResponseEntity<ApiResponse<Page<NotificationTemplate>>> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<NotificationTemplate> templates = templateService.search(tenantId, channel, category, pageable);
        return ResponseEntity.ok(ApiResponse.of(templates, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template by ID")
    public ResponseEntity<ApiResponse<NotificationTemplate>> get(@PathVariable String id) {
        NotificationTemplate template = templateService.getById(id);
        return ResponseEntity.ok(ApiResponse.of(template, TenantContext.get().getCorrelationId()));
    }

    @PostMapping
    @Operation(summary = "Create notification template")
    public ResponseEntity<ApiResponse<NotificationTemplate>> create(@RequestBody NotificationTemplate template) {
        template.setTenantId(TenantContext.get().getTenantId());
        template.setCreatedAt(java.time.Instant.now());
        template.setUpdatedAt(java.time.Instant.now());
        NotificationTemplate saved = templateService.save(template);
        return ResponseEntity.ok(ApiResponse.of(saved, TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update notification template")
    public ResponseEntity<ApiResponse<NotificationTemplate>> update(@PathVariable String id, @RequestBody NotificationTemplate template) {
        template.setId(id);
        template.setUpdatedAt(java.time.Instant.now());
        NotificationTemplate saved = templateService.save(template);
        return ResponseEntity.ok(ApiResponse.of(saved, TenantContext.get().getCorrelationId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification template")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        templateService.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Send test notification using template")
    public ResponseEntity<ApiResponse<String>> test(
            @PathVariable String id,
            @RequestBody TestRequest request) {
        NotificationTemplate template = templateService.getById(id);
        String result = templateService.sendTest(template, request.recipient(), request.payload(), request.locale());
        return ResponseEntity.ok(ApiResponse.of(result, TenantContext.get().getCorrelationId()));
    }

    public record TestRequest(
            String recipient,
            Map<String, Object> payload,
            String locale
    ) {}
}