package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(
        @Size(max = 120) String title,
        @Size(max = 4000) String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status
) {
}
