package com.agile.paybot.service;

import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class FinancialServiceClient {

    private final RestClient restClient;

    public FinancialServiceClient(@Value("${financial.service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public List<BillDTO> getUnpaidBills(String userId) {
        try {
            return restClient.get()
                    .uri("/api/internal/bills?userId={userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch unpaid bills for userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<BillDTO> getBillsByType(String userId, String billType) {
        try {
            return restClient.get()
                    .uri("/api/internal/bills?userId={userId}&billType={billType}", userId, billType)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch bills by type for userId={}, type={}: {}", userId, billType, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<BillDTO> getUnpaidBillsForCurrentMonth(String userId) {
        try {
            return restClient.get()
                    .uri("/api/internal/bills?userId={userId}&currentMonth=true", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch current month bills for userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<BillDTO> getBillById(Long billId) {
        try {
            BillDTO bill = restClient.get()
                    .uri("/api/internal/bills/{id}", billId)
                    .retrieve()
                    .body(BillDTO.class);
            return Optional.ofNullable(bill);
        } catch (Exception e) {
            log.error("Failed to fetch bill id={}: {}", billId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<ScheduledPaymentDTO> getScheduledPayments(String userId, String status) {
        try {
            String uri = status != null && !status.isBlank()
                    ? "/api/internal/payments/scheduled?userId={userId}&status={status}"
                    : "/api/internal/payments/scheduled?userId={userId}";
            return restClient.get()
                    .uri(uri, userId, status)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch scheduled payments for userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void cancelScheduledPayment(Long scheduledPaymentId) {
        restClient.post()
                .uri("/api/internal/payments/scheduled/{id}/cancel", scheduledPaymentId)
                .retrieve()
                .toBodilessEntity();
    }

}
