package com.omobio.conformance.utils;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.specification.RequestSpecification;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Centralized configuration for all conformance tests.
 * Reads system properties to allow override of base URL, tenant, and other settings.
 */
public final class TestConfig {

    public static final String BASE_URL = System.getProperty("omobio.base-url", "http://localhost:8080");
    public static final String DEFAULT_TENANT = System.getProperty("omobio.tenant", "dialog-lk");
    public static final String ADMIN_TENANT = System.getProperty("omobio.admin-tenant", "aia-lk");
    public static final int WIREMOCK_PORT = Integer.parseInt(System.getProperty("wiremock.port", "8090"));
    public static final String WIREMOCK_URL = "http://localhost:" + WIREMOCK_PORT;

    public static final String TENANT_DIALOG = "dialog-lk";
    public static final String TENANT_AIA = "aia-lk";
    public static final String TENANT_HUTCH = "hutch-lk";
    public static final String TENANT_AIRTEL = "airtel-lk";

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    public static final String STEP_UP_TOKEN_HEADER = "X-Step-Up-Token";

    public static final int DEFAULT_TIMEOUT_MS = 30_000;
    public static final int LONG_TIMEOUT_MS = 60_000;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private TestConfig() {
    }

    public static synchronized void configureRestAssured() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", DEFAULT_TIMEOUT_MS)
                        .setParam("http.socket.timeout", DEFAULT_TIMEOUT_MS))
                .objectMapperConfig(new ObjectMapperConfig().jackson2ObjectMapperFactory((cls, charset) -> {
                    ObjectMapper mapper = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    return mapper;
                }))
                .jsonConfig(JsonPathConfig.jsonPathConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL));

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    public static RequestSpecification baseRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader(TENANT_HEADER, DEFAULT_TENANT)
                .addHeader(CORRELATION_ID_HEADER, "conformance-" + java.util.UUID.randomUUID())
                .build();
    }

    public static RequestSpecification baseRequestSpec(String tenantId) {
        return new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader(TENANT_HEADER, tenantId)
                .addHeader(CORRELATION_ID_HEADER, "conformance-" + java.util.UUID.randomUUID())
                .build();
    }

    public static ObjectMapper objectMapper() {
        return MAPPER;
    }
}
