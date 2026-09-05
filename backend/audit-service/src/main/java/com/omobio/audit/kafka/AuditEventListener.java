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
 * Kafka listener that captures audit-worthy domain events from other services
 * and writes them to the audit trail.
 *
 * Topics consumed:
 * - payment.events      — payment state transitions
 * - account.changed     — account/connection changes
 * - config.published    — configuration publish/rollback
 * - identity.events    — login, logout, session events
 *
 * Each record is expected to be a JSON object with at minimum:
 *   { "tenantId": "...", "action": "...", "resourceType": "...", "occurredAt": "..." }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for payment events and record them as audit events.
     */
    @KafkaListener(
            topics = "payment.events",
            groupId = "${spring.kafka.consumer.group-id:audit-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = buildEvent(json, "PAYMENT");
            event.setAction(safeText(json, "eventType", "PAYMENT_EVENT"));
            event.setResourceType("PAYMENT");
            event.setResourceId(safeText(json, "paymentId", null));
            event.setBeforeState(safeJson(json, "beforeState"));
            event.setAfterState(safeJson(json, "afterState"));
            event.setSeverity(safeText(json, "outcome", "SUCCESS"));
            auditService.record(event);
            log.debug("Recorded payment audit event: action={}", event.getAction());
        } catch (Exception e) {
            log.error("Failed to process payment event from Kafka: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    /**
     * Listen for account/connection change events.
     */
    @KafkaListener(
            topics = "account.changed",
            groupId = "${spring.kafka.consumer.group-id:audit-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAccountChanged(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = buildEvent(json, "ACCOUNT");
            event.setAction(safeText(json, "eventType", "ACCOUNT_CHANGED"));
            event.setResourceType("ACCOUNT");
            event.setResourceId(safeText(json, "accountId", null));
            event.setUserId(safeText(json, "affectedUserId", event.getUserId()));
            event.setBeforeState(safeJson(json, "beforeState"));
            event.setAfterState(safeJson(json, "afterState"));
            auditService.record(event);
            log.debug("Recorded account audit event: action={}", event.getAction());
        } catch (Exception e) {
            log.error("Failed to process account.changed event: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    /**
     * Listen for configuration publish/rollback events.
     */
    @KafkaListener(
            topics = "config.published",
            groupId = "${spring.kafka.consumer.group-id:audit-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onConfigPublished(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = buildEvent(json, "CONFIG");
            event.setAction(safeText(json, "eventType", "CONFIG_PUBLISHED"));
            event.setResourceType("CONFIG");
            event.setResourceId(safeText(json, "configId", null));
            event.setSeverity("SUCCESS");
            event.setBeforeState(safeJson(json, "beforeVersion"));
            event.setAfterState(safeJson(json, "afterVersion"));
            auditService.record(event);
            log.debug("Recorded config audit event: action={}", event.getAction());
        } catch (Exception e) {
            log.error("Failed to process config.published event: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    /**
     * Listen for identity events (login, logout, MFA, etc.).
     */
    @KafkaListener(
            topics = "identity.events",
            groupId = "${spring.kafka.consumer.group-id:audit-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onIdentityEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            AuditEvent event = buildEvent(json, "IDENTITY");
            event.setAction(safeText(json, "eventType", "IDENTITY_EVENT"));
            event.setResourceType("SESSION");
            event.setResourceId(safeText(json, "sessionId", null));
            event.setUserId(safeText(json, "userId", event.getUserId()));
            event.setSeverity(safeText(json, "outcome", "INFO"));
            auditService.record(event);
            log.debug("Recorded identity audit event: action={}", event.getAction());
        } catch (Exception e) {
            log.error("Failed to process identity.events event: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build the base AuditEvent from a JSON record, extracting common fields.
     */
    private AuditEvent buildEvent(JsonNode json, String defaultResourceType) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(safeText(json, "tenantId", "UNKNOWN"))
                .userId(safeText(json, "userId", "system"))
                .sessionId(safeText(json, "sessionId", null))
                .correlationId(safeText(json, "correlationId", null))
                .ipAddress(safeText(json, "ipAddress", null))
                .userAgent(safeText(json, "userAgent", null))
                .occurredAt(safeInstant(json, "occurredAt"))
                .recordedAt(Instant.now())
                .build();
    }

    private String safeText(JsonNode json, String field, String defaultValue) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : defaultValue;
    }

    private String safeJson(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
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
}
