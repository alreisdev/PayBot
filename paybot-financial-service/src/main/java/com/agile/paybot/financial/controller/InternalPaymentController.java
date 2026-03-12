package com.agile.paybot.financial.controller;

import com.agile.paybot.financial.service.PaymentService;
import com.agile.paybot.financial.service.ScheduledPaymentService;
import com.agile.paybot.shared.dto.PaymentResultDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import com.agile.paybot.shared.enums.ScheduledPaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/internal/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Internal payment management API")
public class InternalPaymentController {

    private final PaymentService paymentService;
    private final ScheduledPaymentService scheduledPaymentService;

    @PostMapping
    @Operation(summary = "Process a payment synchronously")
    public ResponseEntity<PaymentResultDTO> processPayment(
            @RequestParam Long billId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String requestId) {
        PaymentResultDTO result = paymentService.processPayment(billId, amount, requestId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-request-id")
    @Operation(summary = "Lookup payment by request ID for idempotency")
    public ResponseEntity<Boolean> paymentExistsByRequestId(@RequestParam String requestId) {
        return ResponseEntity.ok(paymentService.findPaymentByRequestId(requestId).isPresent());
    }

    @GetMapping("/scheduled")
    @Operation(summary = "Get scheduled payments for a user")
    public ResponseEntity<List<ScheduledPaymentDTO>> getScheduledPayments(
            @RequestParam(defaultValue = "user-1") String userId,
            @RequestParam(required = false) String status) {

        List<ScheduledPaymentDTO> payments;
        if (status != null && !status.isBlank()) {
            payments = scheduledPaymentService.getScheduledPaymentsByStatus(
                    userId, ScheduledPaymentStatus.valueOf(status.toUpperCase()));
        } else {
            payments = scheduledPaymentService.getScheduledPayments(userId);
        }
        return ResponseEntity.ok(payments);
    }

    @PostMapping("/scheduled/{id}/cancel")
    @Operation(summary = "Cancel a scheduled payment")
    public ResponseEntity<Void> cancelScheduledPayment(@PathVariable Long id) {
        scheduledPaymentService.cancelScheduledPayment(id);
        return ResponseEntity.ok().build();
    }
}
