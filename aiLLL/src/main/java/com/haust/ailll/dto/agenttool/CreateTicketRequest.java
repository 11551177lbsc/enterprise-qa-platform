package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority
) {
}
