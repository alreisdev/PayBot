package com.agile.paybot.shared.dto;

import com.agile.paybot.shared.enums.BillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillDTO(
        Long id,
        String billerName,
        String billType,
        BigDecimal amount,
        LocalDate dueDate,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        BillStatus status,
        String accountNumber
) {}
