package com.selfcare.platform.common.contract.entitlement;

import java.util.List;

/**
 * Canonical entitlement contract. Telco: linked-connection membership (ADR-006) over primary
 * identity; other industries: subscription/role via the same shapes.
 */
public final class EntitlementContract {

    private EntitlementContract() {}

    public record ConnectionSummary(
            String connectionId,
            String roleInLinkedConnections,
            String lob,
            String connType,
            String profileType,
            String status,
            boolean primary) {
    }

    public record ConnectionsResponse(
            String primaryId,
            List<ConnectionSummary> connections,
            boolean canSwitch) {
    }

    public record SwitchConnectionRequest(
            String targetConnectionId) {
    }

    public record EntitlementStatus(
            String mode,
            boolean allowed,
            List<String> allowedActions,
            String reason) {
    }

    public enum LinkedConnectionRole {
        OWNER, PRIMARY_USER, LINKED_USER, ADMIN
    }

    public interface EntitlementService {
        ConnectionsResponse getConnections(String tenantId, String userId);

        EntitlementStatus canSwitch(String tenantId, String userId, String targetConnectionId);

        EntranceResult switchConnection(SwitchConnectionRequest request);
    }

    public record EntranceResult(String requestId, boolean accepted, String status) {
    }
}