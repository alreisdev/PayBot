package com.agile.paybot.financial.integration;

import com.agile.paybot.financial.IntegrationTestBase;
import com.agile.paybot.financial.config.FinancialQueueConfig;
import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.PaymentRepository;
import com.agile.paybot.shared.enums.BillStatus;
import com.agile.paybot.shared.event.PaymentCommandEvent;
import com.agile.paybot.shared.event.PaymentResultEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSagaIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void processPayment_happyPath() throws Exception {
        // Arrange — find an existing PENDING bill seeded by Flyway
        Bill bill = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING)
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING bill found in seed data"));

        String requestId = UUID.randomUUID().toString();
        PaymentCommandEvent command = new PaymentCommandEvent(
                requestId, bill.getId(), bill.getAmount(), "test-session-1");

        // Act — publish command to the payment command queue
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command);

        // Assert — receive result from the payment result queue (with timeout)
        PaymentResultEvent result = receivePaymentResult(10_000);
        assertThat(result).isNotNull();
        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.success()).isTrue();
        assertThat(result.confirmationNumber()).startsWith("PAY-");

        // Verify bill is now PAID in DB
        Bill updatedBill = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.PAID);

        // Verify payment record was created
        Optional<Payment> payment = paymentRepository.findByRequestId(requestId);
        assertThat(payment).isPresent();
        assertThat(payment.get().getConfirmationNumber()).isEqualTo(result.confirmationNumber());
        assertThat(payment.get().getAmountPaid()).isEqualByComparingTo(bill.getAmount());
    }

    @Test
    void processPayment_billNotFound() throws Exception {
        // Arrange — use a non-existent bill ID
        String requestId = UUID.randomUUID().toString();
        PaymentCommandEvent command = new PaymentCommandEvent(
                requestId, 999999L, new BigDecimal("100.00"), "test-session-2");

        // Act
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command);

        // Assert
        PaymentResultEvent result = receivePaymentResult(10_000);
        assertThat(result).isNotNull();
        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.success()).isFalse();
        assertThat(result.confirmationNumber()).isNull();
        assertThat(result.message()).containsIgnoringCase("not found");
    }

    @Test
    void processPayment_billAlreadyPaid() throws Exception {
        // Arrange — pay a bill first, then try to pay it again
        Bill bill = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING)
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING bill found in seed data"));

        String firstRequestId = UUID.randomUUID().toString();
        PaymentCommandEvent firstCommand = new PaymentCommandEvent(
                firstRequestId, bill.getId(), bill.getAmount(), "test-session-3a");

        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                firstCommand);

        PaymentResultEvent firstResult = receivePaymentResult(10_000);
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.success()).isTrue();

        // Act — send a second payment for the same (now PAID) bill
        String secondRequestId = UUID.randomUUID().toString();
        PaymentCommandEvent secondCommand = new PaymentCommandEvent(
                secondRequestId, bill.getId(), bill.getAmount(), "test-session-3b");

        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                secondCommand);

        // Assert
        PaymentResultEvent secondResult = receivePaymentResult(10_000);
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.requestId()).isEqualTo(secondRequestId);
        assertThat(secondResult.success()).isFalse();
        assertThat(secondResult.message()).containsIgnoringCase("already been paid");
    }

    /**
     * Polls the payment result queue until a message arrives or the timeout expires.
     */
    private PaymentResultEvent receivePaymentResult(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object message = rabbitTemplate.receiveAndConvert(
                    FinancialQueueConfig.PAYMENT_RESULT_QUEUE, 1000);
            if (message instanceof PaymentResultEvent event) {
                return event;
            }
            if (message != null) {
                // Unexpected message type — keep polling
                continue;
            }
            Thread.sleep(200);
        }
        return null;
    }
}
