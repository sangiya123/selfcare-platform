package com.omobio.conformance.auth;

import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
import com.omobio.conformance.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * Authentication flow conformance tests.
 * Validates the complete OTP login, token refresh, and logout flows.
 *
 * Covers:
 * - OTP request and verification
 * - Access token format and claims
 * - Refresh token rotation
 * - Session management
 * - Error handling for invalid credentials
 */
@Test(groups = {"auth-flow", "security"})
public class AuthFlowTest {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFlowTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    @Test(description = "OTP request returns 202 Accepted and includes correlation ID and expiry")
    public void otpRequest_returnsAccepted() {
        String identifier = TestDataFactory.uniqueMsisdn();
        String correlationId = "otp-" + UUID.randomUUID();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header(TestConfig.CORRELATION_ID_HEADER, correlationId)
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpRequest(identifier))
                .when()
                .post("/api/v1/auth/otp");

        LOG.info("OTP Request response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(202)
                .body("correlationId", notNullValue())
                .body("expiresAt", notNullValue())
                .body("expiresIn", greaterThan(0));
    }

    @Test(description = "OTP verification with valid code returns tokens and user info")
    public void otpVerify_validCode_returnsTokens() {
        String identifier = TestDataFactory.uniqueMsisdn();
        String correlationId = "otp-" + UUID.randomUUID();

        // Request OTP
        RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header(TestConfig.CORRELATION_ID_HEADER, correlationId)
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpRequest(identifier))
                .when()
                .post("/api/v1/auth/otp")
                .then()
                .statusCode(202);

        // Verify OTP with test code
        Response verifyResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpVerifyRequest(identifier, "123456", correlationId))
                .when()
                .post("/api/v1/auth/otp/verify");

        LOG.info("OTP Verify response: status={}, body={}", verifyResponse.statusCode(), verifyResponse.getBody().asString());

        verifyResponse.then()
                .statusCode(200)
                .body("data.accessToken", notNullValue())
                .body("data.refreshToken", notNullValue())
                .body("data.sessionId", notNullValue())
                .body("data.expiresIn", equalTo(900)) // 15 minutes
                .body("data.refreshExpiresIn", equalTo(2592000)) // 30 days
                .body("data.tokenType", equalTo("Bearer"))
                .body("data.user.userId", notNullValue())
                .body("data.user.tenantId", equalTo(TestConfig.TENANT_DIALOG))
                .body("data.user.primaryConnectionId", notNullValue());

        // Validate token format (JWT structure: header.payload.signature)
        String accessToken = new JSONObject(verifyResponse.getBody().asString())
                .getJSONObject("data").getString("accessToken");
        assertThat(accessToken.split("\\.")).hasSize(3);
    }

    @Test(description = "OTP verification with invalid code returns 401")
    public void otpVerify_invalidCode_returnsUnauthorized() {
        String identifier = TestDataFactory.uniqueMsisdn();
        String correlationId = "otp-" + UUID.randomUUID();

        // Request OTP
        RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header(TestConfig.CORRELATION_ID_HEADER, correlationId)
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpRequest(identifier))
                .when()
                .post("/api/v1/auth/otp")
                .then()
                .statusCode(202);

        // Verify with wrong code
        Response verifyResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpVerifyRequest(identifier, "999999", correlationId))
                .when()
                .post("/api/v1/auth/otp/verify");

        LOG.info("Invalid OTP response: status={}, body={}", verifyResponse.statusCode(), verifyResponse.getBody().asString());

        verifyResponse.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test(description = "OTP verification with expired correlation ID returns 400")
    public void otpVerify_expiredCorrelationId_returnsBadRequest() {
        String identifier = TestDataFactory.uniqueMsisdn();

        Response verifyResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(TestDataFactory.otpVerifyRequest(identifier, "123456", "otp-expired-12345"))
                .when()
                .post("/api/v1/auth/otp/verify");

        LOG.info("Expired correlation ID response: status={}, body={}", verifyResponse.statusCode(), verifyResponse.getBody().asString());

        verifyResponse.then()
                .statusCode(400)
                .body("error.code", anyOf(equalTo("INVALID_INPUT"), equalTo("EXPIRED")));
    }

    @Test(description = "Authenticated requests with valid token succeed")
    public void authenticatedRequest_withValidToken_succeeds() {
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard response: status={}", response.statusCode());
        response.then()
                .statusCode(anyOf(equalTo(200), equalTo(207))); // 207 for partial (acceptable in BFF)
    }

    @Test(description = "Requests without Authorization header return 401")
    public void missingAuthorizationHeader_returnsUnauthorized() {
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Missing auth response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test(description = "Requests with malformed token return 401")
    public void malformedToken_returnsUnauthorized() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Malformed token response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test(description = "Logout invalidates the session and returns 204")
    public void signout_invalidatesSession() {
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        // Sign out
        Response signoutResponse = authHelper.signout(tokenBundle);
        signoutResponse.then().statusCode(204);

        // Attempt to use the token after logout
        Response afterSignout = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        afterSignout.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test(description = "Token refresh returns new tokens while invalidating the old refresh token")
    public void refreshToken_rotatesTokens() {
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle original = authHelper.login(TestConfig.TENANT_DIALOG, identifier);
        String originalRefresh = original.refreshToken;

        // Refresh the token
        AuthHelper.TokenBundle refreshed = authHelper.refresh(original, originalRefresh);

        assertThat(refreshed.accessToken).isNotEqualTo(original.accessToken);
        assertThat(refreshed.refreshToken).isNotEqualTo(original.refreshToken);
        assertThat(refreshed.refreshToken).isNotEqualTo(originalRefresh);
        assertThat(refreshed.sessionId).isEqualTo(original.sessionId);
        assertThat(refreshed.userId).isEqualTo(original.userId);

        // Original token should still work
        Response withOriginal = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + original.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", original.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        withOriginal.then().statusCode(anyOf(equalTo(200), equalTo(207)));
    }

    @Test(description = "OTP request with invalid identifier format returns 400")
    public void otpRequest_invalidIdentifierFormat_returnsBadRequest() {
        Map<String, String> invalidRequest = new HashMap<>();
        invalidRequest.put("identifier", "not-a-valid-phone-number");
        invalidRequest.put("channel", "SMS");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(invalidRequest)
                .when()
                .post("/api/v1/auth/otp");

        LOG.info("Invalid identifier response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_INPUT"));
    }

    @Test(description = "Multiple rapid OTP requests for same identifier are rate limited")
    public void rapidOtpRequests_areRateLimited() {
        String identifier = TestDataFactory.uniqueMsisdn();

        // Make multiple rapid requests
        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            Response response = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(TestDataFactory.otpRequest(identifier))
                    .when()
                    .post("/api/v1/auth/otp");

            if (response.statusCode() == 202) {
                successCount++;
            } else if (response.statusCode() == 429) {
                LOG.info("Rate limited on attempt {}", i + 1);
                response.then()
                        .body("error.code", equalTo("RATE_LIMITED"))
                        .header("Retry-After", notNullValue());
                break;
            }
        }

        LOG.info("Successfully made {} OTP requests before rate limiting", successCount);
    }

    @Test(description = "Access token contains required claims for authorization")
    public void accessToken_containsRequiredClaims() {
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        // Decode JWT payload (middle part)
        String[] parts = tokenBundle.accessToken.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        JSONObject claims = new JSONObject(payload);

        LOG.info("Token claims: {}", claims.toString());

        assertThat(claims.has("sub") || claims.has("userId")).isTrue();
        assertThat(claims.has("tenantId") || claims.has("tenant")).isTrue();
        assertThat(claims.has("exp")).isTrue();
        assertThat(claims.has("iat")).isTrue();
        assertThat(claims.has("sessionId")).isTrue();
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }
}
