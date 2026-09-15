package com.selfcare.platform.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
 *   selfcare.security.jwt.issuer=https://customer-identity.example.com
 *   selfcare.security.jwt.jwks-uri=https://customer-identity.example.com/.well-known/jwks.json
 *   selfcare.security.jwt.audience=selfcare-platform
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResourceServerSecurityConfig {

    @Value("${selfcare.security.jwt.jwks-uri:}")
    private String jwksUri;

    @Value("${selfcare.security.jwt.admin-jwks-uri:}")
    private String adminJwksUri;

    @Value("${selfcare.security.jwt.issuer:}")
    private String expectedIssuer;

    @Value("${selfcare.security.jwt.audience:selfcare-platform}")
    private String expectedAudience;

    @Value("${selfcare.security.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${selfcare.security.dev-mode-bypass:false}")
    private boolean devModeBypass;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring resource server security with JWKS: {} (devModeBypass={})", jwksUri, devModeBypass);
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(resourceServerCorsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> {
                authz
                    .requestMatchers(
                        "/actuator/**",
                        "/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error"
                    ).permitAll();
                if (devModeBypass) {
                    log.warn("⚠ Dev mode bypass ENABLED — permitting all without auth in dev mode");
                    authz.requestMatchers("/api/v1/admin/**", "/api/v1/config/**", "/api/v1/themes/**", "/api/v1/layouts/**").permitAll();
                } else {
                    authz.requestMatchers("/api/v1/admin/**").hasAnyAuthority("SCOPE_admin", "ROLE_admin");
                    authz.requestMatchers("/api/v1/customer/**").hasAnyAuthority("SCOPE_customer", "ROLE_customer");
                }
                authz.anyRequest().authenticated();
            });
        if (!devModeBypass) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter()))
            );
        }

        return http.build();
    }

    @Bean("resourceServerJwtDecoder")
    public JwtDecoder jwtDecoder() {
        if ((jwksUri == null || jwksUri.isBlank())
                && (adminJwksUri == null || adminJwksUri.isBlank())) {
            log.warn("No JWKS URI configured, JWT validation will not be available");
            return token -> {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("JWKS URI not configured");
            };
        }

        JwtDecoder customerDecoder = buildDecoder(jwksUri, "customer");
        if (adminJwksUri == null || adminJwksUri.isBlank()) {
            return customerDecoder;
        }

        JwtDecoder adminDecoder = buildDecoder(adminJwksUri, "admin");
        log.info("Resource server validating against BOTH customer and admin JWKS");

        return token -> {
            try {
                return customerDecoder.decode(token);
            } catch (org.springframework.security.oauth2.jwt.JwtException customerErr) {
                try {
                    return adminDecoder.decode(token);
                } catch (org.springframework.security.oauth2.jwt.JwtException adminErr) {
                    log.debug("JWT rejected by customer and admin JWKS");
                    throw adminErr;
                }
            }
        };
    }

    private JwtDecoder buildDecoder(String uri, String label) {
        if (uri == null || uri.isBlank()) {
            log.warn("{} JWKS URI not configured, JWT validation against {} JWKS unavailable", label, label);
            return token -> {
                throw new org.springframework.security.oauth2.jwt.BadJwtException(label + " JWKS URI not configured");
            };
        }
        log.info("Resource server {} JWKS: {}", label, uri);
        return NimbusJwtDecoder.withJwkSetUri(uri).build();
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
