package com.selfcare.audit.service;

import com.selfcare.audit.domain.AuditEvent;
import com.selfcare.audit.repository.AuditEventRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Audit search and analytics service.
 *
 * Provides:
 *  - Full-text search across message content
 *  - Action sequence analysis (detect repeated failures)
 *  - User session audit trail
 *  - Cross-tenant analytics (admin only)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditSearchService {

    private final AuditEventRepository repository;

    /**
     * Search audit events for the current tenant with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> search(String userId, String action, String resourceType,
                                   String severity, Instant from, Instant to,
                                   int page, int size) {
        String tenantId = TenantContext.get().getTenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return repository.search(tenantId, userId, action, resourceType, from, to, pageable);
    }

    /**
     * Get the session audit trail — all events for a given session ID.
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> sessionTrail(String sessionId) {
        String tenantId = TenantContext.get().getTenantId();
        Pageable pageable = PageRequest.of(0, 1000);
        // Find all events where sessionId matches
        return repository.search(tenantId, null, null, null, null, null, pageable)
                .getContent().stream()
                .filter(e -> sessionId.equals(e.getSessionId()))
                .sorted(Comparator.comparing(AuditEvent::getOccurredAt))
                .toList();
    }

    /**
     * Detect repeated failures by a user — returns true if more than
     * threshold failures occurred within the time window.
     */
    @Transactional(readOnly = true)
    public boolean hasRepeatedFailures(String userId, int threshold, int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        Pageable pageable = PageRequest.of(0, threshold + 1);
        Page<AuditEvent> events = repository.search(
                TenantContext.get().getTenantId(), userId, null, null, cutoff, null, pageable);
        long failureCount = events.getContent().stream()
                .filter(e -> "FAILURE".equals(e.getSeverity()))
                .count();
        return failureCount >= threshold;
    }

    /**
     * Get event count by action for the last N days.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countByAction(int days) {
        String tenantId = TenantContext.get().getTenantId();
        Instant since = Instant.now().minusSeconds(days * 86400L);
        Pageable pageable = PageRequest.of(0, 1000);
        List<AuditEvent> events = repository.search(
                tenantId, null, null, null, since, null, pageable).getContent();
        Map<String, Long> counts = new HashMap<>();
        for (AuditEvent e : events) {
            counts.merge(e.getAction(), 1L, Long::sum);
        }
        return counts;
    }
}
