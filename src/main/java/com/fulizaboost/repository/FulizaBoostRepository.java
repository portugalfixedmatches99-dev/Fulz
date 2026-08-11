package com.fulizaboost.repository;

import com.fulizaboost.entity.FulizaBoost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FulizaBoostRepository extends JpaRepository<FulizaBoost, Long> {

    List<FulizaBoost> findByIdentificationNumber(String identificationNumber);

    FulizaBoost findByExternalReference(String reference);

    // --- NEW: match Daraja callback to the boost record ---
    FulizaBoost findByCheckoutRequestId(String checkoutRequestId);

    List<FulizaBoost> findByPaidTrue();

    List<FulizaBoost> findByPaidTrueAndPaymentDateBetween(LocalDateTime start, LocalDateTime end);

    List<FulizaBoost> findByPaid(Boolean paid);

    List<FulizaBoost> findByPaymentStatus(String paymentStatus);

    @Query("SELECT DISTINCT f.phoneNumber FROM FulizaBoost f WHERE f.phoneNumber IS NOT NULL")
    List<String> findDistinctPhoneNumbers();
}