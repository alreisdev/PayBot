package com.agile.paybot.financial.config;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.financial.repository.BillRepository;
import com.agile.paybot.shared.enums.BillStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("!docker")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final BillRepository billRepository;

    @Override
    public void run(String... args) {
        if (billRepository.count() == 0) {
            log.info("Initializing sample bill data...");

            LocalDate today = LocalDate.now();
            String userId = "user-1";

            List<Bill> bills = List.of(
                    createBill(userId, "City Electric Company", "electricity",
                            new BigDecimal("125.50"), today.plusDays(5),
                            today.minusMonths(1), today, "ACC-ELEC-001"),

                    createBill(userId, "Metro Water Services", "water",
                            new BigDecimal("45.00"), today.plusDays(10),
                            today.minusMonths(1), today, "ACC-WATER-002"),

                    createBill(userId, "FastNet Internet", "internet",
                            new BigDecimal("79.99"), today.plusDays(3),
                            today.minusMonths(1), today, "ACC-NET-003"),

                    createBill(userId, "CityGas Inc", "gas",
                            new BigDecimal("67.25"), today.plusDays(15),
                            today.minusMonths(1), today, "ACC-GAS-004"),

                    createBill(userId, "Mobile Plus", "phone",
                            new BigDecimal("55.00"), today.plusDays(7),
                            today.minusMonths(1), today, "ACC-PHONE-005")
            );

            billRepository.saveAll(bills);
            log.info("Created {} sample bills", bills.size());
        } else {
            log.info("Bills already exist, skipping initialization");
        }
    }

    private Bill createBill(String userId, String billerName, String billType,
                            BigDecimal amount, LocalDate dueDate,
                            LocalDate periodStart, LocalDate periodEnd, String accountNumber) {
        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setBillerName(billerName);
        bill.setBillType(billType);
        bill.setAmount(amount);
        bill.setDueDate(dueDate);
        bill.setBillingPeriodStart(periodStart);
        bill.setBillingPeriodEnd(periodEnd);
        bill.setAccountNumber(accountNumber);
        bill.setStatus(BillStatus.PENDING);
        return bill;
    }
}
