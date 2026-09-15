package com.selfcare.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Product Service — Product/Offer/Catalog.
 *
 * Responsibilities:
 * - Aggregate products/offers from API, MySQL, Mongo, Kafka-fed stores
 * - Normalize into canonical Product/Offer models
 * - Build materialized views/snapshots via background scheduler
 * - Personalized recommendations (deterministic + AI-augmented)
 * - Separate catalog from customer eligibility
 * - Source priority/merge rules
 * - Cache TTL management
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
@EnableJpaRepositories(basePackages = "com.selfcare.product.repository")
@EnableJpaAuditing
@ComponentScan(basePackages = {
    "com.selfcare"
})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
