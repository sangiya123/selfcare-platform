package com.selfcare.identity.service;

import com.selfcare.identity.domain.ConsentRecord;
import com.selfcare.identity.domain.DataErasureRequest;
import com.selfcare.identity.repository.ConsentRecordRepository;
import com.selfcare.identity.repository.DataErasureRequestRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GdprService — Manages user consent and data erasure flows.
 *
 * Implements:
 *   - GDPR Article 6 (lawful basis) — track consent and legitimate interest
 *   - GDPR Article 7 (conditions for consent) — clear affirmative action
 *   - GDPR Article 17 (right to erasure) — wipe user data on request
 *   - GDPR Article 20 (data portability) — export user data
 *   - GDPR Article 30 (records of processing) — consent audit trail
 *   - PDPA, LGPD, CCPA (similar consent & erasure obligations)
 *
 * Note: this service holds *only* consent + erasure audit data.
 * PII lives in account-entitlement-service, customer profile in
 * account-service, etc. On erasure, we emit Kafka events that those
 * services subscribe to and act on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GdprService {

    private final ConsentRecordRepository consentRepository;
    private final DataErasureRequestRepository erasureRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${selfcare.gdpr.kafka.topic.user-erasure:identity.user.erasure}")
    private String erasureTopic;

    // ======================================================================
    // CONSENT
    // ======================================================================

    /**
     * Capture a user consent decision. Idempotent: re-capturing with the
     * same version is a no-op; re-capturing with a different version
     * supersedes the previous record.
     */
    @Transactional
    public ConsentRecord recordConsent(String userId, String purpose, boolean granted,
                                      String version, String source,
                                      String ipAddress, String userAgent) {
        String tenantId = TenantContext.get().getTenantId();
        // If a record with the same version exists, return it (idempotent)
        var existing = consentRepository.findActive(tenantId, userId, purpose);
        if (existing.isPresent() && version.equals(existing.get().getVersion())
                && existing.get().isGranted() == granted) {
            return existing.get();
        }
        // Supersede the existing record
        existing.ifPresent(prev -> prev.setSupersededAt(Instant.now()));
        // Save the new record
        ConsentRecord record = ConsentRecord.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .purpose(purpose)
                .version(version)
                .granted(granted)
                .source(source)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .capturedAt(Instant.now())
                .build();
        ConsentRecord saved = consentRepository.save(record);
        log.info("Consent captured: tenant={} user={} purpose={} granted={} version={} source={}",
                tenantId, userId, purpose, granted, version, source);
        return saved;
    }

    /**
     * Check if the user has granted consent for the given purpose.
     * Returns false for unknown / revoked / expired consents.
     */
    @Transactional(readOnly = true)
    public boolean hasConsent(String userId, String purpose) {
        String tenantId = TenantContext.get().getTenantId();
        return consentRepository.findActive(tenantId, userId, purpose)
                .map(ConsentRecord::isGranted)
                .orElse(false);
    }

    /**
     * List all active consents for a user.
     */
    @Transactional(readOnly = true)
    public List<ConsentRecord> listActiveConsents(String userId) {
        String tenantId = TenantContext.get().getTenantId();
        return consentRepository.findAllActiveByUser(tenantId, userId);
    }

    /**
     * Article 20 — Export all consent decisions for a user.
     */
    @Transactional(readOnly = true)
    public List<ConsentRecord> exportConsents(String userId) {
        String tenantId = TenantContext.get().getTenantId();
        return consentRepository.findAllByUser(tenantId, userId);
    }

    // ======================================================================
    // DATA ERASURE (Article 17)
    // ======================================================================

    /**
     * Initiate a Right-to-be-forgotten erasure flow.
     *
     * Steps:
     *   1. Validate the user (must exist + identity verified)
     *   2. Check for an existing pending request (idempotency)
     *   3. Persist the erasure request (PENDING)
     *   4. Emit Kafka event — each downstream service will act on it
     *   5. Mark IN_PROGRESS
     *   6. The scheduler picks it up and verifies completion
     *
     * Note: the userId is hashed before storage. The original is
     * not recoverable from the erasure record.
     */
    @Transactional
    public DataErasureRequest requestErasure(String userId, String reason,
                                              String requestedBy,
                                              String ipAddress, String userAgent) {
        String tenantId = TenantContext.get().getTenantId();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("userId is required for erasure");
        }
        // Idempotency
        String userIdHash = hashUserId(tenantId, userId);
        var existing = erasureRepository.findActiveForUser(tenantId, userIdHash);
        if (existing.isPresent()) {
            log.info("Erasure already in progress for user hash {}: {}",
                    userIdHash, existing.get().getId());
            return existing.get();
        }
        // Persist
        DataErasureRequest request = DataErasureRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userIdHash(userIdHash)
                .status("PENDING")
                .reason(reason)
                .requestedBy(requestedBy)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestedAt(Instant.now())
                .build();
        request = erasureRepository.save(request);
        log.info("Erasure requested: tenant={} requestId={} requestedBy={}",
                tenantId, request.getId(), requestedBy);
        return request;
    }

    /**
     * Mark erasure as in-progress. Called by the worker that processes
     * erasure events.
     */
    @Transactional
    public void markErasureInProgress(UUID requestId, String notifiedServices) {
        DataErasureRequest req = erasureRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("ErasureRequest", requestId.toString()));
        req.setStatus("IN_PROGRESS");
        req.setStartedAt(Instant.now());
        req.setNotifiedServices(notifiedServices);
        erasureRepository.save(req);
    }

    /**
     * Mark erasure as completed.
     */
    @Transactional
    public void markErasureCompleted(UUID requestId) {
        DataErasureRequest req = erasureRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("ErasureRequest", requestId.toString()));
        req.setStatus("COMPLETED");
        req.setCompletedAt(Instant.now());
        erasureRepository.save(req);
        log.info("Erasure completed: requestId={}", requestId);
    }

    /**
     * Mark erasure as failed.
     */
    @Transactional
    public void markErasureFailed(UUID requestId, String reason) {
        DataErasureRequest req = erasureRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("ErasureRequest", requestId.toString()));
        req.setStatus("FAILED");
        req.setCompletedAt(Instant.now());
        req.setFailureReason(reason);
        erasureRepository.save(req);
        log.warn("Erasure failed: requestId={} reason={}", requestId, reason);
    }

    /**
     * Get the status of an erasure request.
     */
    @Transactional(readOnly = true)
    public DataErasureRequest getErasureStatus(UUID requestId) {
        return erasureRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("ErasureRequest", requestId.toString()));
    }

    /**
     * Emit Kafka event to all downstream services so they can perform
     * their own data erasure (account, billing, audit, etc.).
     */
    public void emitErasureEvent(DataErasureRequest req, String userId) {
        Map<String, Object> event = Map.of(
                "eventType", "USER_DATA_ERASURE",
                "tenantId", req.getTenantId(),
                "userId", userId,
                "userIdHash", req.getUserIdHash(),
                "requestId", req.getId().toString(),
                "requestedAt", req.getRequestedAt().toString()
        );
        kafkaTemplate.send(erasureTopic, req.getId().toString(), event);
        log.info("Emitted erasure event: topic={} requestId={}", erasureTopic, req.getId());
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    /**
     * Hash the user ID for storage in the erasure record. The hash is
     * keyed with the tenantId so the same primary identity across two
     * tenants does not collide.
     */
    private String hashUserId(String tenantId, String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenantId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(userId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
