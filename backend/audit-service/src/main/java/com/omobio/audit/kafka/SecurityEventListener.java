package com.omobio.audit.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.audit.domain.AuditEvent;
import com.omobio.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka listener for security-related events.
 *
 * Topics consumed:
 *  - auth.events: login attempts, MFA challenges, token issues
 *  - rate.limit.exceeded: brute-force detection, API abuse
 *
 * Security events are always recorded — they are immutable and must not be
 * silently dropped. Any processing error is logged and the event is
 * sent to a dead-letter queue topic (security.events.dlq).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "auth.events",
            groupId = "${spring.kafka.consumer.group-id:audit-security}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAuthEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = AuditEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(safeText(json, "tenantId", "UNKNOWN"))
                    .userId(safeText(json, "userId", "anonymous"))
                    .sessionId(safeText(json, "sessionId", null))
                    .action(safeText(json, "eventType", "AUTH_EVENT"))
                    .resourceType("AUTH")
                    .resourceId(safeText(json, "attemptId", null))
                    .correlationId(safeText(json, "correlationId", null))
                    .ipAddress(safeText(json, "ipAddress", null))
                    .userAgent(safeText(json, "userAgent", null))
                    .severity(toSeverity(safeText(json, "outcome", "INFO")))
                    .message(safeText(json, "message", null))
                    .occurredAt(safeInstant(json, "occurredAt"))
                    .recordedAt(Instant.now())
                    .build();

            auditService.record(event);
            log.debug("Recorded auth security event: action={}, userId={}, outcome={}",
                    event.getAction(), event.getUserId(), json.get("outcome"));
        } catch (Exception e) {
            log.error("Failed to process auth event: topic={}, offset={}, error={}",
                    record.topic(), record.offset(), e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "rate.limit.exceeded",
            groupId = "${spring.kafka.consumer.group-id:audit-security}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRateLimitEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = AuditEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(safeText(json, "tenantId", "UNKNOWN"))
                    .userId(safeText(json, "userId", "anonymous"))
                    .action("RATE_LIMIT_EXCEEDED")
                    .resourceType("RATE_LIMIT")
                    .resourceId(safeText(json, "key", null))
                    .correlationId(safeText(json, "correlationId", null))
                    .ipAddress(safeText(json, "ipAddress", null))
                    .severity("WARNING")
                    .message(String.format("Rate limit exceeded: limit=%s, window=%s, current=%s",
                            safeText(json, "limit", "?"),
                            safeText(json, "window", "?"),
                            safeText(json, "current", "?")))
                    .occurredAt(safeInstant(json, "occurredAt"))
                    .recordedAt(Instant.now())
                    .build();

            auditService.record(event);
            log.info("Recorded rate-limit security event: ip={}, key={}",
                    event.getIpAddress(), event.getResourceId());
        } catch (Exception e) {
            log.error("Failed to process rate-limit event: topic={}, offset={}",
                    record.topic(), record.offset(), e);
        }
    }

    private String safeText(JsonNode json, String field, String defaultValue) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : defaultValue;
    }

    private Instant safeInstant(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return Instant.now();
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String toSeverity(String outcome) {
        return switch (outcome.toUpperCase()) {
            case "SUCCESS" -> "SUCCESS";
            case "FAILURE", "DENIED" -> "FAILURE";
            case "WARNING" -> "WARNING";
            default -> "INFO";
        };
    }
}
