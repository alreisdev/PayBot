package com.agile.paybot.controller;

import com.agile.paybot.domain.dto.BillDTO;
import com.agile.paybot.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for direct bill operations (for testing/debugging).
 * The main interaction should be through the chat endpoint.
 */
@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    private static final String DEFAULT_USER_ID = "user-1";

    @GetMapping
    public ResponseEntity<List<BillDTO>> getAllBills() {
        return ResponseEntity.ok(billService.getUnpaidBills(DEFAULT_USER_ID));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillDTO> getBillById(@PathVariable Long id) {
        return billService.getBillById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/current-month")
    public ResponseEntity<List<BillDTO>> getBillsForCurrentMonth() {
        return ResponseEntity.ok(billService.getBillsForCurrentMonth(DEFAULT_USER_ID));
    }
}
