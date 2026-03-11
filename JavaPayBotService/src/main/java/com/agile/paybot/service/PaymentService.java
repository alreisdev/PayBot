package com.agile.paybot.service;

import com.agile.paybot.domain.dto.BillDTO;
import com.agile.paybot.domain.dto.PaymentResultDTO;
import com.agile.paybot.domain.entity.Bill;
import com.agile.paybot.domain.entity.BillStatus;
import com.agile.paybot.domain.entity.Payment;
import com.agile.paybot.exception.BillAlreadyPaidException;
import com.agile.paybot.exception.BillNotFoundException;
import com.agile.paybot.repository.BillRepository;
import com.agile.paybot.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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
        // Validate bill exists and is payable
        BillDTO bill = billService.getBillById(billId)
                .orElseThrow(() -> new BillNotFoundException(billId));

        if (bill.status() == BillStatus.PAID) {
            throw new BillAlreadyPaidException(billId);
        }

        // Simulate payment processing (always succeeds for demo)
        String confirmationNumber = generateConfirmationNumber();

        // Create payment record
        Bill billEntity = billRepository.getReferenceById(billId);
        Payment payment = new Payment();
        payment.setBill(billEntity);
        payment.setAmountPaid(amount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setConfirmationNumber(confirmationNumber);
        payment.setPaymentMethod("SIMULATED");
        payment.setRequestId(requestId);
        paymentRepository.save(payment);

        // Mark bill as paid
        billService.markAsPaid(billId);

        return new PaymentResultDTO(
                true,
                confirmationNumber,
                String.format("Payment successful! Your %s bill of $%.2f has been paid.",
                        bill.billerName(), amount)
        );
    }

    public Optional<Payment> findPaymentByRequestId(String requestId) {
        return paymentRepository.findByRequestId(requestId);
    }

    private String generateConfirmationNumber() {
        return "PAY-" + System.currentTimeMillis() + "-" +
                ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
