package com.omobio.notification.web;

import com.omobio.notification.domain.Notification;
import com.omobio.notification.service.NotificationService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification sending and history")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Send a notification")
    public ResponseEntity<ApiResponse<Notification>> send(
            @RequestBody SendRequest request) {
        Notification notification = notificationService.send(
                request.userId(), request.channel(), request.templateId(),
                request.recipient(), request.payload(), request.locale());
        return ResponseEntity.ok(ApiResponse.of(notification, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<ApiResponse<Notification>> get(@PathVariable String notificationId) {
        return ResponseEntity.ok(ApiResponse.of(
                notificationService.getNotification(notificationId),
                TenantContext.get().getCorrelationId()));
    }

    public record SendRequest(
            String userId,
            String channel,
            String templateId,
            String recipient,
            Map<String, Object> payload,
            String locale
    ) {}
}