package com.omobio.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.account.service.AccountService;
import com.omobio.account.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kafka event listener for account and connection changes.
 *
 * Listens to:
 * - account.profile.updated  — primary identity / display name / email / status changed
 * - account.connection.changed — connection linked or unlinked, status changed, primary switched
 *
 * On each event:
 * 1. Parse the payload (JSON).
 * 2. Update MySQL via AccountService.
 * 3. Invalidate the Redis linked-list cache via EntitlementService.
 *
 * Failure handling:
 * - Per-event errors are logged but do not stop consumption (manual ack after processing).
 * - Use Kafka DLT (dead-letter topic) configuration on the consumer factory for poison messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final AccountService accountService;
    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;

    /**
     * Handle account.profile.updated events.
     *
     * Expected payload:
     * {
     *   "tenantId": "dialog",
     *   "accountId": "...",
     *   "primaryIdentity": "...",
     *   "displayName": "...",
     *   "email": "...",
     *   "status": "ACTIVE",
     *   "profileVersion": 17
     * }
     */
    @KafkaListener(
            topics = "${omobio.kafka.topics.account-profile-updated:account.profile.updated}",
            groupId = "${spring.kafka.consumer.group-id:account-entitlement-service}"
    )
    public void onAccountProfileUpdated(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String tenantId = textOrNull(event, "tenantId");
            String accountId = textOrNull(event, "accountId");

            if (accountId == null) {
                log.warn("account.profile.updated missing accountId, skipping: {}", payload);
                return;
            }

            Long profileVersion = event.has("profileVersion") && !event.get("profileVersion").isNull()
                    ? event.get("profileVersion").asLong()
                    : null;

            accountService.updateProfileVersion(accountId, profileVersion);
            entitlementService.invalidateLinkedListCache(accountId);

            log.info("Processed account.profile.updated: tenant={}, accountId={}, version={}",
                    tenantId, accountId, profileVersion);

        } catch (Exception e) {
            log.error("Failed to process account.profile.updated event: {}", payload, e);
            throw new RuntimeException("Profile update event processing failed", e);
        }
    }

    /**
     * Handle account.connection.changed events.
     *
     * Expected payload (one of the following shapes):
     *
     * 1. Link / unlink:
     * {
     *   "tenantId": "dialog",
     *   "accountId": "...",
     *   "changeType": "LINKED" | "UNLINKED",
     *   "connectionId": "...",
     *   "number": "..."
     * }
     *
     * 2. Primary switched:
     * {
     *   "tenantId": "dialog",
     *   "accountId": "...",
     *   "changeType": "PRIMARY_SWITCHED",
     *   "newPrimaryConnectionId": "..."
     * }
     *
     * 3. Connection updated (status / type / attributes):
     * {
     *   "tenantId": "dialog",
     *   "accountId": "...",
     *   "changeType": "UPDATED",
     *   "connectionId": "...",
     *   "status": "ACTIVE"
     * }
     */
    @KafkaListener(
            topics = "${omobio.kafka.topics.account-connection-changed:account.connection.changed}",
            groupId = "${spring.kafka.consumer.group-id:account-entitlement-service}"
    )
    public void onAccountConnectionChanged(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String tenantId = textOrNull(event, "tenantId");
            String accountId = textOrNull(event, "accountId");
            String changeType = textOrNull(event, "changeType");

            if (accountId == null) {
                log.warn("account.connection.changed missing accountId, skipping: {}", payload);
                return;
            }

            // Invalidate the linked-list cache regardless of the change type.
            entitlementService.invalidateLinkedListCache(accountId);

            log.info("Processed account.connection.changed: tenant={}, accountId={}, changeType={}",
                    tenantId, accountId, changeType);

        } catch (Exception e) {
            log.error("Failed to process account.connection.changed event: {}", payload, e);
            throw new RuntimeException("Connection change event processing failed", e);
        }
    }

    /**
     * Extract a text field, returning null if missing or null.
     */
    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }
}
