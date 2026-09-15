package com.selfcare.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Account & Entitlement Service
 *
 * Manages accounts, profiles, and linked connections. Enforces cross-connection
 * authorization (Actor + Action + Target + Context).
 *
 * ADR-006: Dialog entitlement = primary identity's linked connection list
 *   - A requested connection is authorized when it is in the currently
 *     logged-in primary mobile number's linked connection list
 *   - NOT a same-NIC ownership check
 *   - Linked list comes from operator's profile system, fed via Kafka
 *
 * Architecture:
 *   Customer Identity -> Account Session
 *   Kafka (profile.connection.changed) -> Account Service -> Update linked list
 *   Linked list cached in Redis (compact) and durable in MySQL (full)
 *   Entitlement check: is targetConnectionId in account's linked list?
 */
@SpringBootApplication
@EnableKafka
@ComponentScan(basePackages = {
    "com.selfcare.account",
    "com.selfcare.platform.common"
})
public class AccountEntitlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountEntitlementServiceApplication.class, args);
    }
}