package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record KnowledgeGapStatusRequest(
        @NotBlank @Pattern(regexp = "OPEN|IN_REVIEW|RESOLVED") String status
) {
}
