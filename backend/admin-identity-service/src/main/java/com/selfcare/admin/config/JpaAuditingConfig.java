package com.selfcare.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so {@code @CreatedDate} / {@code @LastModifiedDate}
 * fields on entities (e.g. {@link com.selfcare.admin.domain.AdminSession})
 * are populated automatically by the AuditingEntityListener.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}