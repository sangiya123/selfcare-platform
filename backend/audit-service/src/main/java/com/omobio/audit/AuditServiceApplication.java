package com.omobio.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Audit Service — Immutable audit trail.
 *
 * Records:
 * - Login/logout events
 * - Configuration publish/rollback
 * - Role/permission changes
 * - Payment state transitions
 * - High-risk AI actions
 * - Security events
 * - Admin exports
 *
 * Storage: append-only MySQL table (no UPDATE, no DELETE)
 * Schema includes: timestamp, actor, action, target, before/after state, IP, correlation ID
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@ComponentScan(basePackages = {
    "com.omobio.audit",
    "com.omobio.platform.common"
})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}