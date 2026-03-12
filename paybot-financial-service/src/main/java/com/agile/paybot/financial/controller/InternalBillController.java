package com.agile.paybot.financial.controller;

import com.agile.paybot.financial.service.BillService;
import com.agile.paybot.shared.dto.BillDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/internal/bills")
@RequiredArgsConstructor
@Tag(name = "Bills", description = "Internal bill management API")
public class InternalBillController {

    private final BillService billService;

    @GetMapping
    @Operation(summary = "Get unpaid bills for a user, optionally filtered by type or current month")
    public ResponseEntity<List<BillDTO>> getBills(
            @RequestParam(defaultValue = "user-1") String userId,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false, defaultValue = "false") boolean currentMonth) {

        List<BillDTO> bills;

        if (billType != null && !billType.isBlank()) {
            bills = billService.getBillsByType(userId, billType);
        } else if (currentMonth) {
            bills = billService.getUnpaidBillsForCurrentMonth(userId);
        } else {
            bills = billService.getUnpaidBills(userId);
        }

        return ResponseEntity.ok(bills);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific bill by ID")
    public ResponseEntity<BillDTO> getBillById(@PathVariable Long id) {
        return billService.getBillById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
