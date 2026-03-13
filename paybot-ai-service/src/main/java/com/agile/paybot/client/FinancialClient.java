package com.agile.paybot.client;

import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "financial-service",
        url = "${financial.service.url}",
        fallback = FinancialClientFallback.class
)
public interface FinancialClient {

    // ── Bill endpoints ──

    @GetMapping("/api/internal/bills")
    List<BillDTO> getUnpaidBills(@RequestParam("userId") String userId);

    @GetMapping("/api/internal/bills")
    List<BillDTO> getBillsByType(@RequestParam("userId") String userId,
                                 @RequestParam("billType") String billType);

    @GetMapping("/api/internal/bills")
    List<BillDTO> getUnpaidBillsForCurrentMonth(@RequestParam("userId") String userId,
                                                 @RequestParam("currentMonth") boolean currentMonth);

    @GetMapping("/api/internal/bills/{id}")
    BillDTO getBillById(@PathVariable("id") Long id);

    // ── Payment endpoints ──

    @GetMapping("/api/internal/payments/by-request-id")
    Boolean paymentExistsByRequestId(@RequestParam("requestId") String requestId);

    // ── Scheduled payment endpoints ──

    @GetMapping("/api/internal/payments/scheduled")
    List<ScheduledPaymentDTO> getScheduledPayments(@RequestParam("userId") String userId,
                                                    @RequestParam(value = "status", required = false) String status);

    @PostMapping("/api/internal/payments/scheduled/{id}/cancel")
    void cancelScheduledPayment(@PathVariable("id") Long id);
}
