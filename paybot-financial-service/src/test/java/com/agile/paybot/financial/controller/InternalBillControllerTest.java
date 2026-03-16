package com.agile.paybot.financial.controller;

import com.agile.paybot.financial.service.BillService;
import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.enums.BillStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalBillController.class)
class InternalBillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillService billService;

    private final BillDTO testBill = new BillDTO(
            1L, "Test Electric", "electricity",
            new BigDecimal("150.00"), LocalDate.of(2026, 3, 25),
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
            BillStatus.PENDING, "ACC-001");

    @Test
    void getBills_defaultUser_returnsUnpaid() throws Exception {
        when(billService.getUnpaidBills("user-1")).thenReturn(List.of(testBill));

        mockMvc.perform(get("/api/internal/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].billerName").value("Test Electric"))
                .andExpect(jsonPath("$[0].amount").value(150.00));
    }

    @Test
    void getBills_byType_filtersCorrectly() throws Exception {
        when(billService.getBillsByType("user-1", "electricity")).thenReturn(List.of(testBill));

        mockMvc.perform(get("/api/internal/bills")
                        .param("billType", "electricity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].billType").value("electricity"));
    }

    @Test
    void getBillById_found() throws Exception {
        when(billService.getBillById(1L)).thenReturn(Optional.of(testBill));

        mockMvc.perform(get("/api/internal/bills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.billerName").value("Test Electric"));
    }

    @Test
    void getBillById_notFound_returns404() throws Exception {
        when(billService.getBillById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/bills/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/internal/bills/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorEnv_isDenied() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isForbidden());
    }
}
