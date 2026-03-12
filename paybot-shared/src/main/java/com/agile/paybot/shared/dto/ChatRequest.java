package com.agile.paybot.shared.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "Message cannot be empty")
        String message,

        @NotBlank(message = "Request ID is required for idempotency")
        String requestId,

        @NotBlank(message = "Session ID is required")
        String sessionId,

        List<MessageDTO> conversationHistory
) {
    public ChatRequest {
        if (conversationHistory == null) {
            conversationHistory = List.of();
        }
    }
}
