package com.omobio.dialog.provider;

import com.omobio.notification.adapter.NotificationChannelProvider;
import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dialog Notification Provider — sends SMS via Dialog SMSC.
 *
 * <p>Implements the {@link NotificationChannelProvider} contract for the
 * {@code SMS} channel. Routed by the notification service via
 * {@code ApiAdapterRegistry}.</p>
 *
 * <p>Per-tenant config (SMSC base URL, API key, sender ID) loaded from
 * MongoDB via {@link DialogHttpClient}. Configure via Selfcare Studio
 * admin: Integrations &gt; Dialog SMSC.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = NotificationChannelProvider.class)
@RequiredArgsConstructor
public class DialogNotificationProvider implements ApiAdapter, NotificationChannelProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_SMSC";
    private static final String ADAPTER_ID = "dialog-lk";
    private static final String DEFAULT_SENDER_ID = "OMOBIO";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public SendResult send(String recipient, String subject, String body, Map<String, String> metadata) {
        return send(TenantContextBridge.currentTenantId(), recipient, subject, body, metadata);
    }

    /**
     * Tenant-explicit variant — used by callers that already know the tenant.
     */
    public SendResult send(String tenantId, String recipient, String subject,
                           String body, Map<String, String> metadata) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return SendResult.builder().success(false)
                    .failureReason("No Dialog SMSC integration configured for tenant").build();
        }

        String messageId = UUID.randomUUID().toString();
        String senderId = cfg.senderId() != null ? cfg.senderId() : DEFAULT_SENDER_ID;
        String priority = metadata != null && metadata.get("priority") != null ? metadata.get("priority") : "NORMAL";
        String validity = metadata != null && metadata.get("ttl") != null ? metadata.get("ttl") : "86400";

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", messageId);
        payload.put("sourceAddr", senderId);
        payload.put("destinationAddr", recipient);
        payload.put("message", body != null ? body : "");
        payload.put("priority", priority);
        payload.put("validityPeriod", validity);
        if (subject != null) payload.put("subject", subject);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg.baseUrl(),
                    "/sms/send", payload, null, cfg.apiKey());
            boolean success = resp != null;
            return SendResult.builder()
                    .success(success)
                    .providerMessageId(success ? messageId : null)
                    .providerName("DialogSMSC")
                    .failureReason(success ? null : "Empty response from SMSC")
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog SMS send failed for {}: {}", recipient, e.getMessage());
            return SendResult.builder()
                    .success(false)
                    .failureReason("SMSC error: " + e.getMessage())
                    .build();
        }
    }
}
