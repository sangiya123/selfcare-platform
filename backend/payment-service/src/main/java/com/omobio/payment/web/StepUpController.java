package com.omobio.payment.web;

import com.omobio.payment.service.StepUpService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Step-up authentication REST API.
 *
 * Endpoints:
 * - POST /api/v1/payment/stepup/initiate — Start step-up flow (request OTP)
 * - POST /api/v1/payment/stepup/verify   — Verify OTP and receive step-up token
 */
@RestController
@RequestMapping("/api/v1/payment/stepup")
@RequiredArgsConstructor
public class StepUpController {

    private final StepUpService stepUpService;

    /**
     * Initiate step-up. Returns a correlationId the client uses to verify the code.
     */
    @PostMapping("/initiate")
    public ResponseEntity<StepUpService.StepUpInitiation> initiate(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiateRequest request) {

        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        if (tenantId == null || userId == null) {
            throw new UnauthorizedException("Missing tenant or user context");
        }

        StepUpService.StepUpInitiation result = stepUpService.initiate(
                tenantId, userId, request.action(), request.amount(), idempotencyKey);

        return ResponseEntity.ok(result);
    }

    /**
     * Verify the OTP code and issue a step-up token.
     */
    @PostMapping("/verify")
    public ResponseEntity<StepUpService.StepUpToken> verify(@Valid @RequestBody VerifyRequest request) {
        StepUpService.StepUpToken result = stepUpService.verify(request.correlationId(), request.code());
        return ResponseEntity.ok(result);
    }

    public record InitiateRequest(
            @NotBlank String action,
            @NotNull BigDecimal amount) {}

    public record VerifyRequest(
            @NotBlank String correlationId,
            @NotBlank String code) {}
}
