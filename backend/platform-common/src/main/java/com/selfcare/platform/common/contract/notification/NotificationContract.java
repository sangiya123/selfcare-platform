package com.selfcare.platform.common.contract.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical notification contract. Channel-agnostic (push / sms / email / in-app); templating is
 * declared by templateId + payload, provider resolution is runtime adapters.
 */
public final class NotificationContract {

    private NotificationContract() {}

    public record NotificationSendRequest(
            String tenantId,
            String userId,
            String channel,
            String templateId,
            Map<String, Object> payload,
            String deepLink,
            int priority) {
    }

    public record NotificationMessage(
            String notificationId,
            String tenantId,
            String userId,
            String channel,
            String templateId,
            Map<String, Object> payload,
            NotificationState state,
            String deepLink,
            Instant createdAt) {
    }

    public record NotificationInbox(
            List<NotificationMessage> items,
            long unreadCount,
            long totalItems) {
    }

    public record MarkReadRequest(List<String> notificationIds) {
    }

    public enum NotificationState {
        DRAFT, SENT, DELIVERED, READ, FAILED
    }

    public interface NotificationService {
        String send(NotificationSendRequest request);

        NotificationInbox getInbox(String tenantId, String userId, int page, int size);

        void markRead(String tenantId, String userId, MarkReadRequest request);
    }
}