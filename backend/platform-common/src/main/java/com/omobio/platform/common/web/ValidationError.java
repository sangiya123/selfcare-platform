package com.omobio.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single validation error from request validation.
 *
 * Included in ApiException.validationErrors for 400 Bad Request responses.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationError {

    String field;
    String message;
    String rejectedValue;

    public static ValidationError of(String field, String message, Object rejectedValue) {
        return ValidationError.builder()
            .field(field)
            .message(message)
            .rejectedValue(rejectedValue != null ? rejectedValue.toString() : null)
            .build();
    }
}
