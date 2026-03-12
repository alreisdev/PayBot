package com.agile.paybot.shared.dto;

import com.agile.paybot.shared.enums.ScheduledPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScheduledPaymentDTO(
        Long id,
        Long billId,
        String billerName,
        String billType,
        BigDecimal amount,
        LocalDateTime scheduledDate,
        ScheduledPaymentStatus status,
        String confirmationNumber,
        LocalDateTime createdAt,
        LocalDateTime executedAt
) {}
