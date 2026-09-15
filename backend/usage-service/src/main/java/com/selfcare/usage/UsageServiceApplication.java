package com.selfcare.usage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Usage Service — balance, usage, allowance.
 *
 *  - Reads balance/usage live from per-tenant BalanceProvider adapters
 *  - Caches in Redis (TTL configurable)
 *  - Persists snapshots to MySQL via UsageRecord (history view, fallback)
 *  - Manages Allowances (data/voice/SMS buckets with expiry)
 *  - Listens to usage/recharge/payment Kafka events
 *  - Daily rollup job expires allowances and prunes old history
 */
@SpringBootApplication(scanBasePackages = {
    "com.selfcare"
})
@EnableJpaRepositories(basePackages = "com.selfcare.usage.repository")
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class UsageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UsageServiceApplication.class, args);
    }
}
