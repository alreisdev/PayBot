package com.agile.paybot.financial.service;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.exception.BillNotFoundException;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.enums.BillStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private BillService billService;

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
        testBill.setAccountNumber("ACC-001");
    }

    @Test
    void getUnpaidBills_returnsPendingOnly() {
        when(billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING))
                .thenReturn(List.of(testBill));

        List<BillDTO> bills = billService.getUnpaidBills("user-1");

        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).billerName()).isEqualTo("Test Electric");
        assertThat(bills.get(0).status()).isEqualTo(BillStatus.PENDING);
    }

    @Test
    void getUnpaidBills_empty() {
        when(billRepository.findByUserIdAndStatus("user-1", BillStatus.PENDING))
                .thenReturn(List.of());

        List<BillDTO> bills = billService.getUnpaidBills("user-1");

        assertThat(bills).isEmpty();
    }

    @Test
    void getBillById_found() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        Optional<BillDTO> result = billService.getBillById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(1L);
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void getBillById_notFound() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<BillDTO> result = billService.getBillById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getBillsByType_filtersPending() {
        Bill paidBill = new Bill();
        paidBill.setId(2L);
        paidBill.setUserId("user-1");
        paidBill.setBillerName("Old Electric");
        paidBill.setBillType("electricity");
        paidBill.setAmount(new BigDecimal("100.00"));
        paidBill.setDueDate(LocalDate.now().minusDays(30));
        paidBill.setBillingPeriodStart(LocalDate.now().minusMonths(2));
        paidBill.setBillingPeriodEnd(LocalDate.now().minusMonths(1));
        paidBill.setStatus(BillStatus.PAID);

        when(billRepository.findByUserIdAndBillTypeContainingIgnoreCase("user-1", "electricity"))
                .thenReturn(List.of(testBill, paidBill));

        List<BillDTO> bills = billService.getBillsByType("user-1", "electricity");

        assertThat(bills).hasSize(1);
        assertThat(bills.get(0).billerName()).isEqualTo("Test Electric");
    }

    @Test
    void markAsPaid_success() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        billService.markAsPaid(1L);

        assertThat(testBill.getStatus()).isEqualTo(BillStatus.PAID);
        verify(billRepository).save(testBill);
    }

    @Test
    void markAsPaid_notFound_throws() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billService.markAsPaid(99L))
                .isInstanceOf(BillNotFoundException.class);
    }

    @Test
    void toDTO_mapsAllFields() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        BillDTO dto = billService.getBillById(1L).orElseThrow();

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.billerName()).isEqualTo("Test Electric");
        assertThat(dto.billType()).isEqualTo("electricity");
        assertThat(dto.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(dto.status()).isEqualTo(BillStatus.PENDING);
        assertThat(dto.accountNumber()).isEqualTo("ACC-001");
    }
}
