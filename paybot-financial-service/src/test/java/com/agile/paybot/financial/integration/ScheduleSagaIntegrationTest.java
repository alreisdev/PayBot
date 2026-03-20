package com.agile.paybot.financial.integration;

import com.agile.paybot.financial.IntegrationTestBase;
import com.agile.paybot.financial.config.FinancialQueueConfig;
import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.ScheduledPayment;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.ScheduledPaymentRepository;
import com.agile.paybot.shared.enums.BillStatus;
import com.agile.paybot.shared.enums.ScheduledPaymentStatus;
import com.agile.paybot.shared.event.SchedulePaymentCommandEvent;
import com.agile.paybot.shared.event.SchedulePaymentResultEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleSagaIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private ScheduledPaymentRepository scheduledPaymentRepository;

    @Test
    void schedulePayment_happyPath() throws Exception {
        // Arrange — find a PENDING bill
        Bill bill = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING)
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING bill found in seed data"));

        String requestId = UUID.randomUUID().toString();
        String futureDate = LocalDate.now().plusDays(30).toString(); // yyyy-MM-dd format

        SchedulePaymentCommandEvent command = new SchedulePaymentCommandEvent(
                requestId, bill.getId(), futureDate, "test-session-sched-1");

        // Act
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.SCHEDULE_COMMAND_KEY,
                command);

        // Assert — receive result
        SchedulePaymentResultEvent result = receiveScheduleResult(10_000);
        assertThat(result).isNotNull();
        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.success()).isTrue();
        assertThat(result.scheduledPaymentId()).isNotNull();

        // Verify DB record
        ScheduledPayment scheduled = scheduledPaymentRepository.findById(result.scheduledPaymentId())
                .orElseThrow(() -> new AssertionError("Scheduled payment not found in DB"));
        assertThat(scheduled.getStatus()).isEqualTo(ScheduledPaymentStatus.PENDING);
        assertThat(scheduled.getBill().getId()).isEqualTo(bill.getId());
        assertThat(scheduled.getAmount()).isEqualByComparingTo(bill.getAmount());
        assertThat(scheduled.getRequestId()).isEqualTo(requestId);
    }

    @Test
    void schedulePayment_duplicateRequestId() throws Exception {
        // Arrange — find a PENDING bill that has no existing scheduled payment
        List<Bill> pendingBills = billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING);
        Bill bill = pendingBills.stream()
                .filter(b -> !scheduledPaymentRepository.existsByBillIdAndStatus(
                        b.getId(), ScheduledPaymentStatus.PENDING))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING bill without scheduled payment found"));

        String requestId = UUID.randomUUID().toString();
        String futureDate = LocalDate.now().plusDays(60).toString();

        SchedulePaymentCommandEvent command = new SchedulePaymentCommandEvent(
                requestId, bill.getId(), futureDate, "test-session-sched-dup");

        // Act — send first command
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.SCHEDULE_COMMAND_KEY,
                command);

        SchedulePaymentResultEvent firstResult = receiveScheduleResult(10_000);
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.success()).isTrue();

        // Send duplicate command with same requestId
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.SCHEDULE_COMMAND_KEY,
                command);

        SchedulePaymentResultEvent secondResult = receiveScheduleResult(10_000);
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.success()).isTrue();

        // Assert — same scheduled payment ID returned
        assertThat(secondResult.scheduledPaymentId()).isEqualTo(firstResult.scheduledPaymentId());

        // Assert — only 1 scheduled payment record for this requestId
        List<ScheduledPayment> records = scheduledPaymentRepository.findAll().stream()
                .filter(sp -> requestId.equals(sp.getRequestId()))
                .toList();
        assertThat(records).hasSize(1);
    }

    private SchedulePaymentResultEvent receiveScheduleResult(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Object message = rabbitTemplate.receiveAndConvert(
                    FinancialQueueConfig.SCHEDULE_RESULT_QUEUE, 1000);
            if (message instanceof SchedulePaymentResultEvent event) {
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
