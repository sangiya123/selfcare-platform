package com.selfcare.config.seed;

import com.selfcare.platform.common.config.ClientIntegrationConfig;
import com.selfcare.platform.common.config.ClientIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the client_integrations collection with dev fixtures on
 * application startup. Runs only in the dev profile.
 * Each tenant × integration is seeded independently — missing entries are
 * added without affecting existing ones.
 *
 * In production, integrations are created via the selfcare Studio
 * admin portal — never auto-seeded.
 *
 * Coverage:
 *  - Telco industry pack: Dialog, Hutch, Airtel
 *  - Insurance industry pack: AIA in 6 markets
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientIntegrationSeeder {

    private final ClientIntegrationRepository repository;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final Instant now = Instant.now();

    @EventListener(ApplicationReadyEvent.class)
    public void seedDevData() {
        if (!"dev".equalsIgnoreCase(activeProfile)) {
            log.debug("Skipping integration seed — profile is {}, not dev", activeProfile);
            return;
        }
        int total = 0;

        // ============================================================
        // TELCO INDUSTRY PACK — Dialog (Sri Lanka)
        // ============================================================
        total += seedIfAbsent("dialog-lk", "TELCO", "DIALOG_MIFE",
                "com.selfcare.dialog.provider.DialogAuthProvider",
                "https://mife-mock.selfcare.io/api/v1",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "dev-client", "clientSecret", "dev-secret"));
        total += seedIfAbsent("dialog-lk", "TELCO", "DIALOG_BSS",
                "com.selfcare.dialog.provider.DialogBalanceProvider",
                "https://bss-mock.selfcare.io/api",
                "API_KEY",
                Map.of("apiKey", "dev-key"),
                Map.of(
                        "capabilities", "getBalance,getUsage,getBill,getConnection,getCreditLimit",
                        "lob", "GSM",
                        "path.credit", "/subscriber/credit/{connection}",
                        "field.currency", "currency",
                        "field.creditLimit", "creditLimit",
                        "field.usedCredit", "usedCredit",
                        "field.availableCredit", "availableCredit"));
        total += seedIfAbsent("dialog-lk", "TELCO", "DIALOG_SMSC",
                "com.selfcare.dialog.provider.DialogNotificationProvider",
                "https://smsc-mock.selfcare.io/api",
                "API_KEY",
                Map.of("apiKey", "dev-key", "senderId", "selfcare"));
        total += seedIfAbsent("dialog-lk", "TELCO", "DIALOG_CATALOG",
                "com.selfcare.dialog.provider.DialogProductCatalogProvider",
                "https://vas-mock.selfcare.io/api",
                "API_KEY",
                Map.of("apiKey", "dev-key"));

        // ============================================================
        // TELCO INDUSTRY PACK — Hutch
        // ============================================================
        total += seedIfAbsent("hutch-lk", "TELCO", "HUTCH_BSS",
                "com.selfcare.hutch.provider.HutchAuthProvider",
                "https://hutch-bss-mock.selfcare.io/api",
                "API_KEY",
                Map.of("apiKey", "dev-key"));

        // ============================================================
        // TELCO INDUSTRY PACK — Airtel
        // ============================================================
        total += seedIfAbsent("airtel-lk", "TELCO", "AIRTEL_GATEWAY",
                "com.selfcare.airtel.provider.AirtelAuthProvider",
                "https://airtel-gw-mock.selfcare.io/v1",
                "API_KEY",
                Map.of("clientId", "dev-client", "clientSecret", "dev-secret"));

        // ============================================================
        // INSURANCE INDUSTRY PACK — AIA markets
        // ============================================================
        // Same AIAInsuranceProvider class, per-country tenant configs.
        // Adding a new country = appending 2 lines here. No new code.
        total += seedIfAbsent("aia-lk", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-lk-dev", "clientSecret", "aia-lk-secret", "apiKey", "aia-lk-key"));
        total += seedIfAbsent("aia-sg", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-sg-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-sg-dev", "clientSecret", "aia-sg-secret", "apiKey", "aia-sg-key"));
        total += seedIfAbsent("aia-th", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-th-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-th-dev", "clientSecret", "aia-th-secret", "apiKey", "aia-th-key"));
        total += seedIfAbsent("aia-my", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-my-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-my-dev", "clientSecret", "aia-my-secret", "apiKey", "aia-my-key"));
        total += seedIfAbsent("aia-hk", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-hk-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-hk-dev", "clientSecret", "aia-hk-secret", "apiKey", "aia-hk-key"));
        total += seedIfAbsent("aia-in", "INSURANCE", "AIA_INSURANCE",
                "com.selfcare.aia.provider.AIAInsuranceProvider",
                "https://aia-in-mock.selfcare.io/api",
                "OAUTH2_CLIENT_CREDENTIALS",
                Map.of("clientId", "aia-in-dev", "clientSecret", "aia-in-secret", "apiKey", "aia-in-key"));

        log.info("Seeded {} dev client integrations (telco + insurance)", total);
    }

    private int seedIfAbsent(String tenantId, String industry, String integrationType, String providerClass,
                             String baseUrl, String authType, Map<String, String> credentials) {
        return seedIfAbsent(tenantId, industry, integrationType, providerClass,
                baseUrl, authType, credentials, null);
    }

    private int seedIfAbsent(String tenantId, String industry, String integrationType, String providerClass,
                             String baseUrl, String authType, Map<String, String> credentials,
                             Map<String, String> metadata) {
        if (repository.findByTenantIdAndIntegrationType(tenantId, integrationType).isEmpty()) {
            repository.save(ClientIntegrationConfig.builder()
                    .tenantId(tenantId)
                    .industry(industry)
                    .integrationType(integrationType)
                    .providerClass(providerClass)
                    .baseUrl(baseUrl)
                    .authType(authType)
                    .credentials(new HashMap<>(credentials))
                    .metadata(metadata != null ? new HashMap<>(metadata) : null)
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            log.info("  Seeded {}/{}/{} for tenant {}", industry, integrationType, providerClass, tenantId);
            return 1;
        }
        return 0;
    }
}
