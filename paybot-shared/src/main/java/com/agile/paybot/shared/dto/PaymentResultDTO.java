package com.agile.paybot.shared.dto;

public record PaymentResultDTO(
        boolean success,
        String confirmationNumber,
        String message
) {}
