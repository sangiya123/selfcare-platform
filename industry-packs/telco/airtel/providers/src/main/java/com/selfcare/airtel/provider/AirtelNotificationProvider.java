package com.selfcare.airtel.provider;

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
 * Airtel Notification Provider — sends SMS via Airtel SMSC.
 *
 * <p>Implements the {@link NotificationChannelProvider} contract for the
 * {@code SMS} channel. Airtel uses the {@code /v1/osp/sms/send} endpoint
 * with OAuth2-bearer-token authentication.</p>
 *
 * <p>Per-tenant config (gateway base URL, client credentials, sender ID)
 * loaded from MongoDB via {@link AirtelHttpClient}. Configure via Selfcare
 * Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = NotificationChannelProvider.class)
@RequiredArgsConstructor
public class AirtelNotificationProvider implements ApiAdapter, NotificationChannelProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_GATEWAY";
    private static final String ADAPTER_ID = "airtel-lk";
    private static final String DEFAULT_SENDER_ID = "AIRTLK";

    private final AirtelHttpClient httpClient;

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
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return SendResult.builder().success(false)
                    .failureReason("No Airtel Gateway integration configured for tenant").build();
        }

        String messageId = UUID.randomUUID().toString();
        String senderId = cfg.senderId() != null ? cfg.senderId() : DEFAULT_SENDER_ID;

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", messageId);
        payload.put("sender", senderId);
        payload.put("destination", recipient);
        payload.put("text", body != null ? body : "");
        if (subject != null) payload.put("subject", subject);
        if (metadata != null && metadata.get("priority") != null) {
            payload.put("priority", metadata.get("priority"));
        }

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/sms/send", payload, token);
            boolean success = resp != null;
            return SendResult.builder()
                    .success(success)
                    .providerMessageId(success ? messageId : null)
                    .providerName("AirtelSMSC")
                    .failureReason(success ? null : "Empty response from Airtel SMSC")
                    .build();
        } catch (AirtelApiException e) {
            log.error("Airtel SMS send failed for {}: {}", recipient, e.getMessage());
            return SendResult.builder()
                    .success(false)
                    .failureReason("Airtel SMSC error: " + e.getMessage())
                    .build();
        }
    }
}
