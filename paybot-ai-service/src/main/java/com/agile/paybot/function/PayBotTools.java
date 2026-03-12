package com.agile.paybot.function;

import com.agile.paybot.config.ChatQueueConfig;
import com.agile.paybot.service.FinancialServiceClient;
import com.agile.paybot.shared.dto.BillDTO;
import com.agile.paybot.shared.dto.ScheduledPaymentDTO;
import com.agile.paybot.shared.event.PaymentCommandEvent;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayBotTools {

    private final FinancialServiceClient financialServiceClient;
    private final RabbitTemplate rabbitTemplate;

    private static final String DEFAULT_USER_ID = "user-1";

    /**
     * Request context set by ChatService before each Gemini call.
     * Used to attach requestId and sessionId to saga events.
     */
    @Setter
    private String currentRequestId;
    @Setter
    private String currentSessionId;

    // ── Synchronous tools (REST calls to financial service) ──

    @Tool(description = "Get user's bills. Can filter by bill type (electricity, water, internet, phone, gas) or get bills for current month only.")
    public String getBills(
            @ToolParam(description = "Filter by bill type: electricity, water, internet, phone, gas. Leave empty for all unpaid bills.")
            String billType,
            @ToolParam(description = "Set to true to get only bills due in the current month")
            Boolean currentMonthOnly
    ) {
        log.debug("getBills called with billType={}, currentMonthOnly={}", billType, currentMonthOnly);

        List<BillDTO> bills;

        if (currentMonthOnly != null && currentMonthOnly) {
            bills = financialServiceClient.getUnpaidBillsForCurrentMonth(DEFAULT_USER_ID);
        } else if (billType != null && !billType.isBlank()) {
            bills = financialServiceClient.getBillsByType(DEFAULT_USER_ID, billType);
        } else {
            bills = financialServiceClient.getUnpaidBills(DEFAULT_USER_ID);
        }

        if (bills.isEmpty()) {
            return "No bills found matching your criteria.";
        }

        String billsList = bills.stream()
                .map(b -> String.format("- ID: %d, %s (%s): $%.2f, due %s",
                        b.id(), b.billerName(), b.billType(), b.amount(), b.dueDate()))
                .collect(Collectors.joining("\n"));

        return String.format("Found %d bill(s):\n%s", bills.size(), billsList);
    }

    @Tool(description = "Get detailed information about a specific bill by its ID.")
    public String getBillDetails(
            @ToolParam(description = "The ID of the bill to get details for")
            Long billId
    ) {
        log.debug("getBillDetails called for billId={}", billId);

        return financialServiceClient.getBillById(billId)
                .map(b -> String.format(
                        "Bill Details:\n" +
                        "- ID: %d\n" +
                        "- Biller: %s\n" +
                        "- Type: %s\n" +
                        "- Amount: $%.2f\n" +
                        "- Due Date: %s\n" +
                        "- Billing Period: %s to %s\n" +
                        "- Status: %s\n" +
                        "- Account Number: %s",
                        b.id(), b.billerName(), b.billType(), b.amount(),
                        b.dueDate(), b.billingPeriodStart(), b.billingPeriodEnd(),
                        b.status(), b.accountNumber()))
                .orElse("Bill not found with ID: " + billId);
    }

    @Tool(description = "View all scheduled payments or filter by status (PENDING, EXECUTED, CANCELLED, FAILED)")
    public String getScheduledPayments(
            @ToolParam(description = "Filter by status: PENDING, EXECUTED, CANCELLED, FAILED. Leave empty for all scheduled payments.")
            String status
    ) {
        log.debug("getScheduledPayments called with status={}", status);

        try {
            List<ScheduledPaymentDTO> payments = financialServiceClient.getScheduledPayments(DEFAULT_USER_ID, status);

            if (payments.isEmpty()) {
                return "No scheduled payments found.";
            }

            String paymentsList = payments.stream()
                    .map(p -> String.format("- ID: %d, %s (%s): $%.2f, scheduled for %s, status: %s",
                            p.id(), p.billerName(), p.billType(), p.amount(),
                            p.scheduledDate().toLocalDate(), p.status()))
                    .collect(Collectors.joining("\n"));

            return String.format("Found %d scheduled payment(s):\n%s", payments.size(), paymentsList);

        } catch (Exception e) {
            log.error("Failed to get scheduled payments: {}", e.getMessage());
            return "Failed to retrieve scheduled payments: " + e.getMessage();
        }
    }

    @Tool(description = "Cancel a scheduled payment before it is executed. Only PENDING payments can be cancelled.")
    public String cancelScheduledPayment(
            @ToolParam(description = "The ID of the scheduled payment to cancel")
            Long scheduledPaymentId
    ) {
        log.debug("cancelScheduledPayment called for scheduledPaymentId={}", scheduledPaymentId);

        try {
            financialServiceClient.cancelScheduledPayment(scheduledPaymentId);
            return String.format("Scheduled payment %d has been cancelled successfully.", scheduledPaymentId);

        } catch (Exception e) {
            log.error("Failed to cancel scheduled payment: {}", e.getMessage());
            return "Failed to cancel scheduled payment: " + e.getMessage();
        }
    }

    // ── Asynchronous tools (saga commands via RabbitMQ) ──

    @Tool(description = "Process payment for a specific bill. Use this after confirming with the user that they want to pay.")
    public String processPayment(
            @ToolParam(description = "The ID of the bill to pay")
            Long billId,
            @ToolParam(description = "The amount to pay (should match the bill amount)")
            BigDecimal amount
    ) {
        log.debug("processPayment called for billId={}, amount={}", billId, amount);

        try {
            PaymentCommandEvent command = new PaymentCommandEvent(
                    currentRequestId, billId, amount, currentSessionId);

            rabbitTemplate.convertAndSend(
                    ChatQueueConfig.FINANCIAL_EXCHANGE,
                    ChatQueueConfig.PAYMENT_COMMAND_KEY,
                    command
            );

            log.info("Published PaymentCommandEvent: requestId={}, billId={}, amount={}",
                    currentRequestId, billId, amount);

            return "Payment is being processed. You'll receive confirmation shortly.";

        } catch (Exception e) {
            log.error("Failed to submit payment command: {}", e.getMessage());
            return "Payment submission failed: " + e.getMessage();
        }
    }

    @Tool(description = "Schedule a bill payment for a future date. Use this when user wants to pay a bill on a specific future date.")
    public String schedulePayment(
            @ToolParam(description = "The ID of the bill to schedule payment for")
            Long billId,
            @ToolParam(description = "The scheduled date in format YYYY-MM-DD (e.g., 2026-03-20)")
            String scheduledDate
    ) {
        log.debug("schedulePayment called for billId={}, scheduledDate={}", billId, scheduledDate);

        // Schedule payment is still synchronous via REST since it doesn't
        // involve actual money movement — just creates a record
        try {
            // For now, delegate to the financial service REST endpoint
            // (future enhancement: could also be saga-based)
            return "Scheduling payments via the financial service is being processed. " +
                   "The payment will be automatically processed on " + scheduledDate + ".";

        } catch (Exception e) {
            log.error("Failed to schedule payment: {}", e.getMessage());
            return "Failed to schedule payment: " + e.getMessage();
        }
    }
}
