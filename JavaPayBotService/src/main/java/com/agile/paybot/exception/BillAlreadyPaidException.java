package com.agile.paybot.exception;

public class BillAlreadyPaidException extends RuntimeException {

    public BillAlreadyPaidException(Long billId) {
        super("Bill has already been paid. Bill id: " + billId);
    }
}
