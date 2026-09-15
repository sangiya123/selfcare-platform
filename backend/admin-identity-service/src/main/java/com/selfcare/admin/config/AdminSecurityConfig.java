package com.selfcare.admin.config;

import com.selfcare.admin.security.AdminAuthenticationConverter;
import com.selfcare.admin.security.AdminJwtIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.interfaces.RSAPublicKey;

/**
 * Two filter chains:
 * 1. Public endpoints (login, SAML, JWKS, actuator, MFA login-step)
 * 2. Protected endpoints (require valid JWT)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AdminSecurityConfig {

    private final AdminAuthenticationConverter authConverter;
    private final AdminJwtIssuer adminJwtIssuer;

    public AdminSecurityConfig(AdminAuthenticationConverter authConverter,
                               AdminJwtIssuer adminJwtIssuer) {
        this.authConverter = authConverter;
        this.adminJwtIssuer = adminJwtIssuer;
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder(
            @Value("${selfcare.security.password.bcrypt-strength:12}") int strength) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(strength);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/v1/admin/auth/login",
                             "/api/v1/admin/auth/refresh",
                             "/api/v1/admin/auth/saml/**",
                             "/api/v1/admin/mfa/verify",
                             "/api/v1/auth/login",
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
                .requestMatchers("/api/v1/admin/roles/**").hasAnyRole("SUPER_ADMIN", "TENANT_ADMIN")
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .build();
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        RSAPublicKey publicKey = adminJwtIssuer.getPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("selfcare-admin-identity"));
        return decoder;
    }
}