package com.agile.paybot.financial.listener;

import com.agile.paybot.financial.config.FinancialQueueConfig;
import com.agile.paybot.financial.domain.entity.ScheduledPayment;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.service.ScheduledPaymentService;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import com.agile.paybot.shared.event.SchedulePaymentCommandEvent;
import com.agile.paybot.shared.event.SchedulePaymentResultEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static com.agile.paybot.shared.constants.PayBotConstants.DEFAULT_USER_ID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulePaymentCommandListener {

    private final ScheduledPaymentService scheduledPaymentService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = FinancialQueueConfig.SCHEDULE_COMMAND_QUEUE)
    public void handleScheduleCommand(SchedulePaymentCommandEvent command, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        String requestId = command.requestId();
        log.info("Received schedule payment command: requestId={}, billId={}, scheduledDate={}",
                requestId, command.billId(), command.scheduledDate());

        try {
            // DB-level idempotency check
            Optional<ScheduledPayment> existing = scheduledPaymentService.findByRequestId(requestId);
            if (existing.isPresent()) {
                ScheduledPayment sp = existing.get();
                log.info("Duplicate schedule command for requestId={}, replaying result", requestId);

                publishResult(new SchedulePaymentResultEvent(
                        requestId,
                        command.sessionId(),
                        true,
                        sp.getId(),
                        String.format("Payment was already scheduled. Scheduled payment ID: %d", sp.getId())
                ));

                channel.basicAck(tag, false);
                return;
            }

            LocalDateTime dateTime = LocalDateTime.parse(command.scheduledDate() + "T00:00:00");

            ScheduledPaymentDTO result = scheduledPaymentService.schedulePayment(
                    DEFAULT_USER_ID, command.billId(), dateTime, requestId);

            publishResult(new SchedulePaymentResultEvent(
                    requestId,
                    command.sessionId(),
                    true,
                    result.id(),
                    String.format("Payment scheduled successfully for %s (%s), amount: $%.2f, " +
                            "will be processed on %s.",
                            result.billerName(), result.billType(),
                            result.amount(), result.scheduledDate().toLocalDate())
            ));

            channel.basicAck(tag, false);
            log.info("Schedule payment command processed: requestId={}, scheduledPaymentId={}",
                    requestId, result.id());

        } catch (BillNotFoundException | IllegalStateException | IllegalArgumentException | DateTimeParseException e) {
            log.warn("Business error scheduling payment: requestId={}, error={}",
                    requestId, e.getMessage());

            publishResult(new SchedulePaymentResultEvent(
                    requestId,
                    command.sessionId(),
                    false,
                    null,
                    e.getMessage()
            ));

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Unexpected error scheduling payment: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            channel.basicNack(tag, false, true);
        }
    }

    private void publishResult(SchedulePaymentResultEvent event) {
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.SCHEDULE_RESULT_KEY,
                event
        );
        log.info("Published schedule payment result: requestId={}, success={}",
                event.requestId(), event.success());
    }
}
