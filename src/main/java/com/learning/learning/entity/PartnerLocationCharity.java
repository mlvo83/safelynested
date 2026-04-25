package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Join entity linking a PartnerLocation to a Charity.
 * Each row grants one charity access to one partner location.
 * Many-to-many: a partner location may be linked to multiple charities,
 * and a charity may have access to multiple partner locations.
 */
@Entity
@Table(name = "partner_location_charities",
        uniqueConstraints = @UniqueConstraint(name = "uq_plc", columnNames = {"partner_location_id", "charity_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerLocationCharity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_location_id", nullable = false)
    private PartnerLocation partnerLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
