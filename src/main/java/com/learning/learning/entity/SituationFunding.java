package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SituationFunding entity - Links donations to situations.
 *
 * This is the bridge table that tracks how much of each donation
 * has been allocated to specific situations, and how many nights
 * have been used.
 *
 * A single donation can fund multiple situations, and a single
 * situation can be funded by multiple donations.
 */
@Entity
@Table(name = "situation_funding")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SituationFunding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", nullable = false)
    private Donation donation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "situation_id", nullable = false)
    private Situation situation;

    @Column(name = "amount_allocated", precision = 10, scale = 2)
    private BigDecimal amountAllocated;

    @Column(name = "nights_allocated")
    private Integer nightsAllocated;

    @Column(name = "nights_used")
    private Integer nightsUsed = 0;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by")
    private User allocatedBy;

    @Column(name = "usage_explanation", columnDefinition = "TEXT")
    private String usageExplanation;

    // Ledger reference for allocation transaction
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_transaction_id")
    private LedgerTransaction ledgerTransaction;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (allocatedAt == null) {
            allocatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public Integer getNightsRemaining() {
        int allocated = nightsAllocated != null ? nightsAllocated : 0;
        int used = nightsUsed != null ? nightsUsed : 0;
        return allocated - used;
    }

    public boolean isFullyUsed() {
        return getNightsRemaining() <= 0;
    }

    public void addNightsUsed(int nights) {
        if (this.nightsUsed == null) {
            this.nightsUsed = 0;
        }
        this.nightsUsed += nights;
    }
}
