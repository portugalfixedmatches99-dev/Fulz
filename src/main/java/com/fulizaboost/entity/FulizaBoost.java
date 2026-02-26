package com.fulizaboost.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "fuliza_boost")
public class FulizaBoost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String identificationNumber; // new field for ID
    private Double amount;
    private Double fee;
    // --- Add these fields ---
    private Boolean paid = false;

    private LocalDateTime paymentDate;


    private String externalReference;

    private LocalDateTime createdAt;


    // --- Payment tracking fields ---
    @Column(name = "mpesa_receipt")
    private String mpesaReceipt;

    @Column(name = "payment_status")
    private String paymentStatus; // PENDING, COMPLETED, FAILED, CANCELLED

    // Constructor
    public FulizaBoost() {
        this.createdAt = LocalDateTime.now();
        this.paid = false;
        this.paymentStatus = "PENDING";// auto set creation date
    }


    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdentificationNumber() { return identificationNumber; }
    public void setIdentificationNumber(String identificationNumber) { this.identificationNumber = identificationNumber; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getFee() { return fee; }
    public void setFee(Double fee) { this.fee = fee; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getMpesaReceipt() {
        return mpesaReceipt;
    }

    public void setMpesaReceipt(String mpesaReceipt) {
        this.mpesaReceipt = mpesaReceipt;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}

