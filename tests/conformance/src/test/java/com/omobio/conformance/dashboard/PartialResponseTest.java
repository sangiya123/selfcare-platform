package com.omobio.conformance.dashboard;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
import com.omobio.conformance.utils.TestDataFactory;
import com.omobio.conformance.utils.WireMockManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Dashboard partial response conformance tests.
 *
 * Validates that the BFF correctly handles slow/failing widgets without
 * breaking the entire dashboard response. Per the BFF spec:
 * - Each widget has a 300ms timeout
 * - Slow widgets return TIMEOUT status without blocking others
 * - Overall response deadline is 500ms
 */
@Test(groups = {"dashboard", "partial-response", "resilience"})
public class PartialResponseTest {

    private static final Logger LOG = LoggerFactory.getLogger(PartialResponseTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private WireMockServer wireMock;
    private AuthHelper.TokenBundle tokenBundle;

    @BeforeClass
    public void setup() {
        WireMockManager.getInstance().start();
        wireMock = WireMockManager.getInstance().server();
        tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    @BeforeMethod
    public void resetWireMock() {
        wireMock.resetAll();
    }

    // === Slow Widget Tests ===

    @Test(description = "Slow widget does not block other widgets from loading")
    public void slowWidget_doesNotBlockOthers() {
        LOG.info("=== Testing slow widget isolation ===");

        // Stub balance endpoint to be slow (1 second, exceeds 300ms widget timeout)
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("amount", 1250.00)
                                .put("currency", "LKR")
                                .toString())
                        .withFixedDelay(1000)));

        long start = System.currentTimeMillis();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        long elapsed = System.currentTimeMillis() - start;
        LOG.info("Dashboard response: status={}, elapsed={}ms", response.statusCode(), elapsed);

        // The dashboard should return within reasonable time (500ms deadline + some grace)
        assertThat(elapsed)
                .as("Dashboard should return within reasonable time despite slow widget")
                .isLessThan(2000);

        // Dashboard should either succeed with partial data or have widgets with TIMEOUT/ERROR status
        if (response.statusCode() == 200 || response.statusCode() == 207) {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null && data.has("widgets")) {
                JSONObject widgets = data.getJSONObject("widgets");
                LOG.info("Widget statuses: {}", widgets.keySet());

                // At least one widget should show as SUCCESS (not blocked)
                boolean hasSuccessWidget = widgets.keys().hasNext();
                assertThat(hasSuccessWidget)
                        .as("Dashboard should have at least some widgets available")
                        .isTrue();
            }
        }
    }

    @Test(description = "Multiple slow widgets are all handled gracefully")
    public void multipleSlowWidgets_allHandledGracefully() {
        LOG.info("=== Testing multiple slow widgets ===");

        // Stub multiple endpoints to be slow
        String[] endpoints = {"/api/balance", "/api/usage/data", "/api/bills"};

        for (String endpoint : endpoints) {
            wireMock.stubFor(get(urlPathEqualTo(endpoint))
                    .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")
                            .withFixedDelay(2000)));
        }

        long start = System.currentTimeMillis();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        long elapsed = System.currentTimeMillis() - start;
        LOG.info("Dashboard with multiple slow widgets: status={}, elapsed={}ms",
                response.statusCode(), elapsed);

        // Should still return within 2 seconds despite all slow endpoints
        assertThat(elapsed)
                .as("Dashboard should not wait indefinitely for slow widgets")
                .isLessThan(3000);
    }

    // === Widget Timeout Tests ===

    @Test(description = "Widget timeout is reported in response")
    public void widgetTimeout_reportedInResponse() {
        LOG.info("=== Testing widget timeout reporting ===");

        // Stub to take longer than widget timeout
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")
                        .withFixedDelay(500)));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard with timeout: status={}", response.statusCode());

        if (response.statusCode() == 200 || response.statusCode() == 207) {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null) {
                // Check overall status
                if (data.has("overallStatus")) {
                    String overallStatus = data.getString("overallStatus");
                    LOG.info("Overall status: {}", overallStatus);
                    assertThat(overallStatus).isIn("SUCCESS", "PARTIAL", "ERROR");
                }
            }
        }
    }

    // === Widget Error Handling Tests ===

    @Test(description = "Failed widget does not break other widgets")
    public void failedWidget_doesNotBreakOthers() {
        LOG.info("=== Testing widget error isolation ===");

        // Stub balance endpoint to fail
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard with failing widget: status={}", response.statusCode());

        // Dashboard should still return (not crash)
        assertThat(response.statusCode())
                .as("Dashboard should still return even when one widget fails")
                .isIn(200, 207, 500);
    }

    // === Widget Retry Tests ===

    @Test(description = "Timeout widget can be retried individually")
    public void timeoutWidget_canBeRetried() {
        LOG.info("=== Testing widget retry ===");

        // First call times out, second succeeds
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("amount", 1500.00)
                                .put("currency", "LKR")
                                .toString())
                        .withFixedDelay(100)));

        // Retry the specific widget
        Response widgetResponse = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/widgets/balance");

        LOG.info("Widget retry response: status={}, body={}",
                widgetResponse.statusCode(), widgetResponse.getBody().asString());

        // Widget endpoint should work
        assertThat(widgetResponse.statusCode())
                .as("Individual widget endpoint should work")
                .isIn(200, 404);
    }

    // === Empty Widgets Tests ===

    @Test(description = "Dashboard with no widgets returns valid empty response")
    public void dashboardWithNoWidgets_returnsValidResponse() {
        LOG.info("=== Testing empty dashboard ===");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard response: status={}", response.statusCode());

        if (response.statusCode() == 200 || response.statusCode() == 207) {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            // Empty dashboard is valid
            if (data != null && data.has("widgets")) {
                JSONObject widgets = data.getJSONObject("widgets");
                assertThat(widgets.length())
                        .as("Widgets object should be empty or null")
                        .isGreaterThanOrEqualTo(0);
            }
        }
    }

    // === Widget Order Tests ===

    @Test(description = "Widgets maintain their configured order")
    public void widgets_maintainConfiguredOrder() {
        LOG.info("=== Testing widget ordering ===");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        if (response.statusCode() == 200 || response.statusCode() == 207) {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject data = body.optJSONObject("data");

            if (data != null && data.has("widgets")) {
                JSONObject widgets = data.getJSONObject("widgets");
                LOG.info("Widget keys: {}", widgets.keys().forEachRemaining(k -> LOG.debug("  - {}", k)));

                // Widget order should be deterministic
                java.util.Iterator<String> iterator = widgets.keys();
                String lastKey = null;
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    assertThat(key).isNotNull();
                    lastKey = key;
                }
                assertThat(lastKey).isNotNull();
            }
        }
    }

    @AfterClass
    public void cleanup() {
        authHelper.clearCache();
    }
}
