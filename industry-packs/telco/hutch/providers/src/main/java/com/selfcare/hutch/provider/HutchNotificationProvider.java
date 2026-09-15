package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.NotificationChannelProvider;
import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hutch Notification Provider — sends SMS via Hutch SMSC.
 *
 * <p>Implements the {@link NotificationChannelProvider} contract for the
 * {@code SMS} channel. Hutch uses a slightly different API shape where
 * requests are sent to {@code /sms/send} with the sender ID in the payload.</p>
 *
 * <p>Per-tenant config (SMSC base URL, API key, sender ID) loaded from MongoDB
 * via {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch SMSC.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = NotificationChannelProvider.class)
@RequiredArgsConstructor
public class HutchNotificationProvider implements ApiAdapter, NotificationChannelProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_SMSC";
    private static final String ADAPTER_ID = "hutch-lk";
    private static final String DEFAULT_SENDER_ID = "HUTCHLK";

    private final HutchHttpClient httpClient;

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
     * Tenant-explicit variant.
     */
    public SendResult send(String tenantId, String recipient, String subject,
                          String body, Map<String, String> metadata) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            // Fall back to Hutch BSS SMS endpoint if no SMSC is configured
            cfg = httpClient.resolveConfig(tenantId, "HUTCH_BSS");
            if (!cfg.isValid()) {
                return SendResult.builder().success(false)
                        .failureReason("No Hutch SMS integration configured for tenant").build();
            }
        }

        String messageId = UUID.randomUUID().toString();
        String senderId = cfg.senderId() != null ? cfg.senderId() : DEFAULT_SENDER_ID;
        String priority = metadata != null ? metadata.getOrDefault("priority", "NORMAL") : "NORMAL";

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", messageId);
        payload.put("sender", senderId);
        payload.put("destination", recipient);
        payload.put("message", body != null ? body : "");
        payload.put("priority", priority);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/sms/send", payload, null);
            boolean success = resp != null;
            return SendResult.builder()
                    .success(success)
                    .providerMessageId(success ? messageId : null)
                    .providerName("HutchSMSC")
                    .failureReason(success ? null : "Empty response from Hutch SMSC")
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch SMS send failed for {}: {}", recipient, e.getMessage());
            return SendResult.builder()
                    .success(false)
                    .failureReason("Hutch SMSC error: " + e.getMessage())
                    .build();
        }
    }
}
