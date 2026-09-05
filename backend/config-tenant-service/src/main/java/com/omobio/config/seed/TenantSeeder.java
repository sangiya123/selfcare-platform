package com.omobio.config.seed;

import com.omobio.config.domain.TenantConfig;
import com.omobio.config.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Seeds the tenant_configs collection with dev fixtures.
 *
 * Production tenants are created via the Selfcare Studio admin portal.
 *
 * Adding a new tenant = appending one entry to the SEED_TENANTS list.
 * No code change needed to add a new client / industry / country.
 *
 * The 'industry' field binds the tenant to an industry pack (telco,
 * insurance, travel, ...) which provides the per-vertical provider
 * implementations. The 'operator' field is a free-form client identifier
 * (e.g. "dialog" for telco, "aia" for insurance).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSeeder {

    private final TenantConfigRepository repository;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final List<SeedEntry> SEED_TENANTS = List.of(
            // --- Telco clients (telco industry pack) ---
            new SeedEntry("dialog-lk", "Dialog Sri Lanka", "dialog", "TELECOM", "LK", List.of("mobile", "broadband", "tv")),
            new SeedEntry("hutch-lk", "Hutch Sri Lanka", "hutch", "TELECOM", "LK", List.of("mobile")),
            new SeedEntry("airtel-lk", "Airtel Sri Lanka", "airtel", "TELECOM", "LK", List.of("mobile")),

            // --- Insurance clients (insurance industry pack) ---
            // AIA: same provider, different country configs.
            new SeedEntry("aia-lk", "AIA Insurance Sri Lanka", "aia", "INSURANCE", "LK", List.of("life", "health", "motor", "education")),
            new SeedEntry("aia-sg", "AIA Insurance Singapore", "aia", "INSURANCE", "SG", List.of("life", "health", "critical-illness")),
            new SeedEntry("aia-th", "AIA Insurance Thailand", "aia", "INSURANCE", "TH", List.of("life", "health", "accident")),
            new SeedEntry("aia-my", "AIA Insurance Malaysia", "aia", "INSURANCE", "MY", List.of("life", "health")),
            new SeedEntry("aia-hk", "AIA Insurance Hong Kong", "aia", "INSURANCE", "HK", List.of("life", "health", "wealth", "group")),
            new SeedEntry("aia-in", "AIA Insurance India", "aia", "INSURANCE", "IN", List.of("life", "health", "retirement")),

            // Future insurance clients
            new SeedEntry("allianz-sg", "Allianz Insurance Singapore", "allianz", "INSURANCE", "SG", List.of("general", "life"))
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seedDevData() {
        if (!"dev".equalsIgnoreCase(activeProfile)) {
            log.debug("Skipping tenant seed — profile is {}, not dev", activeProfile);
            return;
        }

        int total = 0;
        for (SeedEntry e : SEED_TENANTS) {
            if (repository.findByTenantId(e.tenantId).isPresent()) continue;

            TenantConfig cfg = TenantConfig.builder()
                    .tenantId(e.tenantId)
                    .name(e.displayName)
                    .operator(e.operator)
                    .industry(e.industry)
                    .country(e.country)
                    .supportedLobs(e.supportedLobs)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            repository.save(cfg);
            total++;
            log.info("  Seeded client: {} ({}/{} · {})", e.tenantId, e.operator, e.industry, e.country);
        }
        if (total > 0) {
            log.info("Seeded {} dev clients (telco + insurance)", total);
        }
    }

    private record SeedEntry(
            String tenantId,
            String displayName,
            String operator,         // Free-form client identifier ("dialog", "aia", "allianz")
            String industry,         // TELECOM | INSURANCE | BANKING | TRAVEL
            String country,          // ISO 3166-1 alpha-2
            List<String> supportedLobs
    ) {}
}
