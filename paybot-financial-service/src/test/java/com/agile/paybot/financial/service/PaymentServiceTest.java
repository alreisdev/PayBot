package com.agile.paybot.financial.service;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.Payment;
import com.agile.paybot.financial.exception.BillAlreadyPaidException;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.PaymentRepository;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.enums.BillStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillService billService;

    @InjectMocks
    private PaymentService paymentService;

    private Bill testBill;

    @BeforeEach
    void setUp() {
        testBill = new Bill();
        testBill.setId(1L);
        testBill.setUserId("user-1");
        testBill.setBillerName("Test Electric");
        testBill.setBillType("electricity");
        testBill.setAmount(new BigDecimal("150.00"));
        testBill.setDueDate(LocalDate.now().plusDays(7));
        testBill.setBillingPeriodStart(LocalDate.now().minusMonths(1));
        testBill.setBillingPeriodEnd(LocalDate.now());
        testBill.setStatus(BillStatus.PENDING);
    }

    @Test
    void processPayment_success() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResultDTO result = paymentService.processPayment(1L, new BigDecimal("150.00"), "req-1");

        assertThat(result.success()).isTrue();
        assertThat(result.confirmationNumber()).startsWith("PAY-");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getAmountPaid()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(saved.getRequestId()).isEqualTo("req-1");

        assertThat(testBill.getStatus()).isEqualTo(BillStatus.PAID);
    }

    @Test
    void processPayment_billNotFound_throws() {
        when(billRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(99L, new BigDecimal("100.00"), "req-2"))
                .isInstanceOf(BillNotFoundException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_billAlreadyPaid_throws() {
        testBill.setStatus(BillStatus.PAID);
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> paymentService.processPayment(1L, new BigDecimal("150.00"), "req-3"))
                .isInstanceOf(BillAlreadyPaidException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_amountMismatch_throws() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> paymentService.processPayment(1L, new BigDecimal("99.99"), "req-4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match bill amount");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_withoutRequestId_success() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResultDTO result = paymentService.processPayment(1L, new BigDecimal("150.00"));

        assertThat(result.success()).isTrue();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getRequestId()).isNull();
    }

    @Test
    void findPaymentByRequestId_found() {
        Payment payment = new Payment();
        payment.setRequestId("req-existing");
        when(paymentRepository.findByRequestId("req-existing")).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.findPaymentByRequestId("req-existing");

        assertThat(result).isPresent();
    }

    @Test
    void findPaymentByRequestId_notFound() {
        when(paymentRepository.findByRequestId("req-missing")).thenReturn(Optional.empty());

        Optional<Payment> result = paymentService.findPaymentByRequestId("req-missing");

        assertThat(result).isEmpty();
    }

    @Test
    void confirmationNumber_usesSecureRandomRange() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResultDTO result = paymentService.processPayment(1L, new BigDecimal("150.00"), "req-5");

        // Confirmation format: PAY-<timestamp>-<4digit>
        String conf = result.confirmationNumber();
        assertThat(conf).matches("PAY-\\d+-\\d{4}");

        // Extract the random part and verify range [1000, 9999]
        String randomPart = conf.substring(conf.lastIndexOf('-') + 1);
        int randomNum = Integer.parseInt(randomPart);
        assertThat(randomNum).isBetween(1000, 9999);
    }
}
