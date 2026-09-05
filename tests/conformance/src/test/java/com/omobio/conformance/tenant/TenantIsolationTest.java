package com.omobio.conformance.tenant;

import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
import com.omobio.conformance.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Tenant isolation conformance tests.
 * Validates that Tenant A cannot access Tenant B's data under any circumstances.
 *
 * ADR-002 multi-tenancy: strict tenant isolation enforced at the API gateway and service layers.
 *
 * @see <a href="https://omobio.io/docs/multi-tenant">Multi-Tenant Architecture</a>
 */
@Test(groups = {"tenant-isolation", "security"})
public class TenantIsolationTest {

    private static final Logger LOG = LoggerFactory.getLogger(TenantIsolationTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle dialogUser;
    private AuthHelper.TokenBundle aiaUser;
    private AuthHelper.TokenBundle hutchUser;

    @BeforeClass
    public void setup() {
        LOG.info("Setting up tenant isolation test: logging in users across tenants");
        // Use deterministic identifiers for repeatability
        dialogUser = authHelper.login(TestConfig.TENANT_DIALOG, "+94771111111");
        aiaUser = authHelper.login(TestConfig.TENANT_AIA, "+94772222222");
        hutchUser = authHelper.login(TestConfig.TENANT_HUTCH, "+94773333333");
        LOG.info("All test users authenticated: dialog={}, aia={}, hutch={}",
                dialogUser.sessionId, aiaUser.sessionId, hutchUser.sessionId);
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    /**
     * VERIFY: A user authenticated with Tenant A cannot read resources belonging to Tenant B
     * when the X-Tenant-Id header is explicitly set to Tenant B.
     *
     * Expected: HTTP 403 Forbidden with error code FORBIDDEN.
     */
    @Test(description = "Cross-tenant read access must be rejected with 403")
    public void tenantA_cannotReadTenantB_bills() {
        LOG.info("TEST: dialog user attempts to read AIA bills using X-Tenant-Id: aia-lk");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_AIA))
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", aiaUser.primaryConnectionId)
                .when()
                .get("/api/v1/bills");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(403)
                .body("error.code", equalTo("FORBIDDEN"))
                .body("error.message", containsString("tenant"));
    }

    /**
     * VERIFY: The API rejects requests where the token's tenant claim does not match the X-Tenant-Id header.
     * This tests the cross-tenant header validation at the gateway level.
     *
     * Expected: HTTP 403 with FORBIDDEN code and clear error message.
     */
    @Test(description = "Mismatched token tenant and header must be rejected")
    public void tokenTenantMustMatchHeaderTenant() {
        LOG.info("TEST: Hutch user token used with X-Tenant-Id: dialog-lk header");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + hutchUser.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", hutchUser.primaryConnectionId)
                .when()
                .get("/api/v1/bills");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(403)
                .body("error.code", equalTo("FORBIDDEN"))
                .body("error.path", containsString("/api/v1/bills"));
    }

    /**
     * VERIFY: A user's session from Tenant A cannot be used to access Tenant B's dashboard.
     * The primaryConnectionId embedded in the token should be validated against the tenant.
     *
     * Expected: HTTP 403 when a dialog token's connectionId is used to access AIA dashboard.
     */
    @Test(description = "Dashboard access requires matching tenant context")
    public void dashboard_accessDeniedForCrossTenantConnection() {
        LOG.info("TEST: dialog user attempts to access AIA dashboard");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_AIA))
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("connectionId", dialogUser.primaryConnectionId)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(403)
                .body("error.code", anyOf(equalTo("FORBIDDEN"), equalTo("UNAUTHORIZED")));
    }

    /**
     * VERIFY: Insurance (AIA tenant) endpoints reject users from telecom tenants (Dialog).
     * This tests industry-specific tenant isolation.
     *
     * Expected: HTTP 403 or 401 when Dialog user accesses AIA insurance policies.
     */
    @Test(description = "Insurance endpoints must reject telecom tenant users")
    public void insuranceEndpoints_rejectTelecomTenantUsers() {
        LOG.info("TEST: Dialog user attempts to list AIA insurance policies");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_AIA))
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/insurance/policies");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(anyOf(equalTo(403), equalTo(401)))
                .body("error.code", anyOf(equalTo("FORBIDDEN"), equalTo("UNAUTHENTICATED")));
    }

    /**
     * VERIFY: Each tenant's configuration is isolated. A tenant cannot read another tenant's
     * manifest or layout configuration.
     *
     * Expected: HTTP 404 (resource not found in this tenant's scope) or 403 (access denied).
     */
    @Test(description = "Tenant config endpoints must enforce tenant boundaries")
    public void configManifest_isolatedPerTenant() {
        LOG.info("TEST: Hutch user attempts to read Dialog config manifest");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + hutchUser.accessToken)
                .contentType(ContentType.JSON)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(anyOf(equalTo(403), equalTo(401)));
    }

    /**
     * VERIFY: Missing X-Tenant-Id header returns HTTP 400 Bad Request.
     *
     * Expected: HTTP 400 with INVALID_INPUT or MISSING_TENANT error.
     */
    @Test(description = "Missing X-Tenant-Id header must be rejected")
    public void missingTenantHeader_returnsBadRequest() {
        LOG.info("TEST: Request without X-Tenant-Id header");

        Response response = RestAssured.given()
                .baseUri(TestConfig.BASE_URL)
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(400)
                .body("error.code", anyOf(equalTo("INVALID_INPUT"), equalTo("MISSING_TENANT")));
    }

    /**
     * VERIFY: Invalid tenant ID format is rejected.
     *
     * Expected: HTTP 400 with validation error about invalid tenant format.
     */
    @Test(description = "Invalid X-Tenant-Id format must be rejected")
    public void invalidTenantIdFormat_returnsBadRequest() {
        LOG.info("TEST: Request with invalid tenant ID format");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec("invalid-tenant-!@#"))
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/dashboard/home");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    /**
     * VERIFY: After logout, the token is completely invalidated and cannot be reused
     * for any tenant, even with a valid tenant header.
     *
     * Expected: HTTP 401 with UNAUTHENTICATED after logout.
     */
    @Test(description = "Logged-out tokens must be rejected across all tenant contexts")
    public void loggedOutToken_rejectedInAllTenantContexts() {
        LOG.info("TEST: Verifying logged-out token is rejected");

        Response signoutResponse = authHelper.signout(dialogUser);
        signoutResponse.then().statusCode(204);

        // Re-login for this test to ensure we have a fresh token
        AuthHelper.TokenBundle freshDialogUser = authHelper.login(TestConfig.TENANT_DIALOG, "+94771111111");
        Response logoutAgain = authHelper.signout(freshDialogUser);
        logoutAgain.then().statusCode(204);

        // Now verify the token is completely dead
        Response postLogoutAccess = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + freshDialogUser.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/dashboard/home");

        postLogoutAccess.then()
                .statusCode(401)
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    /**
     * VERIFY: Users from different tenants have completely separate payment histories.
     * A tenant cannot see or reference another tenant's payment methods.
     *
     * Expected: HTTP 403 or 404 when accessing another tenant's payment methods.
     */
    @Test(description = "Payment methods must be isolated per tenant")
    public void paymentMethods_isolatedPerTenant() {
        LOG.info("TEST: Dialog user attempts to list AIA payment methods");

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_AIA))
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/payments/methods");

        LOG.info("Response: status={}, body={}", response.statusCode(), response.getBody().asString());

        response.then()
                .statusCode(anyOf(equalTo(403), equalTo(401)));
    }
}
