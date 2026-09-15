package com.selfcare.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Customer Identity Service
 *
 * Public customer authentication product. Supports multiple login methods:
 * - OTP (SMS) — primary for telco selfcare
 * - Password — fallback
 * - OIDC/OAuth2 — federated identity
 * - Biometric re-entry
 * - SIM/header-enrichment — operator-specific
 * - Custom provider — operator-pluggable
 *
 * Architecture:
 *   Client -> API Gateway -> Customer Identity Service
 *                            |
 *                            +-> AuthProviderRegistry (per-tenant impl)
 *                            +-> SessionStore (Redis hot cache)
 *                            +-> SessionRepository (MySQL durable)
 *                            +-> JwtService (RS256)
 *                            +-> Kafka (login events)
 *
 * Token policy (ADR-011): access token + refresh session.
 *   - Access token: short-lived, bound to session/device
 *   - Refresh session: longer-lived, durable, revocable
 *   - Token rotation with replay detection
 */
@SpringBootApplication
@EnableAsync
@EnableKafka
@ComponentScan(basePackages = {
    "com.selfcare.identity",
    "com.selfcare.platform.common"
})
public class CustomerIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerIdentityServiceApplication.class, args);
    }
}