package com.agile.paybot.financial.repository;

import com.agile.paybot.financial.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBillId(Long billId);

    Optional<Payment> findByConfirmationNumber(String confirmationNumber);

    Optional<Payment> findByRequestId(String requestId);
}
