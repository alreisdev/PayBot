package com.agile.paybot.financial.service;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.domain.entity.ScheduledPayment;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.financial.repository.ScheduledPaymentRepository;
import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import com.agile.paybot.shared.enums.BillStatus;
import com.agile.paybot.shared.enums.ScheduledPaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledPaymentServiceTest {

    @Mock
    private ScheduledPaymentRepository scheduledPaymentRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillService billService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private ScheduledPaymentService scheduledPaymentService;

    private BillDTO testBillDTO;
    private Bill testBill;

    @BeforeEach
    void setUp() {
        testBillDTO = new BillDTO(1L, "Test Electric", "electricity",
                new BigDecimal("150.00"), LocalDate.now().plusDays(7),
                LocalDate.now().minusMonths(1), LocalDate.now(),
                BillStatus.PENDING, "ACC-001");

        testBill = new Bill();
        testBill.setId(1L);
        testBill.setBillerName("Test Electric");
        testBill.setBillType("electricity");
        testBill.setAmount(new BigDecimal("150.00"));
    }

    @Test
    void schedulePayment_success() {
        LocalDateTime futureDate = LocalDateTime.now().plusDays(5);
        when(billService.getBillById(1L)).thenReturn(Optional.of(testBillDTO));
        when(billRepository.getReferenceById(1L)).thenReturn(testBill);
        when(scheduledPaymentRepository.existsByBillIdAndStatus(1L, ScheduledPaymentStatus.PENDING))
                .thenReturn(false);
        when(scheduledPaymentRepository.save(any(ScheduledPayment.class))).thenAnswer(inv -> {
            ScheduledPayment sp = inv.getArgument(0);
            sp.setId(10L);
            return sp;
        });

        ScheduledPaymentDTO result = scheduledPaymentService.schedulePayment(
                "user-1", 1L, futureDate, "req-1");

        assertThat(result).isNotNull();
        assertThat(result.billId()).isEqualTo(1L);

        ArgumentCaptor<ScheduledPayment> captor = ArgumentCaptor.forClass(ScheduledPayment.class);
        verify(scheduledPaymentRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestId()).isEqualTo("req-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(ScheduledPaymentStatus.PENDING);
    }

    @Test
    void schedulePayment_billNotFound_throws() {
        when(billService.getBillById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledPaymentService.schedulePayment(
                "user-1", 99L, LocalDateTime.now().plusDays(5), "req-2"))
                .isInstanceOf(BillNotFoundException.class);

        verify(scheduledPaymentRepository, never()).save(any());
    }

    @Test
    void schedulePayment_billAlreadyPaid_throws() {
        BillDTO paidBill = new BillDTO(1L, "Test Electric", "electricity",
                new BigDecimal("150.00"), LocalDate.now().plusDays(7),
                LocalDate.now().minusMonths(1), LocalDate.now(),
                BillStatus.PAID, "ACC-001");
        when(billService.getBillById(1L)).thenReturn(Optional.of(paidBill));

        assertThatThrownBy(() -> scheduledPaymentService.schedulePayment(
                "user-1", 1L, LocalDateTime.now().plusDays(5), "req-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been paid");
    }

    @Test
    void schedulePayment_pastDate_throws() {
        when(billService.getBillById(1L)).thenReturn(Optional.of(testBillDTO));

        assertThatThrownBy(() -> scheduledPaymentService.schedulePayment(
                "user-1", 1L, LocalDateTime.now().minusDays(1), "req-4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void schedulePayment_alreadyScheduled_throws() {
        when(billService.getBillById(1L)).thenReturn(Optional.of(testBillDTO));
        when(scheduledPaymentRepository.existsByBillIdAndStatus(1L, ScheduledPaymentStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> scheduledPaymentService.schedulePayment(
                "user-1", 1L, LocalDateTime.now().plusDays(5), "req-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    void cancelScheduledPayment_success() {
        ScheduledPayment sp = new ScheduledPayment();
        sp.setId(10L);
        sp.setStatus(ScheduledPaymentStatus.PENDING);
        when(scheduledPaymentRepository.findById(10L)).thenReturn(Optional.of(sp));

        scheduledPaymentService.cancelScheduledPayment(10L);

        assertThat(sp.getStatus()).isEqualTo(ScheduledPaymentStatus.CANCELLED);
        verify(scheduledPaymentRepository).save(sp);
    }

    @Test
    void cancelScheduledPayment_notPending_throws() {
        ScheduledPayment sp = new ScheduledPayment();
        sp.setId(10L);
        sp.setStatus(ScheduledPaymentStatus.EXECUTED);
        when(scheduledPaymentRepository.findById(10L)).thenReturn(Optional.of(sp));

        assertThatThrownBy(() -> scheduledPaymentService.cancelScheduledPayment(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    void cancelScheduledPayment_notFound_throws() {
        when(scheduledPaymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledPaymentService.cancelScheduledPayment(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeScheduledPayment_success() {
        ScheduledPayment sp = new ScheduledPayment();
        sp.setId(10L);
        sp.setBill(testBill);
        sp.setAmount(new BigDecimal("150.00"));
        sp.setStatus(ScheduledPaymentStatus.PENDING);

        when(paymentService.processPayment(1L, new BigDecimal("150.00")))
                .thenReturn(new PaymentResultDTO(true, "PAY-123-4567", "Payment successful"));

        scheduledPaymentService.executeScheduledPayment(sp);

        assertThat(sp.getStatus()).isEqualTo(ScheduledPaymentStatus.EXECUTED);
        assertThat(sp.getConfirmationNumber()).isEqualTo("PAY-123-4567");
        assertThat(sp.getExecutedAt()).isNotNull();
    }

    @Test
    void executeScheduledPayment_failure_setsFailedStatus() {
        ScheduledPayment sp = new ScheduledPayment();
        sp.setId(10L);
        sp.setBill(testBill);
        sp.setAmount(new BigDecimal("150.00"));
        sp.setStatus(ScheduledPaymentStatus.PENDING);

        when(paymentService.processPayment(1L, new BigDecimal("150.00")))
                .thenThrow(new RuntimeException("Payment gateway down"));

        scheduledPaymentService.executeScheduledPayment(sp);

        assertThat(sp.getStatus()).isEqualTo(ScheduledPaymentStatus.FAILED);
        assertThat(sp.getFailureReason()).isEqualTo("Payment gateway down");
    }

    @Test
    void executePendingScheduledPayments_usesLockingQuery() {
        when(scheduledPaymentRepository.findDueForExecutionWithLock(
                eq(ScheduledPaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduledPaymentService.executePendingScheduledPayments();

        verify(scheduledPaymentRepository).findDueForExecutionWithLock(
                eq(ScheduledPaymentStatus.PENDING), any(LocalDateTime.class));
    }
}
