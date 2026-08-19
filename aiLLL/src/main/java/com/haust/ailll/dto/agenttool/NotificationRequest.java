package com.haust.ailll.dto.agenttool;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NotificationRequest(
        @NotNull Long ticketId,
        @Pattern(regexp = "IN_APP|EMAIL") String channel
) {
}
