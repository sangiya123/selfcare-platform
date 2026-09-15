package com.selfcare.identity.config;

import com.selfcare.platform.common.security.JwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for Customer Identity Service.
 *
 * <p>This service both issues and validates JWTs. Public endpoints (OTP generate,
 * JWKS) are open. Protected endpoints (session listing, signout) require a valid
 * JWT. The JWT is validated using the service's own JWKS endpoint (self-issued).
 *
 * <p>Public endpoints (no auth required):
 * <ul>
 *   <li>POST /api/v1/auth/otp — OTP generation
 *   <li>POST /api/v1/auth/otp/verify — OTP verification
 *   <li>POST /api/v1/auth/cid/exchange — CID authorization-code login
 *   <li>POST /api/v1/auth/refresh — Token refresh
 *   <li>GET  /.well-known/jwks.json — JWKS (clients need this for validation)
 *   <li>GET  /.well-known/openid-configuration
 *   <li>/actuator/health, /v3/api-docs/**, /swagger-ui/**
 * </ul>
 *
 * <p>Protected endpoints (JWT required):
 * <ul>
 *   <li>GET  /api/v1/auth/sessions — list active sessions
 *   <li>DELETE /api/v1/auth/sessions/{id} — remote logout
 *   <li>POST /api/v1/auth/signout — sign out current session
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class CustomerIdentitySecurityConfig {

    @Value("${selfcare.security.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * Security filter chain for public auth endpoints.
     * Open to all, no JWT required.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicAuthFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/.well-known/**",
                             "/api/v1/auth/otp/**",
                             "/api/v1/auth/refresh",
                             "/api/v1/auth/cid/**",
                             "/actuator/health",
                             "/v3/api-docs/**",
                             "/swagger-ui/**",
                             "/swagger-ui.html")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );
        return http.build();
    }

    /**
     * Security filter chain for protected auth endpoints.
     * Requires a valid JWT issued by this service.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain protectedAuthFilterChain(HttpSecurity http,
                                                        JwtDecoder jwtDecoder) throws Exception {
        http
            .securityMatcher("/api/v1/auth/sessions/**",
                             "/api/v1/auth/signout")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(new JwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    @Bean
    @Primary
    public JwtDecoder selfJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwksUri,
            @Value("${selfcare.security.jwt.base-url:http://localhost:8081}") String baseUrl) {
        // If no external JWKS URI is configured, fall back to self (local discovery)
        String uri = (jwksUri != null && !jwksUri.isBlank())
                ? jwksUri
                : "/.well-known/jwks.json";

        log.info("JWT decoder using JWKS: {}{}", baseUrl, uri);
        // Use JWK set URI relative to this service
        return NimbusJwtDecoder
                .withJwkSetUri(baseUrl + uri)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "X-Correlation-Id", "X-Tenant-Id", "Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
