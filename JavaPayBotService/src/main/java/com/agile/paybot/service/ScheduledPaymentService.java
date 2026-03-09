package com.agile.paybot.service;

import com.agile.paybot.domain.dto.BillDTO;
import com.agile.paybot.domain.dto.PaymentResultDTO;
import com.agile.paybot.domain.dto.ScheduledPaymentDTO;
import com.agile.paybot.domain.entity.Bill;
import com.agile.paybot.domain.entity.BillStatus;
import com.agile.paybot.domain.entity.ScheduledPayment;
import com.agile.paybot.domain.entity.ScheduledPaymentStatus;
import com.agile.paybot.exception.BillNotFoundException;
import com.agile.paybot.repository.BillRepository;
import com.agile.paybot.repository.ScheduledPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentService {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final PaymentService paymentService;

    @Transactional
    public ScheduledPaymentDTO schedulePayment(String userId, Long billId, LocalDateTime scheduledDate) {
        // Validate bill exists
        BillDTO billDTO = billService.getBillById(billId)
                .orElseThrow(() -> new BillNotFoundException(billId));

        // Validate bill is not already paid
        if (billDTO.status() == BillStatus.PAID) {
            throw new IllegalStateException("Bill has already been paid");
        }

        // Validate scheduled date is in the future
        if (scheduledDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled date must be in the future");
        }

        // Check if there's already a pending scheduled payment for this bill
        if (scheduledPaymentRepository.existsByBillIdAndStatus(billId, ScheduledPaymentStatus.PENDING)) {
            throw new IllegalStateException("A payment for this bill is already scheduled");
        }

        // Create scheduled payment
        Bill bill = billRepository.getReferenceById(billId);
        ScheduledPayment scheduledPayment = new ScheduledPayment();
        scheduledPayment.setUserId(userId);
        scheduledPayment.setBill(bill);
        scheduledPayment.setScheduledDate(scheduledDate);
        scheduledPayment.setAmount(billDTO.amount());
        scheduledPayment.setStatus(ScheduledPaymentStatus.PENDING);

        ScheduledPayment saved = scheduledPaymentRepository.save(scheduledPayment);
        log.info("Scheduled payment created: id={}, billId={}, scheduledDate={}",
                saved.getId(), billId, scheduledDate);

        return toDTO(saved, billDTO.billerName(), billDTO.billType());
    }

    @Transactional(readOnly = true)
    public List<ScheduledPaymentDTO> getScheduledPayments(String userId) {
        return scheduledPaymentRepository.findByUserIdOrderByScheduledDateAsc(userId)
                .stream()
                .map(sp -> toDTO(sp, sp.getBill().getBillerName(), sp.getBill().getBillType()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduledPaymentDTO> getScheduledPaymentsByStatus(String userId, ScheduledPaymentStatus status) {
        return scheduledPaymentRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(sp -> toDTO(sp, sp.getBill().getBillerName(), sp.getBill().getBillType()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ScheduledPaymentDTO> getScheduledPaymentById(Long id) {
        return scheduledPaymentRepository.findById(id)
                .map(sp -> toDTO(sp, sp.getBill().getBillerName(), sp.getBill().getBillType()));
    }

    @Transactional
    public void cancelScheduledPayment(Long scheduledPaymentId) {
        ScheduledPayment scheduledPayment = scheduledPaymentRepository.findById(scheduledPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled payment not found: " + scheduledPaymentId));

        if (scheduledPayment.getStatus() != ScheduledPaymentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING scheduled payments can be cancelled. Current status: "
                    + scheduledPayment.getStatus());
        }

        scheduledPayment.setStatus(ScheduledPaymentStatus.CANCELLED);
        scheduledPaymentRepository.save(scheduledPayment);
        log.info("Scheduled payment cancelled: id={}", scheduledPaymentId);
    }

    @Transactional
    public void executePendingScheduledPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledPayment> duePayments = scheduledPaymentRepository
                .findByStatusAndScheduledDateBefore(ScheduledPaymentStatus.PENDING, now);

        log.info("Found {} scheduled payments due for execution", duePayments.size());

        for (ScheduledPayment sp : duePayments) {
            executeScheduledPayment(sp);
        }
    }

    @Transactional
    public void executeScheduledPayment(ScheduledPayment scheduledPayment) {
        try {
            log.info("Executing scheduled payment: id={}, billId={}",
                    scheduledPayment.getId(), scheduledPayment.getBill().getId());

            PaymentResultDTO result = paymentService.processPayment(
                    scheduledPayment.getBill().getId(),
                    scheduledPayment.getAmount());

            scheduledPayment.setStatus(ScheduledPaymentStatus.EXECUTED);
            scheduledPayment.setExecutedAt(LocalDateTime.now());
            scheduledPayment.setConfirmationNumber(result.confirmationNumber());
            scheduledPaymentRepository.save(scheduledPayment);

            log.info("Scheduled payment executed successfully: id={}, confirmation={}",
                    scheduledPayment.getId(), result.confirmationNumber());

        } catch (Exception e) {
            log.error("Failed to execute scheduled payment: id={}, error={}",
                    scheduledPayment.getId(), e.getMessage());

            scheduledPayment.setStatus(ScheduledPaymentStatus.FAILED);
            scheduledPayment.setFailureReason(e.getMessage());
            scheduledPaymentRepository.save(scheduledPayment);
        }
    }

    private ScheduledPaymentDTO toDTO(ScheduledPayment sp, String billerName, String billType) {
        return new ScheduledPaymentDTO(
                sp.getId(),
                sp.getBill().getId(),
                billerName,
                billType,
                sp.getAmount(),
                sp.getScheduledDate(),
                sp.getStatus(),
                sp.getConfirmationNumber(),
                sp.getCreatedAt(),
                sp.getExecutedAt()
        );
    }
}