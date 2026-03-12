package com.agile.paybot.financial.listener;

import com.agile.paybot.financial.config.FinancialQueueConfig;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.exception.BillAlreadyPaidException;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.service.PaymentService;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.event.PaymentCommandEvent;
import com.agile.paybot.shared.event.PaymentResultEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandListener {

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = FinancialQueueConfig.PAYMENT_COMMAND_QUEUE)
    public void handlePaymentCommand(PaymentCommandEvent command, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        String requestId = command.requestId();
        log.info("Received payment command: requestId={}, billId={}, amount={}",
                requestId, command.billId(), command.amount());

        try {
            // Step 1: DB-level idempotency check
            Optional<Payment> existingPayment = paymentService.findPaymentByRequestId(requestId);

            if (existingPayment.isPresent()) {
                // Already processed — replay the result
                Payment payment = existingPayment.get();
                log.info("Duplicate payment command for requestId={}, replaying confirmation={}",
                        requestId, payment.getConfirmationNumber());

                publishResult(new PaymentResultEvent(
                        requestId,
                        command.sessionId(),
                        true,
                        payment.getConfirmationNumber(),
                        "Payment was already processed successfully. Confirmation Number: "
                                + payment.getConfirmationNumber()
                ));

                channel.basicAck(tag, false);
                return;
            }

            // Step 2: Process the payment
            PaymentResultDTO result = paymentService.processPayment(
                    command.billId(), command.amount(), requestId);

            // Step 3: Publish success result
            publishResult(new PaymentResultEvent(
                    requestId,
                    command.sessionId(),
                    result.success(),
                    result.confirmationNumber(),
                    result.message()
            ));

            channel.basicAck(tag, false);
            log.info("Payment command processed successfully: requestId={}, confirmation={}",
                    requestId, result.confirmationNumber());

        } catch (BillNotFoundException | BillAlreadyPaidException e) {
            // Business errors — publish failure result, don't retry
            log.warn("Business error processing payment command: requestId={}, error={}",
                    requestId, e.getMessage());

            publishResult(new PaymentResultEvent(
                    requestId,
                    command.sessionId(),
                    false,
                    null,
                    e.getMessage()
            ));

            channel.basicAck(tag, false);

        } catch (Exception e) {
            // Unexpected error — NACK with requeue for retry
            log.error("Unexpected error processing payment command: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            channel.basicNack(tag, false, true);
        }
    }

    private void publishResult(PaymentResultEvent event) {
        rabbitTemplate.convertAndSend(
                FinancialQueueConfig.FINANCIAL_EXCHANGE,
                FinancialQueueConfig.PAYMENT_RESULT_KEY,
                event
        );
        log.info("Published payment result: requestId={}, success={}",
                event.requestId(), event.success());
    }
}
