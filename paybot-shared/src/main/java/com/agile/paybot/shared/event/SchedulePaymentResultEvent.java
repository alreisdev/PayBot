package com.agile.paybot.shared.event;

public record SchedulePaymentResultEvent(
        String requestId,
        String sessionId,
        boolean success,
        Long scheduledPaymentId,
        String message
) {}
