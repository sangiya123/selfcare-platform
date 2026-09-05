package com.omobio.conformance.auth;

import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
import com.omobio.conformance.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * Refresh token replay detection conformance tests.
 *
 * Per ADR-011: when a refresh token is reused (replayed), the entire session must be
 * revoked. This is a critical security feature that prevents token theft attacks.
 */
@Test(groups = {"auth-flow", "replay-detection", "security"})
public class RefreshTokenReplayTest {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenReplayTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    /**
     * VERIFY: Reuse of an already-rotated refresh token triggers full session revocation.
     * This is the core replay detection feature.
     *
     * Expected: HTTP 401 on the replay attempt, then 401 on the new (already-rotated) token too.
     */
    @Test(description = "Reusing a refresh token must trigger full session revocation")
    public void refreshToken_reuseIsRejected() {
        LOG.info("TEST: refresh token replay detection");
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle original = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        // First refresh: should succeed
        Map<String, String> firstRefresh = new HashMap<>();
        firstRefresh.put("refreshToken", original.refreshToken);

        Response firstResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(firstRefresh)
                .when()
                .post("/api/v1/auth/refresh");

        firstResponse.then().statusCode(200);
        String newRefreshToken = new JSONObject(firstResponse.getBody().asString())
                .getJSONObject("data").getString("refreshToken");

        // Second refresh: reuse the old token. This MUST fail with 401 and revoke the session.
        Map<String, String> replayRequest = new HashMap<>();
        replayRequest.put("refreshToken", original.refreshToken);

        Response replayResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(replayRequest)
                .when()
                .post("/api/v1/auth/refresh");

        LOG.info("Replay response: status={}, body={}", replayResponse.statusCode(), replayResponse.getBody().asString());

        replayResponse.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));

        // The newly-issued refresh token must also be invalidated (session revoked)
        Map<String, String> newTokenRequest = new HashMap<>();
        newTokenRequest.put("refreshToken", newRefreshToken);

        Response newTokenResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(newTokenRequest)
                .when()
                .post("/api/v1/auth/refresh");

        newTokenResponse.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    /**
     * VERIFY: An invalid refresh token (not from any session) is rejected.
     *
     * Expected: HTTP 401 with UNAUTHENTICATED.
     */
    @Test(description = "Invalid refresh token is rejected")
    public void invalidRefreshToken_isRejected() {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", "rt-invalid-not-a-real-token-12345");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/refresh");

        LOG.info("Invalid refresh response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    /**
     * VERIFY: An expired refresh token is rejected.
     *
     * Expected: HTTP 401 with UNAUTHENTICATED or specific EXPIRED error.
     */
    @Test(description = "Expired refresh token is rejected")
    public void expiredRefreshToken_isRejected() {
        // In test environments, we can simulate this by using a token that's been manually expired
        // or by using a known test fixture. For now, we test the basic rejection.
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        // Manipulate the token to look like an expired one (format-wise)
        String expiredToken = "rt-expired-" + identifier;

        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", expiredToken);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/refresh");

        response.then()
                .statusCode(401)
                .body("error.code", anyOf(equalTo("UNAUTHENTICATED"), equalTo("TOKEN_EXPIRED")));
    }

    /**
     * VERIFY: Refresh tokens are tenant-scoped. A refresh token from Tenant A cannot be
     * used to refresh in Tenant B.
     *
     * Expected: HTTP 401 or 403.
     */
    @Test(description = "Refresh token is tenant-scoped and cannot be used in other tenants")
    public void refreshToken_isTenantScoped() {
        LOG.info("TEST: cross-tenant refresh token usage");
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle dialogUser = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        // Attempt to use the Dialog refresh token against AIA tenant
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", dialogUser.refreshToken);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_AIA))
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/refresh");

        LOG.info("Cross-tenant refresh response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(anyOf(equalTo(401), equalTo(403)));
    }

    /**
     * VERIFY: Concurrent refresh attempts with the same token: only one should succeed,
     * the other must be rejected as a replay.
     *
     * Expected: First 200, second 401.
     */
    @Test(description = "Concurrent refresh attempts must be detected as replay")
    public void concurrentRefresh_attemptsDetectedAsReplay() throws Exception {
        LOG.info("TEST: concurrent refresh attempts");
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", tokenBundle.refreshToken);

        // Make two concurrent requests
        final Response[] responses = new Response[2];

        Thread t1 = new Thread(() -> {
            responses[0] = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/auth/refresh");
        });

        Thread t2 = new Thread(() -> {
            responses[1] = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(request)
                    .when()
                    .post("/api/v1/auth/refresh");
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        int success = 0;
        int failure = 0;
        for (Response r : responses) {
            if (r.statusCode() == 200) success++;
            else if (r.statusCode() == 401) failure++;
        }

        LOG.info("Concurrent refresh results: success={}, failure={}", success, failure);
        assertThat(success).as("Exactly one concurrent refresh should succeed").isEqualTo(1);
        assertThat(failure).as("The other must be detected as replay").isEqualTo(1);
    }

    /**
     * VERIFY: After multiple legitimate refresh rotations, the oldest tokens are invalidated
     * but the most recent refresh token still works.
     *
     * Expected: Last refresh succeeds, earlier ones are rejected.
     */
    @Test(description = "Refresh token chain: only the latest token is valid")
    public void refreshTokenChain_onlyLatestIsValid() {
        LOG.info("TEST: refresh token chain validation");
        String identifier = TestDataFactory.uniqueMsisdn();
        AuthHelper.TokenBundle original = authHelper.login(TestConfig.TENANT_DIALOG, identifier);

        AuthHelper.TokenBundle refresh1 = authHelper.refresh(original, original.refreshToken);
        AuthHelper.TokenBundle refresh2 = authHelper.refresh(refresh1, refresh1.refreshToken);
        AuthHelper.TokenBundle refresh3 = authHelper.refresh(refresh2, refresh2.refreshToken);

        // The most recent refresh should work
        Map<String, String> validRequest = new HashMap<>();
        validRequest.put("refreshToken", refresh3.refreshToken);

        Response validResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(validRequest)
                .when()
                .post("/api/v1/auth/refresh");

        validResponse.then().statusCode(200);

        // Earlier tokens in the chain should be rejected
        Map<String, String> oldRequest = new HashMap<>();
        oldRequest.put("refreshToken", original.refreshToken);

        Response oldResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(oldRequest)
                .when()
                .post("/api/v1/auth/refresh");

        oldResponse.then().statusCode(401);
    }

    /**
     * VERIFY: Refresh token missing from request body returns 400.
     *
     * Expected: HTTP 400 with INVALID_INPUT.
     */
    @Test(description = "Missing refresh token field is rejected")
    public void missingRefreshTokenField_returnsBadRequest() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(new HashMap<>())
                .when()
                .post("/api/v1/auth/refresh");

        response.then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_INPUT"));
    }

    /**
     * VERIFY: Empty refresh token string is rejected.
     *
     * Expected: HTTP 400 with INVALID_INPUT.
     */
    @Test(description = "Empty refresh token string is rejected")
    public void emptyRefreshToken_returnsBadRequest() {
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", "");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/auth/refresh");

        response.then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_INPUT"));
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }
}
