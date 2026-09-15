package com.selfcare.admin.seed;

import com.selfcare.admin.domain.AdminUser;
import com.selfcare.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Seeds the default SUPER_ADMIN user on dev profile startup.
 *
 * In production, all admin users are created via the selfcare Studio
 * admin portal — never auto-seeded. This seeder exists ONLY to give
 * developers immediate access to the admin portal without manual setup.
 *
 * Credentials (DEV ONLY):
 *   email:    admin@selfcare.io
 *   password: Selfcare_Adm1n_P0rt4l_Pa55w0rd!2026 (reset on first production login)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder {

    private static final String DEV_EMAIL = "admin@selfcare.io";
    private static final String DEV_PASSWORD_PLAINTEXT = "Selfcare_Adm1n_P0rt4l_Pa55w0rd!2026";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDevAdminUser() {
        if (!"dev".equalsIgnoreCase(activeProfile) && !"docker".equalsIgnoreCase(activeProfile)) {
            log.debug("Skipping admin user seed — profile is '{}', not dev/docker", activeProfile);
            return;
        }

        if (adminUserRepository.findByEmail(DEV_EMAIL).isPresent()) {
            log.info("Admin user {} already exists — skipping seed", DEV_EMAIL);
            return;
        }

        AdminUser adminUser = AdminUser.builder()
                .id(UUID.randomUUID().toString())
                .email(DEV_EMAIL)
                .passwordHash(passwordEncoder.encode(DEV_PASSWORD_PLAINTEXT))
                .fullName("selfcare Admin")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .mfaEnabled(false)
                .failedLoginCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        adminUserRepository.save(adminUser);
        log.info("Seeded SUPER_ADMIN user: {} / {} (DEV ONLY — DO NOT USE IN PRODUCTION)", DEV_EMAIL, DEV_PASSWORD_PLAINTEXT);
    }
}
