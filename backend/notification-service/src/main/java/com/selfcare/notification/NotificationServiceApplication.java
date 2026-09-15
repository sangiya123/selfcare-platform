package com.selfcare.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service — Push, SMS, Email with pluggable providers.
 *
 * Capabilities:
 * - Multi-channel delivery (PUSH, SMS, EMAIL, IN_APP)
 * - Pluggable providers per operator and per channel
 * - Template management (locale-aware)
 * - Delivery tracking
 * - Rate limiting per user/channel
 * - Opt-out management
 *
 * Triggered by:
 * - Kafka events (payment completed, bill issued, usage threshold, etc.)
 * - Direct API calls
 * - Scheduled jobs (low-balance reminders, etc.)
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
@ComponentScan(basePackages = {
    "com.selfcare"
})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}