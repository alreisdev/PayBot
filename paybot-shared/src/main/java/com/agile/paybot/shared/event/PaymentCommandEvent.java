package com.agile.paybot.shared.event;

import java.math.BigDecimal;

public record PaymentCommandEvent(
        String requestId,
        Long billId,
        BigDecimal amount,
        String sessionId
) {}
