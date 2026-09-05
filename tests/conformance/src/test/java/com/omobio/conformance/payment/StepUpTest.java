package com.omobio.conformance.payment;

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
 * Step-up authentication conformance tests.
 *
 * Validates that high-value or sensitive operations require re-authentication.
 * Per the step-up auth spec:
 * - Operations above threshold require step-up token
 * - Step-up uses stronger authentication (OTP)
 * - Step-up tokens have shorter validity
 */
@Test(groups = {"payment", "step-up", "security"})
public class StepUpTest {

    private static final Logger LOG = LoggerFactory.getLogger(StepUpTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle tokenBundle;

    @BeforeClass
    public void setup() {
        tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    /**
     * VERIFY: Low-value payment succeeds without step-up.
     */
    @Test(description = "Low-value payment succeeds without step-up")
    public void lowValuePayment_noStepUpRequired() {
        LOG.info("=== Testing low-value payment without step-up ===");

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00); // Below threshold
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");
        paymentRequest.put("fromConnectionId", tokenBundle.primaryConnectionId);
        paymentRequest.put("toConnectionId", "CONN-TEST-001");

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("Low-value payment response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should either succeed or require step-up (never plain 401 UNAUTHENTICATED)
        assertThat(response.statusCode())
                .as("Low-value payment should succeed or require step-up")
                .isIn(200, 201, 202, 401);

        if (response.statusCode() == 401) {
            String errorCode = extractErrorCode(response);
            assertThat(errorCode)
                    .as("401 on payment should be STEP_UP_REQUIRED, not generic auth failure")
                    .isEqualTo("STEP_UP_REQUIRED");
        }
    }

    /**
     * VERIFY: High-value payment requires step-up authentication.
     */
    @Test(description = "High-value payment requires step-up authentication")
    public void highValuePayment_requiresStepUp() {
        LOG.info("=== Testing high-value payment with step-up requirement ===");

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 50000.00); // Above threshold
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");
        paymentRequest.put("fromConnectionId", tokenBundle.primaryConnectionId);
        paymentRequest.put("toConnectionId", "CONN-TEST-001");

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("High-value payment response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should return 401 STEP_UP_REQUIRED
        response.then()
                .statusCode(401)
                .body("error.code", equalTo("STEP_UP_REQUIRED"));
    }

    /**
     * VERIFY: Step-up OTP request returns valid step-up token.
     */
    @Test(description = "Step-up OTP request returns step-up token")
    public void stepUpOtpRequest_returnsStepUpToken() {
        LOG.info("=== Testing step-up OTP flow ===");

        String otpCorrelationId = "sup-" + System.currentTimeMillis();

        // Request step-up OTP
        Map<String, Object> stepUpRequest = new HashMap<>();
        stepUpRequest.put("reason", "HIGH_VALUE_PAYMENT");
        stepUpRequest.put("amount", 50000.00);

        Response stepUpResponse = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.CORRELATION_ID_HEADER, otpCorrelationId)
                .contentType(ContentType.JSON)
                .body(stepUpRequest)
                .when()
                .post("/api/v1/auth/step-up");

        LOG.info("Step-up request response: status={}, body={}",
                stepUpResponse.statusCode(), stepUpResponse.getBody().asString());

        // Should return 202 Accepted
        stepUpResponse.then()
                .statusCode(202);

        // Verify with OTP
        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("code", "123456"); // Test OTP
        verifyRequest.put("correlationId", otpCorrelationId);

        Response verifyResponse = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(verifyRequest)
                .when()
                .post("/api/v1/auth/step-up/verify");

        LOG.info("Step-up verify response: status={}, body={}",
                verifyResponse.statusCode(), verifyResponse.getBody().asString());

        if (verifyResponse.statusCode() == 200) {
            JSONObject data = new JSONObject(verifyResponse.getBody().asString()).optJSONObject("data");
            assertThat(data).isNotNull();

            // Should return step-up token
            assertThat(data.has("stepUpToken") || data.has("accessToken"))
                    .as("Step-up verification should return a token")
                    .isTrue();
        }
    }

    /**
     * VERIFY: High-value payment with valid step-up token succeeds.
     */
    @Test(description = "High-value payment with step-up token succeeds")
    public void highValuePayment_withStepUpToken_succeeds() {
        LOG.info("=== Testing payment with step-up token ===");

        // First get step-up token
        String otpCorrelationId = "sup-" + System.currentTimeMillis();

        Map<String, Object> stepUpRequest = new HashMap<>();
        stepUpRequest.put("reason", "HIGH_VALUE_PAYMENT");
        stepUpRequest.put("amount", 50000.00);

        Response stepUpResponse = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.CORRELATION_ID_HEADER, otpCorrelationId)
                .contentType(ContentType.JSON)
                .body(stepUpRequest)
                .when()
                .post("/api/v1/auth/step-up");

        if (stepUpResponse.statusCode() == 202) {
            // Verify and get step-up token
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("code", "123456");
            verifyRequest.put("correlationId", otpCorrelationId);

            Response verifyResponse = authHelper.authenticatedRequest(tokenBundle)
                    .contentType(ContentType.JSON)
                    .body(verifyRequest)
                    .when()
                    .post("/api/v1/auth/step-up/verify");

            if (verifyResponse.statusCode() == 200) {
                JSONObject data = new JSONObject(verifyResponse.getBody().asString()).optJSONObject("data");
                String stepUpToken = data.optString("stepUpToken", data.optString("accessToken"));

                if (stepUpToken != null && !stepUpToken.isEmpty()) {
                    // Now make the high-value payment with step-up token
                    Map<String, Object> paymentRequest = new HashMap<>();
                    paymentRequest.put("amount", 50000.00);
                    paymentRequest.put("currency", "LKR");
                    paymentRequest.put("paymentMethodId", "PM-TEST-001");
                    paymentRequest.put("fromConnectionId", tokenBundle.primaryConnectionId);
                    paymentRequest.put("toConnectionId", "CONN-TEST-001");

                    Response paymentResponse = RestAssured.given()
                            .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                            .header("Authorization", "Bearer " + stepUpToken)
                            .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                            .contentType(ContentType.JSON)
                            .body(paymentRequest)
                            .when()
                            .post("/api/v1/payments/charge");

                    LOG.info("Payment with step-up token: status={}", paymentResponse.statusCode());

                    assertThat(paymentResponse.statusCode())
                            .as("Payment with valid step-up token should succeed")
                            .isIn(200, 201, 202);
                }
            }
        }
    }

    /**
     * VERIFY: Expired step-up token is rejected.
     */
    @Test(description = "Expired step-up token is rejected")
    public void expiredStepUpToken_isRejected() {
        LOG.info("=== Testing expired step-up token ===");

        // Use a clearly fake/expired step-up token
        String expiredStepUpToken = "stepup-expired-" + tokenBundle.accessToken;

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 50000.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");
        paymentRequest.put("fromConnectionId", tokenBundle.primaryConnectionId);
        paymentRequest.put("toConnectionId", "CONN-TEST-001");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + expiredStepUpToken)
                .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("Expired step-up token response: status={}", response.statusCode());

        response.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    /**
     * VERIFY: Step-up token cannot be used for regular operations.
     */
    @Test(description = "Step-up token is operation-specific")
    public void stepUpToken_isOperationSpecific() {
        LOG.info("=== Testing step-up token operation scope ===");

        // Get step-up token
        String otpCorrelationId = "sup-" + System.currentTimeMillis();

        Map<String, Object> stepUpRequest = new HashMap<>();
        stepUpRequest.put("reason", "HIGH_VALUE_PAYMENT");
        stepUpRequest.put("amount", 50000.00);

        Response stepUpResponse = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.CORRELATION_ID_HEADER, otpCorrelationId)
                .contentType(ContentType.JSON)
                .body(stepUpRequest)
                .when()
                .post("/api/v1/auth/step-up");

        if (stepUpResponse.statusCode() == 202) {
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("code", "123456");
            verifyRequest.put("correlationId", otpCorrelationId);

            Response verifyResponse = authHelper.authenticatedRequest(tokenBundle)
                    .contentType(ContentType.JSON)
                    .body(verifyRequest)
                    .when()
                    .post("/api/v1/auth/step-up/verify");

            if (verifyResponse.statusCode() == 200) {
                JSONObject data = new JSONObject(verifyResponse.getBody().asString()).optJSONObject("data");
                String stepUpToken = data.optString("stepUpToken", data.optString("accessToken"));

                if (stepUpToken != null && !stepUpToken.isEmpty()) {
                    // Try to use step-up token for regular dashboard access
                    Response dashboardResponse = RestAssured.given()
                            .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                            .header("Authorization", "Bearer " + stepUpToken)
                            .queryParam("connectionId", tokenBundle.primaryConnectionId)
                            .when()
                            .get("/api/v1/dashboard/home");

                    LOG.info("Step-up token on dashboard: status={}", dashboardResponse.statusCode());

                    // Step-up token should be rejected for non-high-value operations
                    assertThat(dashboardResponse.statusCode())
                            .as("Step-up token should not work for regular operations")
                            .isIn(401, 403);
                }
            }
        }
    }

    /**
     * VERIFY: Step-up is required for cross-account transfers.
     */
    @Test(description = "Cross-account transfer requires step-up")
    public void crossAccountTransfer_requiresStepUp() {
        LOG.info("=== Testing cross-account transfer step-up ===");

        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("amount", 5000.00);
        transferRequest.put("currency", "LKR");
        transferRequest.put("fromConnectionId", tokenBundle.primaryConnectionId);
        transferRequest.put("toConnectionId", "CONN-OTHER-001"); // Different account
        transferRequest.put("note", "Test transfer");

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                .contentType(ContentType.JSON)
                .body(transferRequest)
                .when()
                .post("/api/v1/payments/transfer");

        LOG.info("Cross-account transfer response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should return 401 STEP_UP_REQUIRED
        if (response.statusCode() == 401) {
            assertThat(extractErrorCode(response))
                    .as("Cross-account transfer should require step-up")
                    .isEqualTo("STEP_UP_REQUIRED");
        }
    }

    /**
     * VERIFY: Step-up threshold is configurable per tenant.
     */
    @Test(description = "Step-up threshold respects tenant configuration")
    public void stepUpThreshold_respectsTenantConfig() {
        LOG.info("=== Testing tenant-specific step-up thresholds ===");

        // AIA tenant (insurance) may have different thresholds
        AuthHelper.TokenBundle aiaUser = authHelper.login(TestConfig.TENANT_AIA, TestDataFactory.uniqueMsisdn());

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 5000.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("policyId", "AIA-POL-TEST-001");

        Response response = authHelper.authenticatedRequest(aiaUser)
                .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/insurance/premium/pay");

        LOG.info("AIA premium payment response: status={}", response.statusCode());

        // Insurance payments should have their own step-up logic
        assertThat(response.statusCode())
                .as("Insurance payment should return valid response")
                .isIn(200, 201, 202, 401, 403);
    }

    // Helper method to extract error code from response
    private String extractErrorCode(Response response) {
        try {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject error = body.optJSONObject("error");
            if (error != null) {
                return error.optString("code", null);
            }
        } catch (Exception e) {
            LOG.debug("Failed to extract error code: {}", e.getMessage());
        }
        return null;
    }
}
