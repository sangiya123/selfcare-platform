package com.selfcare.ai.gateway.service;

import com.selfcare.ai.service.PiiMaskingService;
import com.selfcare.ai.service.PiiMaskingService.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PiiMaskingServiceTest {

    private PiiMaskingService service;

    @BeforeEach
    void setUp() {
        service = new PiiMaskingService();
    }

    @Test
    @DisplayName("mask detects and partially redacts email")
    void maskEmail() {
        String result = service.mask("Contact me at john.doe@example.com please");
        assertThat(result).doesNotContain("john.doe");
        assertThat(result).contains("****");
        assertThat(result).contains("please");
    }

    @Test
    @DisplayName("mask redacts phone numbers")
    void maskPhone() {
        String result = service.mask("Call me at +94 77 123 4567");
        assertThat(result).doesNotContain("77 123 4567");
    }

    @Test
    @DisplayName("mask redacts MSISDN")
    void maskMsisdn() {
        String result = service.mask("Send SMS to 94771234567");
        assertThat(result).doesNotContain("94771234567");
    }

    @Test
    @DisplayName("mask fully redacts JWTs")
    void maskJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        String result = service.mask("Token: " + jwt);
        assertThat(result).doesNotContain(jwt);
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("mask redacts secret keys")
    void maskSecret() {
        String result = service.mask("api_key=abc123def456ghi789jkl012mno");
        assertThat(result).doesNotContain("abc123def456ghi789jkl012mno");
    }

    @Test
    @DisplayName("mask handles multiple PII types in one string")
    void maskMultiple() {
        String result = service.mask("Email: a@b.com, Phone: 94771234567, JWT: " +
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc");
        assertThat(result).doesNotContain("a@b.com");
        assertThat(result).doesNotContain("94771234567");
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    @DisplayName("mask leaves non-PII text alone")
    void maskLeavesPlainTextAlone() {
        String result = service.mask("Hello world, just normal text here.");
        assertThat(result).isEqualTo("Hello world, just normal text here.");
    }

    @Test
    @DisplayName("mask handles null and empty")
    void maskNullEmpty() {
        assertThat(service.mask(null)).isNull();
        assertThat(service.mask("")).isEqualTo("");
    }

    @Test
    @DisplayName("classify returns all PII matches with positions")
    void classify() {
        var matches = service.classify("Email: a@b.com, MSISDN: 94771234567");
        assertThat(matches).isNotEmpty();
        assertThat(matches.stream().map(PiiMaskingService.PiiMatch::type))
                .contains(PiiType.EMAIL, PiiType.MSISDN);
    }

    @Test
    @DisplayName("maskMap recursively masks nested structures")
    void maskMap() {
        Map<String, Object> input = Map.of(
                "user", Map.of("email", "a@b.com", "name", "Alice"),
                "msisdn", "94771234567"
        );
        Map<String, Object> masked = service.maskMap(input);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) masked.get("user");
        assertThat(user.get("name")).isEqualTo("Alice");
        assertThat((String) user.get("email")).doesNotContain("a@b.com");
        assertThat((String) masked.get("msisdn")).doesNotContain("94771234567");
    }

    @Test
    @DisplayName("maskList masks each string element")
    void maskList() {
        List<Object> input = List.of("a@b.com", "plain text", "94771234567");
        List<Object> masked = service.maskList(input);
        assertThat((String) masked.get(0)).doesNotContain("a@b.com");
        assertThat(masked.get(1)).isEqualTo("plain text");
        assertThat((String) masked.get(2)).doesNotContain("94771234567");
    }
}
