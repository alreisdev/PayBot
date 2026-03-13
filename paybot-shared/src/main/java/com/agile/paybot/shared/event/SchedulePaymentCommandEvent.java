package com.agile.paybot.shared.event;

public record SchedulePaymentCommandEvent(
        String requestId,
        Long billId,
        String scheduledDate,
        String sessionId
) {}
