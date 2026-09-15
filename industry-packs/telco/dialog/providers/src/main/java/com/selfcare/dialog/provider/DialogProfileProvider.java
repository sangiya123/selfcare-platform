package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Dialog Profile Provider — fetches subscriber profile from Dialog BSS.
 *
 * <p>Per-tenant config (BSS base URL, API key) loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 *
 * <p>The {@code @RegisterAdapter} annotation is included for
 * discoverability; the canonical profile lookup contract is
 * ad-hoc (used directly by the customer-identity service and BFFs).</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = ApiAdapter.class)
@RequiredArgsConstructor
public class DialogProfileProvider implements ApiAdapter {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    public SubscriberProfile getProfile(String connectionId) {
        return getProfile(TenantContextBridge.currentTenantId(), connectionId);
    }

    public SubscriberProfile getProfile(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("No active Dialog BSS integration for tenant={}", tenantId);
            return null;
        }
        String path = "/subscriber/profile/" + connectionId;
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return mapToProfile(data);
        } catch (DialogApiException e) {
            log.error("Dialog profile fetch failed for connection={}: {}", connectionId, e.getMessage());
            return null;
        }
    }

    private SubscriberProfile mapToProfile(Map<String, Object> data) {
        return SubscriberProfile.builder()
                .connectionId((String) data.get("subscriberId"))
                .msisdn((String) data.get("msisdn"))
                .accountNumber((String) data.get("accountNumber"))
                .accountType((String) data.get("accountType"))
                .segment((String) data.get("segment"))
                .status((String) data.get("status"))
                .name((String) data.get("subscriberName"))
                .email((String) data.get("email"))
                .dateOfBirth((String) data.get("dateOfBirth"))
                .address((String) data.get("address"))
                .city((String) data.get("city"))
                .registrationDate((String) data.get("registrationDate"))
                .simSerialNumber((String) data.get("simSerialNumber"))
                .imsi((String) data.get("imsi"))
                .tariffPlan((String) data.get("tariffPlan"))
                .build();
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class SubscriberProfile {
        private String connectionId;
        private String msisdn;
        private String accountNumber;
        private String accountType;
        private String segment;
        private String status;
        private String name;
        private String email;
        private String dateOfBirth;
        private String address;
        private String city;
        private String registrationDate;
        private String simSerialNumber;
        private String imsi;
        private String tariffPlan;
    }
}
