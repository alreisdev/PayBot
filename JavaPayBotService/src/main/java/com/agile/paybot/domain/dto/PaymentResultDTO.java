package com.agile.paybot.domain.dto;

public record PaymentResultDTO(
        boolean success,
        String confirmationNumber,
        String message
) {}
