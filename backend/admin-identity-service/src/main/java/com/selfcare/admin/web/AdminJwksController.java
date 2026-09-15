package com.selfcare.admin.web;

import com.selfcare.admin.security.AdminJwtIssuer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * JWKS endpoint for admin tokens.
 *
 * <p>Publishes the admin identity service's RSA public key in RFC 7517 JWK
 * format. Downstream resource servers (config-tenant, content, reporting, ...)
 * use this endpoint to validate admin JWTs issued by this service.
 *
 * <p>URI: {@code GET /.well-known/jwks.json}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "JWKS", description = "Admin JSON Web Key Set endpoint")
public class AdminJwksController {

    private final AdminJwtIssuer jwtIssuer;

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get admin JSON Web Key Set",
            description = "Returns the current admin RSA public key(s) in JWKS format for JWT validation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "JWKS document")
    })
    public ResponseEntity<Map<String, Object>> getJwks() {
        RSAPublicKey key = jwtIssuer.getPublicKey();
        String kid = jwtIssuer.getKeyId();

        Map<String, Object> jwk = buildJwk(key, kid);
        Map<String, Object> jwks = Map.of("keys", List.of(jwk));

        log.debug("Admin JWKS served: kid={}", kid);
        return ResponseEntity.ok(jwks);
    }

    /**
     * Also publish OpenID discovery for admin tokens.
     */
    @GetMapping(value = "/.well-known/openid-configuration",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Admin OpenID Connect Discovery",
            description = "Returns OpenID Connect configuration metadata for admin tokens.")
    public ResponseEntity<Map<String, String>> getOpenIdConfiguration() {
        Map<String, String> oidc = Map.of(
                "issuer", "selfcare-admin-identity",
                "jwks_uri", "/.well-known/jwks.json",
                "response_types_supported", "token",
                "id_token_signing_alg_values_supported", "RS256",
                "scopes_supported", "admin"
        );
        return ResponseEntity.ok(oidc);
    }

    private Map<String, Object> buildJwk(RSAPublicKey key, String kid) {
        BigInteger n = key.getModulus();
        byte[] nBytes = unsignedBytes(n);

        BigInteger e = key.getPublicExponent();
        byte[] eBytes = unsignedBytes(e);

        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "kid", kid,
                "alg", "RS256",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(nBytes),
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(eBytes)
        );
    }

    private byte[] unsignedBytes(BigInteger value) {
        byte[] signed = value.toByteArray();
        if (signed[0] != 0) {
            return signed;
        }
        byte[] unsigned = new byte[signed.length - 1];
        System.arraycopy(signed, 1, unsigned, 0, unsigned.length);
        return unsigned;
    }
}