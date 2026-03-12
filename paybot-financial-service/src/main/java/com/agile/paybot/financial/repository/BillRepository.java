package com.agile.paybot.financial.repository;

import com.agile.paybot.financial.domain.entity.Bill;
import com.agile.paybot.shared.enums.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByUserId(String userId);

    List<Bill> findByUserIdAndStatus(String userId, BillStatus status);

    List<Bill> findByUserIdAndBillTypeContainingIgnoreCase(String userId, String billType);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId " +
           "AND b.dueDate BETWEEN :startDate AND :endDate")
    List<Bill> findBillsDueInPeriod(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM Bill b WHERE b.userId = :userId " +
           "AND b.dueDate BETWEEN :startDate AND :endDate " +
           "AND b.status = :status")
    List<Bill> findBillsDueInPeriodWithStatus(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") BillStatus status);
}
