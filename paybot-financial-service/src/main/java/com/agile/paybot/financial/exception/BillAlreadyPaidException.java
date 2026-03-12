package com.agile.paybot.financial.exception;

public class BillAlreadyPaidException extends RuntimeException {

    public BillAlreadyPaidException(Long billId) {
        super("Bill has already been paid. Bill id: " + billId);
    }
}
