package com.omobio.approval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Service-wide configuration for the approval workflow.
 *
 * Properties are read from {@code omobio.approval.*} in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "omobio.approval")
public class ApprovalServiceConfig {

    /**
     * Default number of hours a PENDING request remains open before
     * the scheduled job marks it EXPIRED.
     */
    private long defaultExpiryHours = 72;

    /**
     * How often the scheduled expiry job runs, in milliseconds.
     * Default: 5 minutes (300_000 ms).
     */
    private long expiryCheckIntervalMs = 300_000L;
}
