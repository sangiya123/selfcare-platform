package com.selfcare.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for the admin-identity-service.
 *
 * Provides a builder used by {@link com.selfcare.admin.service.ApprovalClient}
 * to call the approval-service via Spring Cloud LoadBalancer (service
 * discovery with "lb://approval-service" URIs).
 *
 * Bean injection via {@code WebClient.Builder} allows shared configuration
 * (timeouts, filters) and avoids constructing a new client per call.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
