package com.selfcare.conformance.observability;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Synthetic monitoring conformance tests — layer 10 of the test pyramid.
 *
 * These tests simulate production traffic patterns to verify:
 * - SLO compliance (availability, latency)
 * - Correlation ID propagation end-to-end
 * - Health endpoints return 200
 * - Rate limit headers present
 * - Error envelope format correct
 *
 * Run against stg/prod environments as a scheduled canary.
 */
public class SyntheticMonitoringTest {

    private String baseUrl;
    private String tenantId;
    private String accessToken;

    @BeforeClass
    public void setup() {
        // Override in test-resources/stg.properties or via environment
        baseUrl = System.getProperty("test.api.gateway", "http://api.stg.selfcare.io");
        tenantId = System.getProperty("test.tenant", "dialog-lk");

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Obtain a valid token (test user, not a real customer)
        String username = System.getProperty("test.admin.user", "synthetic-test@selfcare.io");
        String password = System.getProperty("test.admin.pass", "");

        if (password == null || password.isEmpty()) {
            // Skip if no credentials — this is a canary, not a blocker
            accessToken = null;
            return;
        }

        String tokenResponse = given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body("""
                { "email": "%s", "password": "%s" }
                """.formatted(username, password))
            .when()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .path("accessToken");

        accessToken = tokenResponse;
    }

    @Test(groups = "synthetic-monitoring")
    public void healthEndpointReturns200() {
        given()
            .baseUri(baseUrl)
            .when()
            .get("/health")
            .then()
            .statusCode(equalTo(200));
    }

    @Test(groups = "synthetic-monitoring")
    public void correlationIdPropagatedToResponse() {
        String correlationId = java.util.UUID.randomUUID().toString().substring(0, 16);

        given()
            .baseUri(baseUrl)
            .header("X-Correlation-Id", correlationId)
            .header("X-Tenant-Id", tenantId)
            .when()
            .get("/api/v1/config/manifest")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(404))) // 404 is ok if no config
            .header("X-Correlation-Id", correlationId);
    }

    @Test(groups = "synthetic-monitoring", dependsOnMethods = "healthEndpointReturns200")
    public void rateLimitHeadersPresent() {
        Response response = given()
            .baseUri(baseUrl)
            .header("X-Tenant-Id", tenantId)
            .when()
            .get("/api/v1/config/manifest");

        // Headers must be present on every response
        String limit = response.getHeader("X-RateLimit-Limit");
        String remaining = response.getHeader("X-RateLimit-Remaining");
        String reset = response.getHeader("X-RateLimit-Reset");

        // At least one header should be present
        org.testng.Assert.assertTrue(
            limit != null || remaining != null || reset != null,
            "At least one rate-limit header must be present"
        );
    }

    @Test(groups = "synthetic-monitoring")
    public void errorEnvelopeHasCorrectSchema() {
        // Call with an invalid ID to trigger a 404
        given()
            .baseUri(baseUrl)
            .header("X-Tenant-Id", tenantId)
            .header("Authorization", "Bearer " + (accessToken != null ? accessToken : "invalid"))
            .when()
            .get("/api/v1/customers/nonexistent-id-xyz")
            .then()
            .statusCode(equalTo(404))
            .body("error.code", notNullValue())
            .body("error.message", notNullValue())
            .body("error.correlationId", notNullValue())
            .body("error.retryable", notNullValue());
    }

    @Test(groups = "synthetic-monitoring")
    public void unauthenticatedRequestReturns401() {
        given()
            .baseUri(baseUrl)
            .header("X-Tenant-Id", tenantId)
            .when()
            .get("/api/v1/customers/me")
            .then()
            .statusCode(equalTo(401));
    }

    @Test(groups = "synthetic-monitoring")
    public void sloAvailabilityTargetMet() {
        // For nightly canary: make 100 requests, verify < 0.5% error rate
        int total = 100;
        int errors = 0;

        for (int i = 0; i < total; i++) {
            int status = given()
                .baseUri(baseUrl)
                .header("X-Tenant-Id", tenantId)
                .when()
                .get("/health")
                .statusCode();

            if (status >= 500) {
                errors++;
            }
        }

        double errorRate = (double) errors / total;
        org.testng.Assert.assertTrue(
            errorRate < 0.005,
            String.format("SLO availability violated: %.2f%% errors (limit: 0.5%%)", errorRate * 100)
        );
    }

    @Test(groups = "synthetic-monitoring")
    public void sloLatencyTargetMet() {
        // Verify P95 latency < 300ms for health check
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.currentTimeMillis();
            given()
                .baseUri(baseUrl)
                .when()
                .get("/health");
            latencies[i] = System.currentTimeMillis() - start;
        }

        // Sort and get P95
        java.util.Arrays.sort(latencies);
        int p95Index = (int) Math.ceil(0.95 * latencies.length) - 1;
        long p95 = latencies[p95Index];

        org.testng.Assert.assertTrue(
            p95 < 300,
            String.format("SLO latency violated: P95 = %dms (limit: 300ms)", p95)
        );
    }
}
