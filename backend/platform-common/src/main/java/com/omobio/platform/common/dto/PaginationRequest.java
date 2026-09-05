package com.omobio.platform.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Standard pagination request parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

    @Min(0)
    @Builder.Default
    private int page = 0;

    @Min(1)
    @Max(200)
    @Builder.Default
    private int size = 20;

    private String sortBy;
    private String sortDirection = "ASC";
    private String cursor;
}