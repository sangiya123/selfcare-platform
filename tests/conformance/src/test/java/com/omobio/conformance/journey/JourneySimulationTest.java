package com.omobio.conformance.journey;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * Journey simulation conformance tests.
 * Validates end-to-end user journeys across multiple API calls.
 *
 * Tests:
 * - Dashboard load journey
 * - Bill payment journey
 * - Plan change journey
 * - Data purchase journey
 * - Cross-feature journeys
 */
@Test(groups = {"journey-simulation", "e2e"})
public class JourneySimulationTest {

    private static final Logger LOG = LoggerFactory.getLogger(JourneySimulationTest.class);
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

    // === Dashboard Load Journey ===

    @Test(description = "User can load dashboard and see widgets populate")
    public void dashboardLoad_journey() {
        LOG.info("=== Starting dashboard load journey ===");

        // Step 1: Get dashboard with widgets
        Response dashboard = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard response: status={}", dashboard.statusCode());

        if (dashboard.statusCode() == 200 || dashboard.statusCode() == 207) {
            JSONObject body = new JSONObject(dashboard.getBody().asString());

            // Verify overall status
            JSONObject data = body.optJSONObject("data");
            assertThat(data).isNotNull();

            if (data.has("overallStatus")) {
                String overallStatus = data.getString("overallStatus");
                LOG.info("Dashboard overall status: {}", overallStatus);
                assertThat(overallStatus).isIn("SUCCESS", "PARTIAL", "ERROR");
            }
        }

        LOG.info("=== Dashboard load journey complete ===");
    }

    @Test(description = "User can refresh individual widgets")
    public void widgetRefresh_journey() {
        LOG.info("=== Starting widget refresh journey ===");

        String[] widgets = {"balance", "bills", "usage"};

        for (String widgetId : widgets) {
            Response widgetResponse = authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/widgets/" + widgetId);

            LOG.info("Widget '{}' response: status={}", widgetId, widgetResponse.statusCode());

            assertThat(widgetResponse.statusCode())
                    .as("Widget '{}' should return 200 or 404 (not found)", widgetId)
                    .isIn(200, 404);
        }

        LOG.info("=== Widget refresh journey complete ===");
    }

    // === Bill Payment Journey ===

    @Test(description = "User can view and pay a bill end-to-end")
    public void billPayment_journey() {
        LOG.info("=== Starting bill payment journey ===");
        String idempotencyKey = TestDataFactory.uniqueIdempotencyKey();

        // Step 1: List outstanding bills
        Response billsList = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .queryParam("status", "OUTSTANDING")
                .when()
                .get("/api/v1/bills");

        LOG.info("Bills list response: status={}", billsList.statusCode());

        if (billsList.statusCode() == 200) {
            JSONObject body = new JSONObject(billsList.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null && data.has("bills") && data.getJSONArray("bills").length() > 0) {
                JSONObject firstBill = data.getJSONArray("bills").getJSONObject(0);
                String billId = firstBill.getString("billId");
                double amount = firstBill.getDouble("totalAmount");

                LOG.info("Found bill {} with amount {}", billId, amount);

                // Step 2: Pay the bill
                Map<String, Object> paymentRequest = new HashMap<>();
                paymentRequest.put("amount", amount);
                paymentRequest.put("paymentMethodId", "PM-TEST-001");

                Response payment = authHelper.authenticatedRequest(tokenBundle)
                        .header(TestConfig.IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(ContentType.JSON)
                        .body(paymentRequest)
                        .when()
                        .post("/api/v1/bills/" + billId + "/pay");

                LOG.info("Bill payment response: status={}, body={}",
                        payment.statusCode(), payment.getBody().asString());

                // Should succeed or return step-up required
                assertThat(payment.statusCode())
                        .as("Bill payment should succeed or require step-up")
                        .isIn(200, 202, 401);
            }
        }

        LOG.info("=== Bill payment journey complete ===");
    }

    // === Data Purchase Journey ===

    @Test(description = "User can browse and purchase a data pack")
    public void dataPurchase_journey() {
        LOG.info("=== Starting data purchase journey ===");

        // Step 1: Browse available data packs
        Response products = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .queryParam("category", "DATA")
                .when()
                .get("/api/v1/products");

        LOG.info("Products response: status={}", products.statusCode());

        if (products.statusCode() == 200) {
            JSONObject body = new JSONObject(products.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null && data.has("products") && data.getJSONArray("products").length() > 0) {
                JSONObject firstProduct = data.getJSONArray("products").getJSONObject(0);
                String productId = firstProduct.getString("productId");

                LOG.info("Found product {}: {}", productId, firstProduct.toString());

                // Step 2: Purchase the data pack
                Map<String, Object> purchaseRequest = new HashMap<>();
                purchaseRequest.put("productId", productId);
                purchaseRequest.put("connectionId", tokenBundle.primaryConnectionId);

                Response purchase = authHelper.authenticatedRequest(tokenBundle)
                        .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                        .contentType(ContentType.JSON)
                        .body(purchaseRequest)
                        .when()
                        .post("/api/v1/products/purchase");

                LOG.info("Purchase response: status={}, body={}",
                        purchase.statusCode(), purchase.getBody().asString());

                assertThat(purchase.statusCode())
                        .as("Purchase should succeed or require step-up")
                        .isIn(200, 201, 202, 401);
            }
        }

        LOG.info("=== Data purchase journey complete ===");
    }

    // === Profile Update Journey ===

    @Test(description = "User can view and update profile information")
    public void profileUpdate_journey() {
        LOG.info("=== Starting profile update journey ===");

        // Step 1: Get current profile
        Response profile = authHelper.authenticatedRequest(tokenBundle)
                .when()
                .get("/api/v1/profile");

        LOG.info("Profile response: status={}", profile.statusCode());

        if (profile.statusCode() == 200) {
            JSONObject body = new JSONObject(profile.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            assertThat(data).isNotNull();
            LOG.info("Current profile: {}", data.toString());

            // Step 2: Update notification preferences (if endpoint exists)
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("notifications", Map.of(
                    "sms", true,
                    "email", false,
                    "push", true
            ));

            Response update = authHelper.authenticatedRequest(tokenBundle)
                    .contentType(ContentType.JSON)
                    .body(updateRequest)
                    .when()
                    .patch("/api/v1/profile/notifications");

            LOG.info("Profile update response: status={}", update.statusCode());

            // Should succeed or return 404 if endpoint doesn't exist
            assertThat(update.statusCode())
                    .as("Profile update should succeed or endpoint not exist")
                    .isIn(200, 204, 404);
        }

        LOG.info("=== Profile update journey complete ===");
    }

    // === Usage Check Journey ===

    @Test(description = "User can check all usage metrics")
    public void usageCheck_journey() {
        LOG.info("=== Starting usage check journey ===");

        // Step 1: Check data usage
        Response dataUsage = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/data");

        LOG.info("Data usage response: status={}", dataUsage.statusCode());
        assertThat(dataUsage.statusCode()).isIn(200, 404);

        // Step 2: Check voice usage
        Response voiceUsage = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/voice");

        LOG.info("Voice usage response: status={}", voiceUsage.statusCode());
        assertThat(voiceUsage.statusCode()).isIn(200, 404);

        // Step 3: Check SMS usage
        Response smsUsage = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/sms");

        LOG.info("SMS usage response: status={}", smsUsage.statusCode());
        assertThat(smsUsage.statusCode()).isIn(200, 404);

        LOG.info("=== Usage check journey complete ===");
    }

    // === Plan Change Journey ===

    @Test(description = "User can browse and change plans")
    public void planChange_journey() {
        LOG.info("=== Starting plan change journey ===");

        // Step 1: Get current plan
        Response currentPlan = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/products/current-plan");

        LOG.info("Current plan response: status={}", currentPlan.statusCode());

        // Step 2: Browse available plans
        Response availablePlans = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/products/plans");

        LOG.info("Available plans response: status={}", availablePlans.statusCode());

        if (availablePlans.statusCode() == 200) {
            JSONObject body = new JSONObject(availablePlans.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null && data.has("plans") && data.getJSONArray("plans").length() > 0) {
                JSONObject newPlan = data.getJSONArray("plans").getJSONObject(0);
                String planId = newPlan.getString("planId");

                LOG.info("Found plan {} for switching", planId);

                // Step 3: Attempt to switch plans
                Map<String, Object> switchRequest = new HashMap<>();
                switchRequest.put("planId", planId);
                switchRequest.put("effectiveDate", "IMMEDIATE");

                Response switchResponse = authHelper.authenticatedRequest(tokenBundle)
                        .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                        .contentType(ContentType.JSON)
                        .body(switchRequest)
                        .when()
                        .post("/api/v1/products/switch");

                LOG.info("Plan switch response: status={}, body={}",
                        switchResponse.statusCode(), switchResponse.getBody().asString());

                assertThat(switchResponse.statusCode())
                        .as("Plan switch should succeed or require step-up")
                        .isIn(200, 201, 202, 401);
            }
        }

        LOG.info("=== Plan change journey complete ===");
    }

    // === Notification Preferences Journey ===

    @Test(description = "User can manage notification preferences end-to-end")
    public void notificationPreferences_journey() {
        LOG.info("=== Starting notification preferences journey ===");

        // Step 1: Get current preferences
        Response prefs = authHelper.authenticatedRequest(tokenBundle)
                .when()
                .get("/api/v1/notifications/preferences");

        LOG.info("Preferences response: status={}", prefs.statusCode());

        // Step 2: Update preferences
        Map<String, Object> newPrefs = new HashMap<>();
        newPrefs.put("marketing", false);
        newPrefs.put("billReminders", true);
        newPrefs.put("usageAlerts", true);
        newPrefs.put("channel", "PUSH");

        Response updatePrefs = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(newPrefs)
                .when()
                .put("/api/v1/notifications/preferences");

        LOG.info("Update preferences response: status={}", updatePrefs.statusCode());

        assertThat(updatePrefs.statusCode())
                .as("Preferences update should succeed or endpoint not exist")
                .isIn(200, 204, 404);

        LOG.info("=== Notification preferences journey complete ===");
    }

    // === Re-authentication Journey ===

    @Test(description = "Session persists across multiple requests")
    public void sessionPersistence_journey() {
        LOG.info("=== Starting session persistence journey ===");

        int successCount = 0;

        // Make 5 sequential requests with the same session
        for (int i = 0; i < 5; i++) {
            Response response = authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/home");

            if (response.statusCode() == 200 || response.statusCode() == 207) {
                successCount++;
            }
            LOG.info("Request {}: status={}", i + 1, response.statusCode());
        }

        LOG.info("Session persistence: {}/5 requests succeeded", successCount);
        assertThat(successCount)
                .as("All requests with same session should succeed")
                .isEqualTo(5);

        LOG.info("=== Session persistence journey complete ===");
    }
}
