package com.selfcare.config.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON Schema validation of authorable config at publish time (ADR-004 / v6 rule 3).
 *
 * Loads the bundled JSON Schemas (config-schema/schemas, baked into the jar) in a
 * cached, thread-safe map and validates payloads against them. Validation is invoked
 * by {@link ConfigCompiler#validateDocument} when a layout is published — authoring
 * cannot reach PUBLISHED without passing schema validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSchemaValidator {

    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    private static final SpecVersion.VersionFlag DRAFT = SpecVersion.VersionFlag.V7;

    /** Validate a payload against a bundled schema; returns the list of violations (empty when valid). */
    public List<String> validate(String schemaResource, JsonNode payload) {
        try {
            JsonSchema schema = schemaFor(schemaResource);
            return schema.validate(payload).stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
        } catch (IOException e) {
            log.error("Cannot load schema {}: {}", schemaResource, e.getMessage());
            throw new IllegalStateException("Schema unavailable: " + schemaResource, e);
        }
    }

    /** Validate and fail fast on the first violation. */
    public void validateOrThrow(String schemaResource, JsonNode payload) {
        List<String> errors = validate(schemaResource, payload);
        if (!errors.isEmpty()) {
            String detail = String.join("; ", errors);
            log.warn("Config validation failed ({}): {}", schemaResource, detail);
            throw new IllegalStateException("Config does not conform to " + schemaResource + " — " + detail);
        }
    }

    private JsonSchema schemaFor(String resource) throws IOException {
        JsonSchema schema = cache.get(resource);
        if (schema == null) {
            JsonNode node = objectMapper.readTree(new ClassPathResource("config-schema/" + resource).getInputStream());
            schema = JsonSchemaFactory.getInstance(DRAFT).getSchema(node);
            cache.put(resource, schema);
        }
        return schema;
    }
}