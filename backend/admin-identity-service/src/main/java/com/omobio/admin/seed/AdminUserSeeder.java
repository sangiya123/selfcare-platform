package com.omobio.admin.seed;

import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminUserRepository;
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
 * In production, all admin users are created via the Selfcare Studio
 * admin portal — never auto-seeded. This seeder exists ONLY to give
 * developers immediate access to the admin portal without manual setup.
 *
 * Credentials (DEV ONLY):
 *   email:    admin@omobio.io
 *   password: admin123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder {

    private static final String DEV_EMAIL = "admin@omobio.io";
    private static final String DEV_PASSWORD_PLAINTEXT = "admin123";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDevAdminUser() {
        if (!"dev".equalsIgnoreCase(activeProfile)) {
            log.debug("Skipping admin user seed — profile is '{}', not dev", activeProfile);
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
                .fullName("OMOBIO Admin")
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
