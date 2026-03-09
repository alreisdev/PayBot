package com.agile.paybot.domain.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "Message cannot be empty")
        String message,

        List<MessageDTO> conversationHistory
) {
    public ChatRequest {
        if (conversationHistory == null) {
            conversationHistory = List.of();
        }
    }
}
