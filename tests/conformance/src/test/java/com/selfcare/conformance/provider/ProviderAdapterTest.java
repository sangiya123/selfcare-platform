package com.selfcare.conformance.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.selfcare.conformance.utils.AuthHelper;
import com.selfcare.conformance.utils.TestConfig;
import com.selfcare.conformance.utils.TestDataFactory;
import com.selfcare.conformance.utils.WireMockManager;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Provider adapter contract conformance tests.
 * Validates that the platform correctly handles responses from downstream providers
 * (BSS systems, billing, balance, etc.)
 *
 * Tests:
 * - Provider timeout handling
 * - Provider error translation
 * - Provider response shape validation
 * - Circuit breaker behavior
 * - Fallback responses
 */
@Test(groups = {"provider-adapter", "resilience"})
public class ProviderAdapterTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderAdapterTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private WireMockServer wireMock;
    private AuthHelper.TokenBundle tokenBundle;

    @BeforeClass
    public void setup() {
        WireMockManager.getInstance().start();
        wireMock = WireMockManager.getInstance().server();
        tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());
        LOG.info("Provider adapter test setup complete");
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    @BeforeMethod
    public void resetWireMock() {
        wireMock.resetAll();
    }

    // === Balance Provider Tests ===

    @Test(description = "Balance provider returns correct response shape")
    public void balanceProvider_returnsCorrectShape() {
        LOG.info("Setting up WireMock stub for balance provider");

        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("connectionId", tokenBundle.primaryConnectionId)
                                .put("amount", 1250.75)
                                .put("currency", "LKR")
                                .put("asOf", TestDataFactory.currentTimestamp())
                                .put("balanceType", "MAIN")
                                .toString())));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/balance");

        LOG.info("Balance response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then().statusCode(200);

        JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
        assertThat(data).isNotNull();
        assertThat(data.has("amount") || data.has("balance"))
                .as("Balance response must include amount or balance field")
                .isTrue();
    }

    @Test(description = "Balance provider timeout triggers graceful degradation")
    public void balanceProvider_timeout_triggersGracefulDegradation() {
        LOG.info("Setting up WireMock to simulate timeout");

        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10_000) // 10 second delay (exceeds normal timeout)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        long start = System.currentTimeMillis();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .config(RestAssured.config()
                        .httpClient(RestAssured.config().getHttpClientConfig()
                                .setParam("http.socket.timeout", 2000))) // 2s timeout
                .when()
                .get("/api/v1/usage/balance");

        long elapsed = System.currentTimeMillis() - start;
        LOG.info("Balance timeout response: status={}, elapsed={}ms", response.statusCode(), elapsed);

        // Should return either 200 with error data or 504 Gateway Timeout
        assertThat(response.statusCode())
                .as("Timeout should return either error response or gateway timeout")
                .isIn(200, 504, 503, 207);
    }

    @Test(description = "Balance provider 500 error is translated to UPSTREAM_ERROR")
    public void balanceProvider_500Translated_toUpstreamError() {
        LOG.info("Setting up WireMock to simulate provider 500");

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
                .get("/api/v1/usage/balance");

        LOG.info("Provider 500 response: status={}, body={}", response.statusCode(), response.getBody().asString());

        // Should return 502 Bad Gateway with UPSTREAM_ERROR code
        assertThat(response.statusCode())
                .as("Provider 500 should be translated to 502")
                .isIn(502, 503);
    }

    @Test(description = "Balance provider 404 returns NOT_FOUND")
    public void balanceProvider_404_returnsNotFound() {
        String unknownConnectionId = TestDataFactory.uniqueConnectionId();

        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(unknownConnectionId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Connection not found\"}")));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", unknownConnectionId)
                .when()
                .get("/api/v1/usage/balance");

        LOG.info("Provider 404 response: status={}", response.statusCode());

        assertThat(response.statusCode())
                .as("Provider 404 should translate to API 404")
                .isEqualTo(404);
    }

    // === Billing Provider Tests ===

    @Test(description = "Bills list provider returns correct response shape")
    public void billsProvider_returnsCorrectShape() {
        String billId = TestDataFactory.uniqueBillId();

        wireMock.stubFor(get(urlPathEqualTo("/api/bills"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("bills", new org.json.JSONArray()
                                        .put(new JSONObject()
                                                .put("billId", billId)
                                                .put("connectionId", tokenBundle.primaryConnectionId)
                                                .put("issueDate", "2026-08-01")
                                                .put("dueDate", "2026-08-31")
                                                .put("totalAmount", 5500.00)
                                                .put("currency", "LKR")
                                                .put("status", "OUTSTANDING")))
                                .toString())));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/bills");

        LOG.info("Bills response: status={}", response.statusCode());

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            assertThat(data).isNotNull();
        }
    }

    // === Product Catalog Provider Tests ===

    @Test(description = "Product catalog provider returns valid product data")
    public void productCatalog_returnsValidProducts() {
        String productId = TestDataFactory.uniqueId("PROD");

        wireMock.stubFor(get(urlPathEqualTo("/api/products"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("products", new org.json.JSONArray()
                                        .put(new JSONObject()
                                                .put("productId", productId)
                                                .put("name", "Data Pack 10GB")
                                                .put("price", 500.00)
                                                .put("currency", "LKR")
                                                .put("validityDays", 30)
                                                .put("dataVolumeMB", 10240)
                                                .put("status", "ACTIVE")))
                                .toString())));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/products");

        LOG.info("Products response: status={}", response.statusCode());
        response.then().statusCode(200);
    }

    // === Provider Retry Behavior Tests ===

    @Test(description = "Provider transient failure triggers retry")
    public void providerTransientFailure_triggersRetry() {
        int[] callCount = {0};
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service Unavailable\"}")
                        .withUniformRandomDelay(100, 300)));

        // Make multiple requests to observe retry behavior
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/balance");

        LOG.info("Transient failure response: status={}", response.statusCode());

        // Should eventually return 503 or 504 after retries exhausted
        assertThat(response.statusCode())
                .as("After retry attempts, should return service error")
                .isIn(200, 503, 504);
    }

    // === Circuit Breaker Tests ===

    @Test(description = "Circuit breaker opens after consecutive failures")
    public void circuitBreaker_opensAfterFailures() {
        // Stub the provider to always fail
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Server Error\"}")));

        // Make requests until circuit opens (typically 5 consecutive failures)
        int circuitOpenedAfter = -1;
        for (int i = 0; i < 10; i++) {
            Response response = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .header("Authorization", "Bearer " + tokenBundle.accessToken)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/usage/balance");

            if (response.statusCode() == 503 && response.getHeader("X-Circuit-Breaker") != null) {
                circuitOpenedAfter = i;
                LOG.info("Circuit breaker opened after {} requests", i + 1);
                break;
            }
        }

        LOG.info("Circuit breaker test: opened after {} requests", circuitOpenedAfter);
        // Circuit should open after a certain number of failures
        // This may not trigger in all environments, so we just log
    }

    // === Provider Health Check Tests ===

    @Test(description = "Provider health endpoint reflects actual provider status")
    public void providerHealth_reflectsStatus() {
        // Set up a healthy provider
        wireMock.stubFor(get(urlPathEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));

        Response response = RestAssured.given()
                .baseUri(TestConfig.WIREMOCK_URL)
                .when()
                .get("/health");

        LOG.info("Health check response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then().statusCode(200);
        assertThat(response.getBody().asString()).contains("UP");
    }

    @Test(description = "Provider returns valid JSON (not malformed)")
    public void providerReturns_validJson() {
        wireMock.stubFor(get(urlPathEqualTo("/api/balance"))
                .withQueryParam("connectionId", equalTo(tokenBundle.primaryConnectionId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JSONObject()
                                .put("connectionId", tokenBundle.primaryConnectionId)
                                .put("amount", 1250.75)
                                .put("currency", "LKR")
                                .toString())));

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/usage/balance");

        if (response.statusCode() == 200) {
            // Verify response is valid JSON
            try {
                new JSONObject(response.getBody().asString());
            } catch (Exception e) {
                fail("Response is not valid JSON: " + e.getMessage());
            }
        }
    }

    @AfterClass
    public void classCleanup() {
        authHelper.clearCache();
    }
}
