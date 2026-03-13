package com.agile.paybot.client;

import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class FinancialClientFallback implements FinancialClient {

    @Override
    public List<BillDTO> getUnpaidBills(String userId) {
        log.warn("Fallback: Financial service unavailable - getUnpaidBills(userId={})", userId);
        return Collections.emptyList();
    }

    @Override
    public List<BillDTO> getBillsByType(String userId, String billType) {
        log.warn("Fallback: Financial service unavailable - getBillsByType(userId={}, billType={})", userId, billType);
        return Collections.emptyList();
    }

    @Override
    public List<BillDTO> getUnpaidBillsForCurrentMonth(String userId, boolean currentMonth) {
        log.warn("Fallback: Financial service unavailable - getUnpaidBillsForCurrentMonth(userId={})", userId);
        return Collections.emptyList();
    }

    @Override
    public BillDTO getBillById(Long id) {
        log.warn("Fallback: Financial service unavailable - getBillById(id={})", id);
        return null;
    }

    @Override
    public Boolean paymentExistsByRequestId(String requestId) {
        log.warn("Fallback: Financial service unavailable - paymentExistsByRequestId(requestId={})", requestId);
        return false;
    }

    @Override
    public List<ScheduledPaymentDTO> getScheduledPayments(String userId, String status) {
        log.warn("Fallback: Financial service unavailable - getScheduledPayments(userId={}, status={})", userId, status);
        return Collections.emptyList();
    }

    @Override
    public void cancelScheduledPayment(Long id) {
        log.warn("Fallback: Financial service unavailable - cancelScheduledPayment(id={})", id);
        throw new RuntimeException("Financial service is currently unavailable. Please try again later.");
    }
}
