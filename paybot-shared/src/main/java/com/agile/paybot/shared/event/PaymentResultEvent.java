package com.agile.paybot.shared.event;

public record PaymentResultEvent(
        String requestId,
        String sessionId,
        boolean success,
        String confirmationNumber,
        String message
) {}
