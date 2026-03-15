package com.agile.paybot.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,

        @NotBlank(message = "Request ID is required for idempotency")
        @Size(max = 100, message = "Request ID must not exceed 100 characters")
        String requestId,

        @NotBlank(message = "Session ID is required")
        @Size(max = 100, message = "Session ID must not exceed 100 characters")
        String sessionId,

        List<MessageDTO> conversationHistory
) {
    public ChatRequest {
        if (conversationHistory == null) {
            conversationHistory = List.of();
        }
    }
}
