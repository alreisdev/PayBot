package com.agile.paybot.financial.repository;

import com.agile.paybot.financial.domain.entity.ScheduledPayment;
import com.agile.paybot.shared.enums.ScheduledPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {

    List<ScheduledPayment> findByUserId(String userId);

    List<ScheduledPayment> findByUserIdAndStatus(String userId, ScheduledPaymentStatus status);

    List<ScheduledPayment> findByStatusAndScheduledDateBefore(
            ScheduledPaymentStatus status, LocalDateTime dateTime);

    List<ScheduledPayment> findByUserIdOrderByScheduledDateAsc(String userId);

    boolean existsByBillIdAndStatus(Long billId, ScheduledPaymentStatus status);

    Optional<ScheduledPayment> findByRequestId(String requestId);
}
