package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnswerFeedbackRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String runId,
        @NotBlank @Size(max = 4000) String question,
        @NotBlank @Pattern(regexp = "RESOLVED|UNRESOLVED") String outcome,
        @Size(max = 1000) String comment,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confidence
) {
}
