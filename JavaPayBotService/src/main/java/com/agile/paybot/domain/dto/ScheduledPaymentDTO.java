package com.agile.paybot.domain.dto;

import com.agile.paybot.domain.entity.ScheduledPaymentStatus;

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