package com.agile.paybot.financial.listener;

import com.agile.paybot.financial.config.FinancialQueueConfig;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.service.PaymentService;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.event.PaymentCommandEvent;
import com.agile.paybot.shared.event.PaymentResultEvent;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCommandListenerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private PaymentCommandListener listener;

    @Test
    void handlePaymentCommand_newPayment_processesAndPublishes() throws IOException {
        PaymentCommandEvent command = new PaymentCommandEvent(
                "req-1", 1L, new BigDecimal("150.00"), "sess-1");

        when(paymentService.findPaymentByRequestId("req-1")).thenReturn(Optional.empty());
        when(paymentService.processPayment(1L, new BigDecimal("150.00"), "req-1"))
                .thenReturn(new PaymentResultDTO(true, "PAY-123", "Payment successful"));

        listener.handlePaymentCommand(command, channel, 1L);

        verify(channel).basicAck(1L, false);

        ArgumentCaptor<PaymentResultEvent> eventCaptor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FinancialQueueConfig.FINANCIAL_EXCHANGE),
                eq(FinancialQueueConfig.PAYMENT_RESULT_KEY),
                eventCaptor.capture());

        PaymentResultEvent published = eventCaptor.getValue();
        assertThat(published.success()).isTrue();
        assertThat(published.confirmationNumber()).isEqualTo("PAY-123");
        assertThat(published.requestId()).isEqualTo("req-1");
    }

    @Test
    void handlePaymentCommand_duplicate_replaysResult() throws IOException {
        PaymentCommandEvent command = new PaymentCommandEvent(
                "req-dup", 1L, new BigDecimal("150.00"), "sess-1");

        Payment existingPayment = new Payment();
        existingPayment.setConfirmationNumber("PAY-EXISTING");
        when(paymentService.findPaymentByRequestId("req-dup"))
                .thenReturn(Optional.of(existingPayment));

        listener.handlePaymentCommand(command, channel, 1L);

        // Should ACK without processing again
        verify(channel).basicAck(1L, false);
        verify(paymentService, never()).processPayment(anyLong(), any(), anyString());

        // Should still publish the replayed result
        verify(rabbitTemplate).convertAndSend(
                eq(FinancialQueueConfig.FINANCIAL_EXCHANGE),
                eq(FinancialQueueConfig.PAYMENT_RESULT_KEY),
                any(PaymentResultEvent.class));
    }

    @Test
    void handlePaymentCommand_billNotFound_publishesFailure() throws IOException {
        PaymentCommandEvent command = new PaymentCommandEvent(
                "req-nf", 99L, new BigDecimal("100.00"), "sess-1");

        when(paymentService.findPaymentByRequestId("req-nf")).thenReturn(Optional.empty());
        when(paymentService.processPayment(99L, new BigDecimal("100.00"), "req-nf"))
                .thenThrow(new BillNotFoundException(99L));

        listener.handlePaymentCommand(command, channel, 1L);

        verify(channel).basicAck(1L, false);

        ArgumentCaptor<PaymentResultEvent> eventCaptor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue().success()).isFalse();
    }

    @Test
    void handlePaymentCommand_unexpectedError_nacks() throws IOException {
        PaymentCommandEvent command = new PaymentCommandEvent(
                "req-err", 1L, new BigDecimal("150.00"), "sess-1");

        when(paymentService.findPaymentByRequestId("req-err")).thenReturn(Optional.empty());
        when(paymentService.processPayment(1L, new BigDecimal("150.00"), "req-err"))
                .thenThrow(new RuntimeException("DB connection lost"));

        listener.handlePaymentCommand(command, channel, 1L);

        // Should NACK with requeue for unexpected errors
        verify(channel).basicNack(1L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
