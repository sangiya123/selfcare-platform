package com.selfcare.support.web.dto;

import java.util.List;

/**
 * Request payload to create a support ticket / service request.
 */
public record TicketRequestDto(
        String subject,
        String category,
        String priority,
        String description,
        String connectionId,
        String productCode,
        List<String> attachments
) {}