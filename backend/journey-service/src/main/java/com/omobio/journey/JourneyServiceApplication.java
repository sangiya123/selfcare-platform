package com.omobio.journey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Journey Service — Configurable multi-step journey runtime.
 *
 * Step types (per planning docs):
 *   SCREEN, API, CONDITION, CONFIRMATION, OTP/STEP_UP, PAYMENT, DOCUMENT,
 *   WAIT/POLL, WEB_SSO, SUCCESS/ERROR
 *
 * Features:
 * - Schema-validated step transitions
 * - Secure steps call deterministic backend services
 * - Resumable journeys
 * - Compensation/rollback
 * - Analytics events
 * - Test simulator
 * - No arbitrary code execution (only registered steps)
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.omobio.journey",
    "com.omobio.platform.common"
})
public class JourneyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JourneyServiceApplication.class, args);
    }
}