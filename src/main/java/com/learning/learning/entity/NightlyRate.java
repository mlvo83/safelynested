package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NightlyRate entity - Tracks negotiated nightly rates per location over time.
 *
 * This allows the system to calculate how many nights a donation can fund
 * based on actual negotiated rates with partner properties. Rates can change
 * over time, so we track effective dates.
 *
 * When calculating nights funded for a donation, the system uses the
 * average of active rates for the charity's locations at the time of donation.
 */
@Entity
@Table(name = "nightly_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NightlyRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private CharityLocation location;

    @Column(name = "rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Helper methods
    public boolean isActiveOn(LocalDate date) {
        if (date.isBefore(effectiveDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    public boolean isCurrentlyActive() {
        return isActiveOn(LocalDate.now());
    }
}
