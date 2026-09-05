package com.omobio.platform.common.adapter;

import com.omobio.platform.common.adapter.ApiAdapter;

import java.util.Map;

/**
 * Provider interface for notification channels.
 *
 * Each operator implements providers for one or more channels:
 * PUSH (FCM/APNS), SMS (Dialog SMSC, etc.), EMAIL, IN_APP.
 */
public interface NotificationChannelProvider extends ApiAdapter {

    /**
     * Get the channel this provider handles.
     */
    Channel channel();

    /**
     * Send a notification.
     *
     * @param recipient Target (device token, phone number, email, userId)
     * @param subject   Subject (for EMAIL)
     * @param body      Body content (may be template-rendered)
     * @param metadata  Channel-specific metadata (locale, priority, ttl, etc.)
     * @return Send result with provider message ID
     */
    SendResult send(String recipient, String subject, String body, Map<String, String> metadata);

    enum Channel {
        PUSH,
        SMS,
        EMAIL,
        IN_APP
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SendResult {
        private boolean success;
        private String providerMessageId;
        private String failureReason;
        private String providerName;
    }
}