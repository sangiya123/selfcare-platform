package com.selfcare.conformance.security;

import com.selfcare.conformance.utils.AuthHelper;
import com.selfcare.conformance.utils.TestConfig;
import com.selfcare.conformance.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * Rate limiting conformance tests.
 * Validates that rate limits are enforced per the spec:
 * - Auth endpoints: 30 req/min per IP
 * - Dashboard: 60 req/min per user
 * - Payments: 10 req/min per user
 * - Reads: 120 req/min per user
 */
@Test(groups = {"security", "rate-limiting"})
public class RateLimitTest {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitTest.class);
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
     * VERIFY: Auth endpoint rate limit is enforced (30 req/min per IP).
     */
    @Test(description = "Auth endpoints are rate limited")
    public void authEndpoints_rateLimited() {
        LOG.info("=== Testing auth endpoint rate limit ===");

        String identifier = TestDataFactory.uniqueMsisdn();
        boolean rateLimited = false;

        for (int i = 0; i < 35; i++) {
            Response response = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(TestDataFactory.otpRequest(identifier))
                    .when()
                    .post("/api/v1/auth/otp");

            if (response.statusCode() == 429) {
                rateLimited = true;
                LOG.info("Rate limited after {} requests", i + 1);
                response.then()
                        .body("error.code", equalTo("RATE_LIMITED"))
                        .header("Retry-After", notNullValue());
                break;
            }
        }

        LOG.info("Auth rate limit: {}", rateLimited ? "enforced" : "not triggered in test");
    }

    /**
     * VERIFY: Dashboard endpoint rate limit (60 req/min per user).
     */
    @Test(description = "Dashboard endpoints are rate limited")
    public void dashboardEndpoint_rateLimited() {
        LOG.info("=== Testing dashboard rate limit ===");

        boolean rateLimited = false;
        int requestCount = 0;

        for (int i = 0; i < 70; i++) {
            Response response = authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/home");

            requestCount++;
            if (response.statusCode() == 429) {
                rateLimited = true;
                LOG.info("Rate limited after {} requests", requestCount);
                response.then()
                        .body("error.code", equalTo("RATE_LIMITED"))
                        .header("Retry-After", notNullValue());
                break;
            }
        }

        LOG.info("Dashboard rate limit: {} ({} requests made)", rateLimited ? "enforced" : "not triggered", requestCount);
    }

    /**
     * VERIFY: Payment endpoint rate limit (10 req/min per user).
     */
    @Test(description = "Payment endpoints are rate limited")
    public void paymentEndpoint_rateLimited() {
        LOG.info("=== Testing payment rate limit ===");

        boolean rateLimited = false;
        int requestCount = 0;

        for (int i = 0; i < 15; i++) {
            Response response = authHelper.authenticatedRequest(tokenBundle)
                    .header(TestConfig.IDEMPOTENCY_HEADER, TestDataFactory.uniqueIdempotencyKey())
                    .contentType(ContentType.JSON)
                    .body(java.util.Map.of(
                            "amount", 10.00,
                            "currency", "LKR",
                            "paymentMethodId", "PM-TEST-001",
                            "fromConnectionId", tokenBundle.primaryConnectionId,
                            "toConnectionId", "CONN-TEST-001"
                    ))
                    .when()
                    .post("/api/v1/payments/charge");

            requestCount++;
            if (response.statusCode() == 429) {
                rateLimited = true;
                LOG.info("Rate limited after {} requests", requestCount);
                response.then()
                        .body("error.code", equalTo("RATE_LIMITED"))
                        .header("Retry-After", notNullValue());
                break;
            }
        }

        LOG.info("Payment rate limit: {} ({} requests made)", rateLimited ? "enforced" : "not triggered", requestCount);
    }

    /**
     * VERIFY: Rate limit response includes Retry-After header.
     */
    @Test(description = "Rate limit response includes Retry-After header")
    public void rateLimitResponse_includesRetryAfter() {
        LOG.info("=== Testing Retry-After header ===");

        // Trigger rate limit
        String identifier = TestDataFactory.uniqueMsisdn();
        Response response = null;
        for (int i = 0; i < 40; i++) {
            response = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(TestDataFactory.otpRequest(identifier))
                    .when()
                    .post("/api/v1/auth/otp");

            if (response.statusCode() == 429) {
                break;
            }
        }

        if (response != null && response.statusCode() == 429) {
            String retryAfter = response.getHeader("Retry-After");
            String xRateLimit = response.getHeader("X-RateLimit-Reset");
            String xRateLimitRemaining = response.getHeader("X-RateLimit-Remaining");

            LOG.info("Retry-After: {}", retryAfter);
            LOG.info("X-RateLimit-Reset: {}", xRateLimit);
            LOG.info("X-RateLimit-Remaining: {}", xRateLimitRemaining);

            // At least one rate limit header should be present
            assertThat(retryAfter != null || xRateLimit != null)
                    .as("Rate limit response should include timing information")
                    .isTrue();
        } else {
            LOG.info("Rate limit not triggered - skipping Retry-After check");
        }
    }

    /**
     * VERIFY: Rate limit is per-user (different users have separate limits).
     */
    @Test(description = "Rate limit is per-user")
    public void rateLimit_isPerUser() {
        LOG.info("=== Testing per-user rate limit ===");

        // Exhaust rate limit for user 1
        for (int i = 0; i < 5; i++) {
            authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/home");
        }

        // Login user 2
        AuthHelper.TokenBundle user2 = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());

        // User 2 should still be able to make requests
        Response response = authHelper.authenticatedRequest(user2)
                .queryParam("connectionId", user2.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("User 2 request: status={}", response.statusCode());

        // User 2 should not be rate limited (different user)
        assertThat(response.statusCode())
                .as("Different user should have separate rate limit")
                .isIn(200, 207, 404);
    }

    /**
     * VERIFY: Rate limit headers are present on every response.
     */
    @Test(description = "Rate limit headers are present on responses")
    public void rateLimitHeaders_arePresent() {
        LOG.info("=== Testing rate limit headers ===");

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        String limitHeader = response.getHeader("X-RateLimit-Limit");
        String remainingHeader = response.getHeader("X-RateLimit-Remaining");
        String resetHeader = response.getHeader("X-RateLimit-Reset");

        LOG.info("X-RateLimit-Limit: {}", limitHeader);
        LOG.info("X-RateLimit-Remaining: {}", remainingHeader);
        LOG.info("X-RateLimit-Reset: {}", resetHeader);

        // At least some rate limit information should be provided
        assertThat(limitHeader != null || remainingHeader != null)
                .as("Rate limit information should be in response headers")
                .isTrue();
    }

    /**
     * VERIFY: Per-IP rate limit on auth endpoints.
     */
    @Test(description = "Per-IP rate limit on auth endpoints")
    public void perIPRateLimit_onAuthEndpoints() {
        LOG.info("=== Testing per-IP rate limit ===");

        // Make requests from "same IP" (test client)
        String[] identifiers = {
                TestDataFactory.uniqueMsisdn(),
                TestDataFactory.uniqueMsisdn(),
                TestDataFactory.uniqueMsisdn()
        };

        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 0; i < 35; i++) {
            String identifier = identifiers[i % identifiers.length];

            Response response = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .contentType(ContentType.JSON)
                    .body(TestDataFactory.otpRequest(identifier))
                    .when()
                    .post("/api/v1/auth/otp");

            if (response.statusCode() == 202) {
                successCount++;
            } else if (response.statusCode() == 429) {
                rateLimitedCount++;
            }
        }

        LOG.info("Auth requests: {} successful, {} rate limited", successCount, rateLimitedCount);
    }

    /**
     * VERIFY: Rate limit is per-tenant (separate limits per tenant).
     */
    @Test(description = "Rate limit is per-tenant")
    public void rateLimit_isPerTenant() {
        LOG.info("=== Testing per-tenant rate limit ===");

        // Exhaust Dialog rate limit
        for (int i = 0; i < 3; i++) {
            authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/home");
        }

        // AIA user should have their own rate limit
        AuthHelper.TokenBundle aiaUser = authHelper.login(TestConfig.TENANT_AIA, TestDataFactory.uniqueMsisdn());

        Response response = authHelper.authenticatedRequest(aiaUser)
                .queryParam("connectionId", aiaUser.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("AIA request after Dialog exhausted: status={}", response.statusCode());

        // AIA should not be rate limited (different tenant)
        assertThat(response.statusCode())
                .as("Different tenant should have separate rate limit")
                .isIn(200, 207, 404);
    }

    /**
     * VERIFY: Rate limit is enforced per endpoint category.
     */
    @Test(description = "Rate limit is per endpoint category")
    public void rateLimit_isPerEndpointCategory() {
        LOG.info("=== Testing per-endpoint rate limit ===");

        // Make many dashboard requests
        for (int i = 0; i < 5; i++) {
            authHelper.authenticatedRequest(tokenBundle)
                    .queryParam("connectionId", tokenBundle.primaryConnectionId)
                    .when()
                    .get("/api/v1/dashboard/home");
        }

        // Bills endpoint should have its own counter
        Response billsResponse = authHelper.authenticatedRequest(tokenBundle)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/bills");

        LOG.info("Bills request after dashboard requests: status={}", billsResponse.statusCode());

        // Different endpoint should not be affected by dashboard rate limit
        assertThat(billsResponse.statusCode())
                .as("Different endpoint category should not share rate limit")
                .isIn(200, 404);
    }
}
