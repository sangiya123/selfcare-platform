package com.selfcare.config.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Public config endpoints (compiled manifest serving for mobile/web apps) must be
 * reachable <b>before</b> authentication. Runs ahead of the shared
 * {@code ResourceServerSecurityConfig} chain and only intercepts {@code /api/v1/config/**}.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class ConfigPublicSecurityConfig {

    @Bean
    @Order(-1)
    public SecurityFilterChain publicConfigFilterChain(HttpSecurity http) throws Exception {
        log.info("Permitting anonymous access to /api/v1/config/** (public manifest serving)");
        http.securityMatcher("/api/v1/config/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }
}