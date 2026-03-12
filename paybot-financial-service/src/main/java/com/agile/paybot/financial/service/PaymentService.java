package com.agile.paybot.financial.service;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.exception.BillAlreadyPaidException;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.PaymentRepository;
import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.enums.BillStatus;
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
        BillDTO bill = billService.getBillById(billId)
                .orElseThrow(() -> new BillNotFoundException(billId));

        if (bill.status() == BillStatus.PAID) {
            throw new BillAlreadyPaidException(billId);
        }

        String confirmationNumber = generateConfirmationNumber();

        Bill billEntity = billRepository.getReferenceById(billId);
        Payment payment = new Payment();
        payment.setBill(billEntity);
        payment.setAmountPaid(amount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setConfirmationNumber(confirmationNumber);
        payment.setPaymentMethod("SIMULATED");
        payment.setRequestId(requestId);
        paymentRepository.save(payment);

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
