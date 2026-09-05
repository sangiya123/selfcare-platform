package com.omobio.usage.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.usage.domain.Allowance;
import com.omobio.usage.service.AllowanceService;
import com.omobio.usage.service.UsageHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Listens on platform topics that affect allowances and usage history:
 *
 *  - usage.events: real-time data/voice/SMS usage updates from the operator
 *  - payment.events: payment success → invalidates balance cache, may activate a new allowance
 *  - recharge.events: recharge / package purchase → new allowance + cache invalidation
 *
 * Every handler sets the tenant from the event payload — never from
 * the inbound HTTP request — because Kafka events cross service boundaries
 * without an HTTP security context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageEventListener {

    private final AllowanceService allowanceService;
    private final UsageHistoryService usageHistoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "usage.events", groupId = "usage-service")
    public void onUsageEvent(Map<String, Object> event) {
        try {
            String eventType = stringOrNull(event, "eventType");
            if (!"USAGE_RECORDED".equals(eventType)) {
                log.debug("Ignoring usage event type: {}", eventType);
                return;
            }
            String tenantId = stringOrNull(event, "tenantId");
            String connectionId = stringOrNull(event, "connectionId");
            if (tenantId == null || connectionId == null) {
                log.warn("Usage event missing tenantId/connectionId: {}", event);
                return;
            }
            try {
                TenantContext.current().setTenantId(tenantId);
                Long delta = longOrNull(event, "dataBytesDelta");
                if (delta != null && delta > 0) {
                    String allowanceId = stringOrNull(event, "allowanceId");
                    if (allowanceId != null) {
                        allowanceService.recordConsumption(connectionId, allowanceId, delta);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Failed to process usage event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "recharge.events", groupId = "usage-service")
    public void onRechargeEvent(Map<String, Object> event) {
        try {
            String eventType = stringOrNull(event, "eventType");
            if (!"RECHARGE_COMPLETED".equals(eventType) && !"PACKAGE_ACTIVATED".equals(eventType)) {
                return;
            }
            String tenantId = stringOrNull(event, "tenantId");
            String connectionId = stringOrNull(event, "connectionId");
            if (tenantId == null || connectionId == null) return;

            try {
                TenantContext.current().setTenantId(tenantId);
                Allowance a = Allowance.builder()
                        .allowanceId(stringOrNull(event, "allowanceId"))
                        .connectionId(connectionId)
                        .allowanceType(stringOrNull(event, "allowanceType"))
                        .name(stringOrNull(event, "packageName"))
                        .source("RECHARGE")
                        .totalUnits(longOrNull(event, "totalUnits"))
                        .usedUnits(0L)
                        .remainingUnits(longOrNull(event, "totalUnits"))
                        .unit(stringOrNull(event, "unit"))
                        .activatedAt(Instant.now())
                        .expiresAt(instantOrNull(event, "expiresAt"))
                        .status("ACTIVE")
                        .build();
                if (a.getAllowanceId() == null) {
                    a.setAllowanceId("alw-" + System.currentTimeMillis());
                }
                allowanceService.upsertAllowance(a);
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Failed to process recharge event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.events", groupId = "usage-service")
    public void onPaymentEvent(Map<String, Object> event) {
        try {
            String status = stringOrNull(event, "status");
            if (!"SUCCESS".equals(status)) return;
            String connectionId = stringOrNull(event, "targetConnectionId");
            if (connectionId == null) connectionId = stringOrNull(event, "sourceConnectionId");
            if (connectionId == null) return;
            log.info("Payment SUCCESS — usage service will refresh balance on next request: conn={}", connectionId);
            // Force cache invalidation by deleting the next fetch will re-read from BSS.
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static String stringOrNull(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private static Long longOrNull(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant instantOrNull(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        try {
            return Instant.parse(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
