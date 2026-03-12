package com.agile.paybot.financial.exception;

public class BillNotFoundException extends RuntimeException {

    public BillNotFoundException(Long billId) {
        super("Bill not found with id: " + billId);
    }
}
