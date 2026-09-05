package com.omobio.platform.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;

/**
 * Resource server security configuration.
 *
 * Validates JWTs issued by customer-identity-service or admin-identity-service.
 * Uses JWKS endpoint for public key rotation.
 *
 * Tenant, user, and session are extracted from JWT claims and pushed into TenantContext
 * by JwtAuthenticationConverter.
 *
 * Configuration:
 *   omobio.security.jwt.issuer=https://customer-identity.example.com
 *   omobio.security.jwt.jwks-uri=https://customer-identity.example.com/.well-known/jwks.json
 *   omobio.security.jwt.audience=omobio-selfcare-platform
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceServerSecurityConfig {

    @Value("${omobio.security.jwt.jwks-uri:}")
    private String jwksUri;

    @Value("${omobio.security.jwt.issuer:}")
    private String expectedIssuer;

    @Value("${omobio.security.jwt.audience:omobio-selfcare-platform}")
    private String expectedAudience;

    @Value("${omobio.security.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring resource server security with JWKS: {}", jwksUri);
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(resourceServerCorsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/actuator/**",
                    "/health/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyAuthority("SCOPE_admin", "ROLE_admin")
                .requestMatchers("/api/v1/customer/**").hasAnyAuthority("SCOPE_customer", "ROLE_customer")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean("resourceServerJwtDecoder")
    public JwtDecoder jwtDecoder() {
        if (jwksUri == null || jwksUri.isBlank()) {
            log.warn("No JWKS URI configured, JWT validation will not be available");
            return token -> {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("JWKS URI not configured");
            };
        }
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    @Bean
    public CorsConfigurationSource resourceServerCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Correlation-Id", "X-Tenant-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
