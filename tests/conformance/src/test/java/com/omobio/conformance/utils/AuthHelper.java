package com.omobio.conformance.utils;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for authenticating test users and managing tokens.
 * Provides convenient methods for logging in users across tenants and caching tokens within a test method.
 */
public final class AuthHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AuthHelper.class);
    private static final String OTP_ENDPOINT = "/api/v1/auth/otp";
    private static final String OTP_VERIFY_ENDPOINT = "/api/v1/auth/otp/verify";
    private static final String REFRESH_ENDPOINT = "/api/v1/auth/refresh";
    private static final String SIGNOUT_ENDPOINT = "/api/v1/auth/signout";

    private final Map<String, TokenBundle> tokenCache = new ConcurrentHashMap<>();

    /**
     * Login a test user using OTP verification flow.
     * @param tenantId the tenant identifier
     * @param identifier the user identifier (e.g. MSISDN, email, customer ID)
     * @return a bundle containing access token, refresh token, and user details
     */
    public TokenBundle login(String tenantId, String identifier) {
        String cacheKey = tenantId + ":" + identifier;
        return tokenCache.computeIfAbsent(cacheKey, k -> doLogin(tenantId, identifier));
    }

    private TokenBundle doLogin(String tenantId, String identifier) {
        String otpCorrelationId = "otp-" + UUID.randomUUID();
        Map<String, String> otpRequest = new HashMap<>();
        otpRequest.put("identifier", identifier);
        otpRequest.put("channel", "SMS");

        Response otpResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(tenantId))
                .header(TestConfig.CORRELATION_ID_HEADER, otpCorrelationId)
                .contentType(ContentType.JSON)
                .body(otpRequest)
                .when()
                .post(OTP_ENDPOINT);

        if (otpResponse.statusCode() != 202) {
            throw new IllegalStateException(
                    String.format("OTP request failed: tenant=%s, identifier=%s, status=%d, body=%s",
                            tenantId, identifier, otpResponse.statusCode(), otpResponse.getBody().asString()));
        }

        // For test environments, the OTP is the deterministic test code
        Map<String, String> verifyRequest = new HashMap<>();
        verifyRequest.put("identifier", identifier);
        verifyRequest.put("code", "123456");
        verifyRequest.put("correlationId", otpCorrelationId);

        Response verifyResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(tenantId))
                .contentType(ContentType.JSON)
                .body(verifyRequest)
                .when()
                .post(OTP_VERIFY_ENDPOINT);

        if (verifyResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    String.format("OTP verify failed: tenant=%s, identifier=%s, status=%d, body=%s",
                            tenantId, identifier, verifyResponse.statusCode(), verifyResponse.getBody().asString()));
        }

        JSONObject data = new JSONObject(verifyResponse.getBody().asString()).getJSONObject("data");
        return new TokenBundle(
                tenantId,
                identifier,
                data.getString("accessToken"),
                data.getString("refreshToken"),
                data.getString("sessionId"),
                data.getString("userId"),
                data.getString("primaryConnectionId")
        );
    }

    public TokenBundle refresh(TokenBundle original, String oldRefreshToken) {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", oldRefreshToken);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(original.tenantId))
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post(REFRESH_ENDPOINT);

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    String.format("Token refresh failed: tenant=%s, status=%d, body=%s",
                            original.tenantId, response.statusCode(), response.getBody().asString()));
        }

        JSONObject data = new JSONObject(response.getBody().asString()).getJSONObject("data");
        return new TokenBundle(
                original.tenantId,
                original.identifier,
                data.getString("accessToken"),
                data.getString("refreshToken"),
                data.getString("sessionId"),
                original.userId,
                original.primaryConnectionId
        );
    }

    public Response signout(TokenBundle bundle) {
        return RestAssured.given()
                .spec(TestConfig.baseRequestSpec(bundle.tenantId))
                .header("Authorization", "Bearer " + bundle.accessToken)
                .when()
                .post(SIGNOUT_ENDPOINT);
    }

    public void clearCache() {
        tokenCache.clear();
    }

    public RequestSpecification authenticatedRequest(TokenBundle bundle) {
        return RestAssured.given()
                .spec(TestConfig.baseRequestSpec(bundle.tenantId))
                .header("Authorization", "Bearer " + bundle.accessToken);
    }

    public static class TokenBundle {
        public final String tenantId;
        public final String identifier;
        public final String accessToken;
        public final String refreshToken;
        public final String sessionId;
        public final String userId;
        public final String primaryConnectionId;

        public TokenBundle(String tenantId, String identifier, String accessToken,
                           String refreshToken, String sessionId, String userId, String primaryConnectionId) {
            this.tenantId = tenantId;
            this.identifier = identifier;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.sessionId = sessionId;
            this.userId = userId;
            this.primaryConnectionId = primaryConnectionId;
        }
    }
}
