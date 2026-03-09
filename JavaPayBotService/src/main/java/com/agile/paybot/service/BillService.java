package com.agile.paybot.service;

import com.agile.paybot.domain.dto.BillDTO;
import com.agile.paybot.domain.entity.Bill;
import com.agile.paybot.domain.entity.BillStatus;
import com.agile.paybot.exception.BillNotFoundException;
import com.agile.paybot.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillService {

    private final BillRepository billRepository;

    public List<BillDTO> getAllBills(String userId) {
        return billRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillDTO> getUnpaidBills(String userId) {
        return billRepository.findByUserIdAndStatus(userId, BillStatus.PENDING)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillDTO> getBillsByType(String userId, String billType) {
        return billRepository.findByUserIdAndBillTypeContainingIgnoreCase(userId, billType)
                .stream()
                .filter(b -> b.getStatus() == BillStatus.PENDING)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillDTO> getBillsForCurrentMonth(String userId) {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        return billRepository.findBillsDueInPeriod(userId, startOfMonth, endOfMonth)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<BillDTO> getUnpaidBillsForCurrentMonth(String userId) {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        return billRepository.findBillsDueInPeriodWithStatus(
                        userId, startOfMonth, endOfMonth, BillStatus.PENDING)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<BillDTO> getBillById(Long billId) {
        return billRepository.findById(billId).map(this::toDTO);
    }

    @Transactional
    public void markAsPaid(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BillNotFoundException(billId));
        bill.setStatus(BillStatus.PAID);
        billRepository.save(bill);
    }

    private BillDTO toDTO(Bill bill) {
        return new BillDTO(
                bill.getId(),
                bill.getBillerName(),
                bill.getBillType(),
                bill.getAmount(),
                bill.getDueDate(),
                bill.getBillingPeriodStart(),
                bill.getBillingPeriodEnd(),
                bill.getStatus(),
                bill.getAccountNumber()
        );
    }
}
