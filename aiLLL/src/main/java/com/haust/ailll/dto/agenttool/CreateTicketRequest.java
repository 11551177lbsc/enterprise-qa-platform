package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @Pattern(regexp = "DEVICE|ACCOUNT|ORDER|BILLING|SAFETY|OTHER") String category,
        @Size(max = 120) String productModel,
        @DecimalMin("0.0") @DecimalMax("1.0") Double knowledgeConfidence,
        @Size(max = 500) String escalationReason
) {
    public CreateTicketRequest(String title, String description, String priority) {
        this(title, description, priority, null, null, null, null);
    }
}
