package com.agile.paybot.domain.dto;

public record ChatResponse(
        MessageDTO message,
        ChatMetadata metadata
) {
    public record ChatMetadata(
            String model,
            Long promptTokens,
            Long completionTokens,
            Long totalTokens
    ) {}
}
