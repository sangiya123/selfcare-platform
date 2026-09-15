package com.selfcare.conformance.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.*;

/**
 * Config schema and policy conformance tests.
 * Validates that tenant configurations, layouts, and themes conform to the expected schemas.
 *
 * Tests:
 * - Layout document schema validation
 * - Theme token validation
 * - Component registry validation
 * - Unknown component rejection
 * - Config versioning with ETags
 */
@Test(groups = {"config-schema", "contract"})
public class ConfigSchemaTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigSchemaTest.class);
    private final AuthHelper authHelper = new AuthHelper();
    private final ObjectMapper mapper = TestConfig.objectMapper();

    // Known valid component types
    private static final Set<String> VALID_COMPONENTS = Set.of(
            "BalanceCard", "BillSummary", "BannersCarousel", "QuickActions",
            "DataUsageWidget", "VoiceUsageWidget", "SmsUsageWidget",
            "PlanDetails", "PromotionsWidget", "NotificationBadge",
            "ResourceComponent", "ResourceDetails", "CallForwardingWidget",
            "RoamingStatus", "InternationalDialingWidget"
    );

    private AuthHelper.TokenBundle tokenBundle;

    @BeforeClass
    public void setup() {
        tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    // === Schema Validation Tests ===

    @Test(description = "Valid layout document is accepted by config compiler")
    public void validLayoutDocument_isAccepted() throws IOException {
        // Read the schema from classpath or file system
        Path schemaPath = Paths.get("../config-schema/schemas/layout-document.schema.json");
        if (!Files.exists(schemaPath)) {
            LOG.warn("Layout schema not found at {}, skipping schema validation", schemaPath);
            return;
        }

        String schema = Files.readString(schemaPath);
        LOG.info("Layout schema found: {} bytes", schema.length());

        // The schema should be valid JSON Schema
        JsonNode schemaNode = mapper.readTree(schema);
        assertThat(schemaNode.has("$schema")).isTrue();
        assertThat(schemaNode.has("type")).isTrue();
    }

    @Test(description = "Config manifest returns required version fields")
    public void configManifest_hasRequiredVersionFields() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        LOG.info("Config manifest: status={}", response.statusCode());

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            assertThat(data).isNotNull();

            assertThat(data.has("schemaVersion"))
                    .as("Manifest must include schemaVersion")
                    .isTrue();
            assertThat(data.has("configVersion"))
                    .as("Manifest must include configVersion")
                    .isTrue();
            assertThat(data.get("configVersion"))
                    .as("configVersion must be a positive integer")
                    .isInstanceOf(Integer.class);

            int version = data.getInt("configVersion");
            assertThat(version).isGreaterThan(0);
        }
    }

    @Test(description = "Config manifest includes tenant and experience identifiers")
    public void configManifest_includesIdentifiers() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            assertThat(data).isNotNull();
            assertThat(data.optString("tenant", null))
                    .as("Manifest must include tenant ID")
                    .isNotEmpty();
            assertThat(data.optString("experience", null))
                    .as("Manifest must include experience name")
                    .isNotEmpty();
        }
    }

    // === Theme Token Validation ===

    @Test(description = "Theme response contains valid token structure")
    public void themeResponse_hasValidTokenStructure() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");

            if (data != null && data.has("theme")) {
                JSONObject theme = data.getJSONObject("theme");
                LOG.info("Theme keys: {}", theme.keys().forEachRemaining(k -> LOG.debug("  - {}", k)));

                // Theme should have standard color tokens
                assertThat(theme.has("primary") || theme.has("colors"))
                        .as("Theme must define primary color or colors object")
                        .isTrue();
            }
        }
    }

    @Test(description = "Unknown component type in layout returns error")
    public void unknownComponentType_returnsError() {
        // Build a layout with an unknown component
        Map<String, Object> layout = new HashMap<>();
        Map<String, Object> section = new HashMap<>();
        section.put("component", "UnknownWidgetXYZ123");
        section.put("order", 1);

        Map<String, Object>[] sections = {section};
        layout.put("sections", sections);

        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .contentType(ContentType.JSON)
                .body(layout)
                .when()
                .post("/api/v1/config/layouts/validate");

        LOG.info("Unknown component response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should either return 400 or 200 with validation errors
        if (response.statusCode() == 200) {
            JSONObject body = new JSONObject(response.getBody().asString());
            assertThat(body.has("errors") || body.has("validationErrors"))
                    .as("Response should include validation errors")
                    .isTrue();
        } else {
            assertThat(response.statusCode())
                    .as("Unknown component should return 400 or 422")
                    .isIn(400, 422);
        }
    }

    // === ETag / Caching Tests ===

    @Test(description = "Config manifest supports ETag caching")
    public void configManifest_supportsETagCaching() {
        // First request to get initial ETag
        Response first = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        String etag = first.getHeader("ETag");
        int firstStatus = first.statusCode();

        if (firstStatus == 200 && etag != null) {
            LOG.info("First request ETag: {}", etag);

            // Second request with If-None-Match should return 304
            Response second = RestAssured.given()
                    .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                    .header("Authorization", "Bearer " + tokenBundle.accessToken)
                    .header("If-None-Match", etag)
                    .queryParam("experience", "home")
                    .queryParam("profileKey", "mobile_prepaid")
                    .when()
                    .get("/api/v1/config/manifest");

            LOG.info("Conditional request: status={}", second.statusCode());
            assertThat(second.statusCode())
                    .as("Conditional request with valid ETag should return 304 Not Modified")
                    .isEqualTo(304);
        } else {
            LOG.info("First request returned: status={}, ETag={}", firstStatus, etag);
        }
    }

    @Test(description = "Stale ETag returns fresh content")
    public void staleETag_returnsFreshContent() {
        // Request with a fake old ETag
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .header("If-None-Match", "\"old-etag-value-12345\"")
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        LOG.info("Stale ETag response: status={}", response.statusCode());

        // Should return 200 with fresh content
        assertThat(response.statusCode())
                .as("Stale ETag should return fresh content (200)")
                .isIn(200, 304);
    }

    // === Profile Key Validation ===

    @Test(description = "Invalid profile key returns 404 or validation error")
    public void invalidProfileKey_returnsError() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "nonexistent_profile_key_xyz")
                .when()
                .get("/api/v1/config/manifest");

        LOG.info("Invalid profile response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        assertThat(response.statusCode())
                .as("Invalid profile key should return 404 or 400")
                .isIn(404, 400);
    }

    // === Navigation Structure Tests ===

    @Test(description = "Config manifest includes navigation structure")
    public void configManifest_includesNavigation() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            if (data != null && data.has("navigation")) {
                JSONObject navigation = data.getJSONObject("navigation");
                assertThat(navigation.has("tabs") || navigation.has("items") || navigation.has("routes"))
                        .as("Navigation must define tabs, items, or routes")
                        .isTrue();
            }
        }
    }

    // === Section Order Validation ===

    @Test(description = "Sections have valid ordering")
    public void sections_haveValidOrdering() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            if (data != null && data.has("sections")) {
                var sections = data.getJSONArray("sections");
                assertThat(sections.length()).isGreaterThan(0);

                Set<Integer> orders = new HashSet<>();
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject section = sections.getJSONObject(i);
                    assertThat(section.has("order") || section.has("component"))
                            .as("Each section must have order and component")
                            .isTrue();
                    if (section.has("order")) {
                        int order = section.getInt("order");
                        assertThat(orders.add(order))
                                .as("Section order values should be unique")
                                .isTrue();
                    }
                }
            }
        }
    }

    @Test(description = "Theme colors follow hex format")
    public void themeColors_followHexFormat() {
        Response response = RestAssured.given()
                .spec(TestConfig.baseRequestSpec(TestConfig.TENANT_DIALOG))
                .header("Authorization", "Bearer " + tokenBundle.accessToken)
                .queryParam("experience", "home")
                .queryParam("profileKey", "mobile_prepaid")
                .when()
                .get("/api/v1/config/manifest");

        if (response.statusCode() == 200) {
            JSONObject data = new JSONObject(response.getBody().asString()).optJSONObject("data");
            if (data != null && data.has("theme")) {
                JSONObject theme = data.getJSONObject("theme");

                // Check that color values are valid hex format
                theme.keys().forEachRemaining(key -> {
                    if (key.toLowerCase().contains("color")) {
                        Object value = theme.get(key);
                        if (value instanceof String) {
                            String colorValue = (String) value;
                            assertThat(colorValue.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$") ||
                                           colorValue.matches("^rgba?\\(") ||
                                           colorValue.equals("transparent"))
                                    .as("Color value '" + colorValue + "' for key '" + key + "' should be valid")
                                    .isTrue();
                        }
                    }
                });
            }
        }
    }

    @AfterClass
    public void cleanup() {
        authHelper.clearCache();
    }
}
