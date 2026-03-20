package com.agile.paybot.shared.dto;

public record ChatResponse(
        MessageDTO message,
        ChatMetadata metadata
) {
    public record ChatMetadata(
            String model,
            String sessionId,
            String requestId,
            Long processingTimeMs,
            Boolean paymentTriggered
    ) {}
}
