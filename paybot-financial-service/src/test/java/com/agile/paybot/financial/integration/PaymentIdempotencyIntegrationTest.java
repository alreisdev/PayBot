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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void duplicateRequestId_replaysResult() throws Exception {
        // Arrange — find a PENDING bill
        Bill bill = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING)
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING bill found in seed data"));

        String requestId = UUID.randomUUID().toString();
        PaymentCommandEvent command = new PaymentCommandEvent(
                requestId, bill.getId(), bill.getAmount(), "test-session-idem-1");

        // Act — send the same command twice
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command);

        // Wait for the first result before sending the duplicate
        PaymentResultEvent firstResult = receivePaymentResult(10_000);
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.success()).isTrue();

        // Send duplicate
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command);

        PaymentResultEvent secondResult = receivePaymentResult(10_000);
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.success()).isTrue();

        // Assert — both results have the same confirmation number
        assertThat(secondResult.confirmationNumber()).isEqualTo(firstResult.confirmationNumber());

        // Assert — only 1 payment record in DB for this requestId
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> requestId.equals(p.getRequestId()))
                .toList();
        assertThat(payments).hasSize(1);
    }

    @Test
    void differentRequestId_processedSeparately() throws Exception {
        // Arrange — find two different PENDING bills
        List<Bill> pendingBills = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING);
        assertThat(pendingBills).hasSizeGreaterThanOrEqualTo(2);

        Bill bill1 = pendingBills.get(0);
        Bill bill2 = pendingBills.get(1);

        String requestId1 = UUID.randomUUID().toString();
        String requestId2 = UUID.randomUUID().toString();

        PaymentCommandEvent command1 = new PaymentCommandEvent(
                requestId1, bill1.getId(), bill1.getAmount(), "test-session-sep-1");
        PaymentCommandEvent command2 = new PaymentCommandEvent(
                requestId2, bill2.getId(), bill2.getAmount(), "test-session-sep-2");

        // Act — send both commands
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command1);
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_COMMAND_KEY,
                command2);

        // Collect both results
        List<PaymentResultEvent> results = new ArrayList<>();
        PaymentResultEvent r1 = receivePaymentResult(10_000);
        assertThat(r1).isNotNull();
        results.add(r1);

        PaymentResultEvent r2 = receivePaymentResult(10_000);
        assertThat(r2).isNotNull();
        results.add(r2);

        // Assert — both succeeded
        assertThat(results).allMatch(PaymentResultEvent::success);

        // Assert — different confirmation numbers
        assertThat(results.get(0).confirmationNumber())
                .isNotEqualTo(results.get(1).confirmationNumber());

        // Assert — 2 distinct payment records
        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> requestId1.equals(p.getRequestId()) || requestId2.equals(p.getRequestId()))
                .toList();
        assertThat(payments).hasSize(2);
    }

    private PaymentResultEvent receivePaymentResult(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object message = rabbitTemplate.receiveAndConvert(
                    FinancialQueueConfig.PAYMENT_RESULT_QUEUE, 1000);
            if (message instanceof PaymentResultEvent event) {
                return event;
            }
            if (message != null) {
                continue;
            }
            Thread.sleep(200);
        }
        return null;
    }
}
