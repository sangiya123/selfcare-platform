package com.omobio.identity.web;

import com.omobio.identity.security.JwtIssuer;
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
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * JWKS endpoint — publishes the service's RSA public key in JSON Web Key
 * (JWK) format as defined in RFC 7517.
 *
 * <p>Clients (other services, API gateway) use this endpoint to retrieve
 * the current signing public key for JWT validation. The key is identified
 * by its {@code kid} which is embedded in every JWT header.
 *
 * <p>URI: {@code GET /.well-known/jwks.json}
 *
 * <p>Response shape (RFC 7517):
 * <pre>
 * {
 *   "keys": [
 *     {
 *       "kty": "RSA",
 *       "use": "sig",
 *       "kid": "omobio-selfcare-1",
 *       "alg": "RS256",
 *       "n": "...",
 *       "e": "AQAB"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7517">RFC 7517 — JSON Web Key</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7518">RFC 7518 — JWA (alg / kty details)</a>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "JWKS", description = "JSON Web Key Set endpoint")
public class JwksController {

    private final JwtIssuer jwtIssuer;

    /**
     * Return the JWKS document.
     *
     * @return 200 with the JWKS JSON body
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get JSON Web Key Set",
            description = "Returns the current RSA public key(s) in JWKS format for JWT validation."
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

        log.debug("JWKS served: kid={}", kid);
        return ResponseEntity.ok(jwks);
    }

    /**
     * Also serve the canonical OpenID Connect discovery path.
     */
    @GetMapping(value = "/.well-known/openid-configuration",
               produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "OpenID Connect Discovery",
            description = "Returns OpenID Connect configuration metadata.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OIDC configuration document")
    })
    public ResponseEntity<Map<String, String>> getOpenIdConfiguration() {
        Map<String, String> oidc = Map.of(
                "issuer", "omobio-selfcare-platform",
                "jwks_uri", "/.well-known/jwks.json",
                "token_endpoint", "/api/v1/auth/token",
                "response_types_supported", "token",
                "id_token_signing_alg_values_supported", "RS256",
                "scopes_supported", "openid profile selfcare"
        );
        return ResponseEntity.ok(oidc);
    }

    // -------------------------------------------------------------------------
    // JWK construction (RFC 7518 §6.3)
    // -------------------------------------------------------------------------

    /**
     * Build a JWK from an RSA public key.
     *
     * <p>Fields per RFC 7518 §6.3:
     * <ul>
     *   <li>{@code kty} — "RSA"
     *   <li>{@code use} — "sig"
     *   <li>{@code kid} — passed from JwtIssuer
     *   <li>{@code alg} — "RS256"
     *   <li>{@code n}  — Base64url(modulus)
     *   <li>{@code e}  — Base64url(exponent)
     * </ul>
     *
     * @param key the RSA public key
     * @param kid the key ID
     * @return a JWK map suitable for JSON serialisation
     */
    private Map<String, Object> buildJwk(RSAPublicKey key, String kid) {
        // Modulus: unsigned big-endian bytes
        BigInteger n = key.getModulus();
        byte[] nBytes = unsignedBytes(n);

        // Exponent: unsigned big-endian bytes
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

    /**
     * Convert a BigInteger to an unsigned byte array (no leading sign bit).
     */
    private byte[] unsignedBytes(BigInteger value) {
        byte[] signed = value.toByteArray();
        if (signed[0] != 0) {
            return signed; // already unsigned
        }
        // Strip the leading zero sign byte
        byte[] unsigned = new byte[signed.length - 1];
        System.arraycopy(signed, 1, unsigned, 0, unsigned.length);
        return unsigned;
    }
}
