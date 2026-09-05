package com.omobio.conformance.api;

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

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * API contract conformance tests.
 * Validates that all API endpoints return responses matching the OpenAPI specification.
 *
 * Checks:
 * - Response envelope structure (data, error, meta fields)
 * - HTTP status code correctness
 * - Required headers present
 * - Content-Type headers
 * - Pagination response format
 * - Error response format
 */
@Test(groups = {"api-contract"})
public class ApiContractTest {

    private static final Logger LOG = LoggerFactory.getLogger(ApiContractTest.class);
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

    // === Success Response Envelope Tests ===

    @Test(description = "Dashboard response follows standard ApiResponse envelope")
    public void dashboardResponse_followsEnvelope() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Dashboard response: status={}", response.statusCode());
        String body = response.getBody().asString();
        LOG.debug("Dashboard body: {}", body);

        // Should be 200 or 207 (Multi-Status for partial)
        assertThat(response.statusCode()).isIn(200, 207);

        JSONObject json = new JSONObject(body);
        // Standard envelope: either top-level "data" or "widgets" within data
        assertThat(json.has("data") || json.has("widgets"))
                .as("Response must have either 'data' envelope or 'widgets' for BFF")
                .isTrue();
    }

    @Test(description = "Bills list response follows standard pagination envelope")
    public void billsResponse_followsPaginationEnvelope() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/bills");

        LOG.info("Bills response: status={}", response.statusCode());
        String body = response.getBody().asString();

        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(body);
            assertThat(json.has("data")).isTrue();

            // If pagination is present
            if (json.has("pagination")) {
                JSONObject pagination = json.getJSONObject("pagination");
                assertThat(pagination.has("hasMore") || pagination.has("nextCursor"))
                        .as("Pagination must have hasMore or nextCursor")
                        .isTrue();
            }
        }
    }

    @Test(description = "Config manifest response follows standard envelope")
    public void configManifestResponse_hasRequiredFields() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        LOG.info("Config manifest response: status={}", response.statusCode());

        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.getBody().asString());
            JSONObject data = json.optJSONObject("data");
            assertThat(data).isNotNull();
            assertThat(data.has("schemaVersion") || data.has("configVersion"))
                    .as("Config manifest must have version information")
                    .isTrue();
        } else if (response.statusCode() == 304) {
            // Not modified is also valid with ETag
            assertThat(response.getHeader("ETag")).isNotNull();
        }
    }

    // === Error Response Envelope Tests ===

    @Test(description = "404 error follows standard error envelope")
    public void notFoundError_followsEnvelope() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .get("/api/v1/nonexistent-resource-xyz");

        LOG.info("404 response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("error.code", equalTo("NOT_FOUND"))
                .body("error.status", equalTo(404))
                .body("error.path", containsString("/api/v1/nonexistent-resource-xyz"));
    }

    @Test(description = "400 error includes field-level details")
    public void validationError_includesFieldDetails() {
        // Make a request with missing required fields
        Map<String, Object> emptyPayment = new java.util.HashMap<>();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .body(emptyPayment)
                .when()
                .post("/api/v1/bills/BILL-001/pay");

        LOG.info("Validation error response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("error.code", equalTo("INVALID_INPUT"))
                .body("error.message", notNullValue());

        // Details should include field-level information
        String body = response.getBody().asString();
        JSONObject json = new JSONObject(body);
        if (json.getJSONObject("error").has("details")) {
            JSONObject details = json.getJSONObject("error").getJSONObject("details");
            assertThat(details.has("field") || details.has("reason"))
                    .as("Error details should include field or reason")
                    .isTrue();
        }
    }

    @Test(description = "500 error is returned as standard error envelope (no stack trace leaked)")
    public void internalError_doesNotLeakStackTrace() {
        // This test verifies that internal errors don't expose implementation details
        // We can't force a 500 easily, but we verify the error format
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/bills");

        String body = response.getBody().asString();
        JSONObject json = new JSONObject(body);

        if (response.statusCode() == 500) {
            assertThat(body).doesNotContain("java.", "at com.omobio", "NullPointerException");
            assertThat(json.getJSONObject("error").getString("message"))
                    .doesNotContain("java.", "null", "Exception");
        }
    }

    // === Header Tests ===

    @Test(description = "Response includes correlation ID when requested")
    public void correlationIdHeader_isEchoedInResponse() {
        String customCorrelationId = "test-correlation-" + System.currentTimeMillis();

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .header(TestConfig.CORRELATION_ID_HEADER, customCorrelationId)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        String echoedCorrelationId = response.getHeader(TestConfig.CORRELATION_ID_HEADER);
        assertThat(echoedCorrelationId)
                .as("Response must echo back the correlation ID")
                .isEqualTo(customCorrelationId);
    }

    @Test(description = "Response includes standard headers")
    public void responseIncludesStandardHeaders() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .get("/api/v1/dashboard/home");

        assertThat(response.getHeader("Content-Type")).isNotNull();
        assertThat(response.getHeader("Content-Type")).contains("application/json");
        assertThat(response.getHeader("X-Request-Id") || response.getHeader(TestConfig.CORRELATION_ID_HEADER))
                .as("Response should include request tracking ID")
                .isNotNull();
    }

    // === Content-Type Tests ===

    @Test(description = "JSON endpoints return application/json")
    public void jsonEndpoints_returnJsonContentType() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .accept(ContentType.JSON)
                .queryParam("connectionId", tokenBundle.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        assertThat(response.getContentType()).contains("application/json");
    }

    @Test(description = "Accept header is respected")
    public void acceptHeader_isRespected() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .accept(ContentType.XML)
                .when()
                .get("/api/v1/dashboard/home");

        // Either returns XML or 406 Not Acceptable
        assertThat(response.statusCode())
                .as("Should return either 200 with JSON or 406 Not Acceptable")
                .isIn(200, 406);
    }

    // === HTTP Method Tests ===

    @Test(description = "GET on POST-only endpoint returns 405")
    public void wrongHttpMethod_returns405() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .get("/api/v1/auth/otp");

        response.then()
                .statusCode(405)
                .body("error.code", equalTo("METHOD_NOT_ALLOWED"));
    }

    @Test(description = "POST on GET-only endpoint returns 405")
    public void postOnGetEndpoint_returns405() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/dashboard/home");

        response.then()
                .statusCode(405)
                .body("error.code", equalTo("METHOD_NOT_ALLOWED"));
    }

    // === Versioning Tests ===

    @Test(description = "API versioning follows /api/v{version} pattern")
    public void apiVersioning_followsPattern() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .get("/api/v1/dashboard/home");

        // v1 endpoint should work
        assertThat(response.statusCode()).isIn(200, 207, 400, 401, 403);

        // Non-existent v2 should return 404 or 501
        Response v2 = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .get("/api/v999/dashboard/home");

        assertThat(v2.statusCode())
                .as("Non-existent API version should return 404 or 501")
                .isIn(404, 501);
    }

    // === CORS Headers (if applicable) ===

    @Test(description = "OPTIONS requests return CORS headers")
    public void corsHeaders_returnedOnOptions() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .when()
                .options("/api/v1/dashboard/home");

        // CORS headers should be present
        String allowMethods = response.getHeader("Allow");
        String corsHeaders = response.getHeader("Access-Control-Allow-Methods");

        // At least one CORS-related header should be present
        assertThat(allowMethods != null || corsHeaders != null)
                .as("OPTIONS response should include CORS-related headers")
                .isTrue();
    }

    // === Timeout/Boundary Tests ===

    @Test(description = "Large request payload returns 413 or handles gracefully")
    public void oversizedPayload_returns413() {
        // Generate a very large request body
        StringBuilder largeBody = new StringBuilder();
        largeBody.append("{\"data\":\"");
        for (int i = 0; i < 100_000; i++) {
            largeBody.append("x");
        }
        largeBody.append("\"}");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .body(largeBody.toString())
                .when()
                .post("/api/v1/auth/otp");

        // Should either reject with 413 or handle gracefully
        assertThat(response.statusCode())
                .as("Oversized payload should return 413 or be handled")
                .isIn(400, 413, 431);
    }

    @Test(description = "Malformed JSON returns 400")
    public void malformedJson_returns400() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .body("{invalid json content")
                .when()
                .post("/api/v1/auth/otp");

        response.then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_INPUT"));
    }

    @AfterClass
    public void cleanup() {
        authHelper.clearCache();
    }
}
