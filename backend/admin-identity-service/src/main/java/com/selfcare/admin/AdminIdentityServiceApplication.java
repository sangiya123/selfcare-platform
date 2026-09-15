package com.selfcare.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Admin Identity Service — Internal admin authentication.
 *
 * Supports:
 * - SAML 2.0 SSO
 * - MFA (TOTP, SMS, hardware key)
 * - RBAC (Role-Based Access Control)
 * - Audit trail integration
 * - Tenant-scoped admin users
 *
 * Architecture:
 *   selfcare Studio -> Admin Identity Service -> SAML Provider (or local)
 *                                  |
 *                                  +-> Admin user DB (MySQL)
 *                                  +-> MFA secrets (encrypted)
 *                                  +-> Role/permission registry
 *                                  +-> Audit event publisher (Kafka)
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.selfcare.admin",
    "com.selfcare.platform.common"
})
public class AdminIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminIdentityServiceApplication.class, args);
    }
}