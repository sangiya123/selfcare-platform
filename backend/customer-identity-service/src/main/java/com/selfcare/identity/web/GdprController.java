package com.selfcare.identity.web;

import com.selfcare.identity.domain.ConsentRecord;
import com.selfcare.identity.domain.DataErasureRequest;
import com.selfcare.identity.service.GdprService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for GDPR consent + data erasure.
 *
 * Public surface:
 *   - POST   /api/v1/customer/consent         capture / supersede a consent
 *   - GET    /api/v1/customer/consent         list current consents
 *   - POST   /api/v1/customer/data-erasure    initiate right-to-be-forgotten
 *   - GET    /api/v1/customer/data-erasure/{id}  get status
 *   - GET    /api/v1/customer/data-export     Article 20 data portability
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class GdprController {

    private final GdprService gdprService;

    // ===== CONSENT =====

    @PostMapping("/consent")
    public ResponseEntity<ApiResponse<ConsentRecord>> captureConsent(
            @RequestBody ConsentRequest body,
            HttpServletRequest httpRequest) {
        ConsentRecord saved = gdprService.recordConsent(
                body.userId(),
                body.purpose(),
                body.granted(),
                body.version(),
                body.source(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved));
    }

    @GetMapping("/consent")
    public ApiResponse<List<ConsentRecord>> listConsents(@RequestParam String userId) {
        return ApiResponse.ok(gdprService.listActiveConsents(userId));
    }

    @GetMapping("/consent/check")
    public ApiResponse<Map<String, Object>> checkConsent(
            @RequestParam String userId,
            @RequestParam String purpose) {
        return ApiResponse.ok(Map.of(
                "userId", userId,
                "purpose", purpose,
                "granted", gdprService.hasConsent(userId, purpose)
        ));
    }

    // ===== DATA ERASURE =====

    @PostMapping("/data-erasure")
    public ResponseEntity<ApiResponse<DataErasureRequest>> requestErasure(
            @RequestBody ErasureRequest body,
            HttpServletRequest httpRequest) {
        DataErasureRequest req = gdprService.requestErasure(
                body.userId(),
                body.reason(),
                body.requestedBy(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        // Emit the Kafka event in the background — downstream services
        // will act on it.
        gdprService.emitErasureEvent(req, body.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(req));
    }

    @GetMapping("/data-erasure/{id}")
    public ApiResponse<DataErasureRequest> getErasureStatus(@PathVariable UUID id) {
        return ApiResponse.ok(gdprService.getErasureStatus(id));
    }

    // ===== DATA EXPORT (Article 20) =====

    @GetMapping("/data-export")
    public ApiResponse<Map<String, Object>> exportData(@RequestParam String userId) {
        List<ConsentRecord> consents = gdprService.exportConsents(userId);
        return ApiResponse.ok(Map.of(
                "exportedAt", java.time.Instant.now().toString(),
                "userId", userId,
                "tenantId", TenantContext.get().getTenantId(),
                "consents", consents
        ));
    }

    // ===== helpers =====

    private String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ===== request DTOs =====

    public record ConsentRequest(
            String userId,
            String purpose,
            boolean granted,
            String version,
            String source
    ) {}

    public record ErasureRequest(
            String userId,
            String reason,
            String requestedBy
    ) {}
}
