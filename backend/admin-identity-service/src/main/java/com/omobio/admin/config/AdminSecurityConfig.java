package com.omobio.admin.config;

import com.omobio.admin.security.AdminAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Two filter chains:
 * 1. Public endpoints (login, SAML, JWKS, actuator)
 * 2. Protected endpoints (require valid JWT)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AdminSecurityConfig {

    @Value("${omobio.security.admin-jwt.secret}")
    private String jwtSecret;

    private final AdminAuthenticationConverter authConverter;

    public AdminSecurityConfig(AdminAuthenticationConverter authConverter) {
        this.authConverter = authConverter;
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder(
            @Value("${omobio.security.password.bcrypt-strength:12}") int strength) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(strength);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/v1/auth/login",
                             "/api/v1/auth/saml/**",
                             "/api/v1/auth/mfa/**",
                             "/.well-known/**",
                             "/actuator/health",
                             "/actuator/info")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().permitAll()
            )
            .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain protectedChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authConverter))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/sessions/**").authenticated()
                .requestMatchers("/api/v1/admin/roles/**").hasRole("TENANT_ADMIN")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("TENANT_ADMIN", "LAYOUT_EDITOR", "JOURNEY_EDITOR")
                .anyRequest().authenticated()
            )
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
