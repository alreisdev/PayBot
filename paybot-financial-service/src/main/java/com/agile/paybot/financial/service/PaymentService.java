package com.agile.paybot.financial.service;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.exception.BillAlreadyPaidException;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.PaymentRepository;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.enums.BillStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillService billService;

    public PaymentResultDTO processPayment(Long billId, BigDecimal amount) {
        return processPayment(billId, amount, null);
    }

    public PaymentResultDTO processPayment(Long billId, BigDecimal amount, String requestId) {
        // Acquire pessimistic write lock on the bill row to prevent TOCTOU race
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new BillNotFoundException(billId));

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BillAlreadyPaidException(billId);
        }

        // Validate payment amount matches bill amount
        if (amount.compareTo(bill.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    String.format("Payment amount $%.2f does not match bill amount $%.2f",
                            amount, bill.getAmount()));
        }

        String confirmationNumber = generateConfirmationNumber();

        Payment payment = new Payment();
        payment.setBill(bill);
        payment.setAmountPaid(amount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setConfirmationNumber(confirmationNumber);
        payment.setPaymentMethod("SIMULATED");
        payment.setRequestId(requestId);
        paymentRepository.save(payment);

        // Mark bill as paid on the locked entity directly
        bill.setStatus(BillStatus.PAID);
        billRepository.save(bill);

        return new PaymentResultDTO(
                true,
                confirmationNumber,
                String.format("Payment successful! Your %s bill of $%.2f has been paid.",
                        bill.getBillerName(), amount)
        );
    }

    public Optional<Payment> findPaymentByRequestId(String requestId) {
        return paymentRepository.findByRequestId(requestId);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateConfirmationNumber() {
        return "PAY-" + System.currentTimeMillis() + "-" +
                (1000 + SECURE_RANDOM.nextInt(9000));
    }
}
