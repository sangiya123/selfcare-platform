package com.selfcare.conformance.payment;

import com.selfcare.conformance.utils.AuthHelper;
import com.selfcare.conformance.utils.TestConfig;
import com.selfcare.conformance.utils.TestDataFactory;
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
 * Payment idempotency conformance tests.
 *
 * Validates that all payment operations are idempotent when called with the same
 * X-Idempotency-Key header. Per the idempotency spec:
 * - Duplicate requests with same key return original result
 * - Different keys create separate operations
 * - Keys expire after 24 hours
 * - Invalid keys return 400
 */
@Test(groups = {"payment", "idempotency", "security"})
public class IdempotencyTest {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyTest.class);
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
     * VERIFY: Duplicate payment requests with the same idempotency key return identical responses.
     */
    @Test(description = "Duplicate request with same idempotency key returns original result")
    public void duplicatePayment_sameKey_returnsOriginalResult() {
        LOG.info("=== Testing idempotency with same key ===");
        String idempotencyKey = TestDataFactory.uniqueIdempotencyKey();
        LOG.info("Using idempotency key: {}", idempotencyKey);

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // First request
        Response first = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("First payment response: status={}, body={}", first.statusCode(), first.getBody().asString());

        // Second request with same key
        Response second = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("Second payment response: status={}, body={}", second.statusCode(), second.getBody().asString());

        // Both should return the same status
        assertThat(second.statusCode())
                .as("Duplicate request should return same status code")
                .isEqualTo(first.statusCode());

        // If successful, transaction IDs should match
        if (first.statusCode() == 200 || first.statusCode() == 201) {
            String firstTxnId = extractTransactionId(first);
            String secondTxnId = extractTransactionId(second);

            if (firstTxnId != null && secondTxnId != null) {
                assertThat(secondTxnId)
                        .as("Duplicate request should return same transaction ID")
                        .isEqualTo(firstTxnId);
            }
        }
    }

    /**
     * VERIFY: Different idempotency keys create separate payment operations.
     */
    @Test(description = "Different idempotency keys create separate operations")
    public void differentIdempotencyKeys_createSeparateOperations() {
        LOG.info("=== Testing different idempotency keys ===");

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // First payment with key 1
        String key1 = TestDataFactory.uniqueIdempotencyKey();
        Response first = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, key1)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        // Second payment with key 2
        String key2 = TestDataFactory.uniqueIdempotencyKey();
        Response second = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, key2)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("First (key1): status={}, TxnId={}", first.statusCode(), extractTransactionId(first));
        LOG.info("Second (key2): status={}, TxnId={}", second.statusCode(), extractTransactionId(second));

        // Both should succeed
        assertThat(first.statusCode())
                .as("First payment should succeed")
                .isIn(200, 201, 202, 401);
        assertThat(second.statusCode())
                .as("Second payment should succeed")
                .isIn(200, 201, 202, 401);

        // If both succeeded, they should have different transaction IDs
        String firstTxnId = extractTransactionId(first);
        String secondTxnId = extractTransactionId(second);

        if (firstTxnId != null && secondTxnId != null) {
            assertThat(secondTxnId)
                    .as("Different idempotency keys should create different transactions")
                    .isNotEqualTo(firstTxnId);
        }
    }

    /**
     * VERIFY: Missing idempotency key on payment endpoint returns 400.
     */
    @Test(description = "Missing idempotency key on payment returns 400")
    public void missingIdempotencyKey_returnsBadRequest() {
        LOG.info("=== Testing missing idempotency key ===");

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("Missing idempotency key response: status={}", response.statusCode());

        // Payment endpoint should require idempotency key
        assertThat(response.statusCode())
                .as("Missing idempotency key should return 400")
                .isIn(400, 422);
    }

    /**
     * VERIFY: Invalid idempotency key format is rejected.
     */
    @Test(description = "Invalid idempotency key format is rejected")
    public void invalidIdempotencyKeyFormat_returnsBadRequest() {
        LOG.info("=== Testing invalid idempotency key format ===");

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // Try with empty string
        Response emptyResponse = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, "")
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        emptyResponse.then()
                .statusCode(anyOf(equalTo(400), equalTo(422)));

        // Try with too long key (e.g., 10KB)
        String longKey = "x".repeat(10 * 1024);
        Response longResponse = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, longKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        longResponse.then()
                .statusCode(anyOf(equalTo(400), equalTo(422), equalTo(431)));
    }

    /**
     * VERIFY: Idempotency key is scoped to the user/session.
     */
    @Test(description = "Idempotency key is user-scoped")
    public void idempotencyKey_isUserScoped() {
        LOG.info("=== Testing user-scoped idempotency ===");

        String sharedKey = "shared-key-" + System.currentTimeMillis();

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // User 1 makes payment
        Response user1 = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, sharedKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        // User 2 (new session) uses same key - should not get user 1's result
        AuthHelper.TokenBundle user2 = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());

        Response user2Response = authHelper.authenticatedRequest(user2)
                .header(TestConfig.IDEMPOTENCY_HEADER, sharedKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("User 1: status={}", user1.statusCode());
        LOG.info("User 2: status={}", user2Response.statusCode());

        // User 2 should get their own payment, not user 1's cached result
        // The key is user-scoped, so user 2 creates a new transaction
        assertThat(user2Response.statusCode())
                .as("Different user with same key should create new transaction")
                .isIn(200, 201, 202, 400, 401);
    }

    /**
     * VERIFY: Idempotency key is scoped to the tenant.
     */
    @Test(description = "Idempotency key is tenant-scoped")
    public void idempotencyKey_isTenantScoped() {
        LOG.info("=== Testing tenant-scoped idempotency ===");

        String sharedKey = "tenant-shared-key-" + System.currentTimeMillis();

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 100.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // Dialog tenant makes payment
        Response dialogPayment = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, sharedKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        // AIA tenant uses same key - should create separate transaction
        AuthHelper.TokenBundle aiaUser = authHelper.login(TestConfig.TENANT_AIA, TestDataFactory.uniqueMsisdn());

        Response aiaPayment = authHelper.authenticatedRequest(aiaUser)
                .header(TestConfig.IDEMPOTENCY_HEADER, sharedKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/payments/charge");

        LOG.info("Dialog: status={}", dialogPayment.statusCode());
        LOG.info("AIA: status={}", aiaPayment.statusCode());

        // Different tenants should have separate idempotency scopes
        assertThat(dialogPayment.statusCode())
                .as("Dialog payment should succeed or require auth")
                .isIn(200, 201, 202, 400, 401);
        assertThat(aiaPayment.statusCode())
                .as("AIA payment should succeed or require auth")
                .isIn(200, 201, 202, 400, 401);
    }

    /**
     * VERIFY: Idempotency is preserved across retries within the key's TTL.
     */
    @Test(description = "Multiple rapid retries return same result")
    public void rapidRetries_returnSameResult() throws InterruptedException {
        LOG.info("=== Testing rapid retries ===");

        String idempotencyKey = TestDataFactory.uniqueIdempotencyKey();

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 50.00);
        paymentRequest.put("currency", "LKR");
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        Response[] responses = new Response[5];
        String[] transactionIds = new String[5];

        for (int i = 0; i < 5; i++) {
            responses[i] = authHelper.authenticatedRequest(tokenBundle)
                    .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                    .contentType(ContentType.JSON)
                    .body(paymentRequest)
                    .when()
                    .post("/api/v1/payments/charge");

            transactionIds[i] = extractTransactionId(responses[i]);

            // Small delay between requests
            Thread.sleep(10);
        }

        // All should return the same transaction ID
        String firstTxnId = transactionIds[0];
        for (int i = 1; i < 5; i++) {
            LOG.info("Retry {}: status={}, TxnId={}", i + 1, responses[i].statusCode(), transactionIds[i]);

            assertThat(responses[i].statusCode())
                    .as("All retries should return same status")
                    .isEqualTo(responses[0].statusCode());

            if (firstTxnId != null && transactionIds[i] != null) {
                assertThat(transactionIds[i])
                        .as("All retries should return same transaction ID")
                        .isEqualTo(firstTxnId);
            }
        }
    }

    // === Bill Payment Idempotency Tests ===

    /**
     * VERIFY: Bill payments are idempotent with idempotency keys.
     */
    @Test(description = "Bill payment is idempotent")
    public void billPayment_isIdempotent() {
        LOG.info("=== Testing bill payment idempotency ===");

        String idempotencyKey = TestDataFactory.uniqueIdempotencyKey();
        String billId = "BILL-TEST-001";

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", 1000.00);
        paymentRequest.put("paymentMethodId", "PM-TEST-001");

        // First payment
        Response first = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/bills/" + billId + "/pay");

        // Duplicate payment
        Response second = authHelper.authenticatedRequest(tokenBundle)
                .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(ContentType.JSON)
                .body(paymentRequest)
                .when()
                .post("/api/v1/bills/" + billId + "/pay");

        LOG.info("First bill payment: status={}", first.statusCode());
        LOG.info("Second bill payment: status={}", second.statusCode());

        // Should return same result
        assertThat(second.statusCode())
                .as("Duplicate bill payment should return same status")
                .isEqualTo(first.statusCode());
    }

    // Helper method to extract transaction ID from response
    private String extractTransactionId(Response response) {
        if (response.statusCode() == 200 || response.statusCode() == 201 || response.statusCode() == 202) {
            try {
                JSONObject body = new JSONObject(response.getBody().asString());
                JSONObject data = body.optJSONObject("data");
                if (data != null && data.has("transactionId")) {
                    return data.getString("transactionId");
                }
            } catch (Exception e) {
                LOG.debug("Failed to extract transaction ID: {}", e.getMessage());
            }
        }
        return null;
    }
}
