package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "donations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    // Financial breakdown
    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "facilitator_fee", precision = 10, scale = 2)
    private BigDecimal facilitatorFee;

    @Column(name = "processing_fee", precision = 10, scale = 2)
    private BigDecimal processingFee;

    @Column(name = "net_amount", precision = 10, scale = 2)
    private BigDecimal netAmount;

    // Nights calculation
    @Column(name = "nights_funded")
    private Integer nightsFunded;

    @Column(name = "avg_nightly_rate_at_donation", precision = 10, scale = 2)
    private BigDecimal avgNightlyRateAtDonation;

    // Status tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DonationStatus status = DonationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 50)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    // Audit fields
    @Column(name = "fee_structure_version", length = 20)
    private String feeStructureVersion = "v1.0";

    @Column(name = "donated_at")
    private LocalDateTime donatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "donation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SituationFunding> situationFundings = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (donatedAt == null) {
            donatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum DonationStatus {
        PENDING("Pending"),
        VERIFIED("Verified"),
        ALLOCATED("Allocated"),
        PARTIALLY_USED("Partially Used"),
        FULLY_USED("Fully Used"),
        CANCELLED("Cancelled");

        private final String displayName;

        DonationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum VerificationStatus {
        PENDING("Pending Review"),
        VERIFIED("Verified"),
        REJECTED("Rejected");

        private final String displayName;

        VerificationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Helper methods
    public BigDecimal getTotalFees() {
        BigDecimal total = BigDecimal.ZERO;
        if (platformFee != null) total = total.add(platformFee);
        if (facilitatorFee != null) total = total.add(facilitatorFee);
        if (processingFee != null) total = total.add(processingFee);
        return total;
    }

    public int getNightsUsed() {
        return situationFundings.stream()
                .mapToInt(sf -> sf.getNightsUsed() != null ? sf.getNightsUsed() : 0)
                .sum();
    }

    public int getNightsRemaining() {
        int funded = nightsFunded != null ? nightsFunded : 0;
        return funded - getNightsUsed();
    }

    public boolean isFullyAllocated() {
        return status == DonationStatus.ALLOCATED ||
               status == DonationStatus.PARTIALLY_USED ||
               status == DonationStatus.FULLY_USED;
    }
}
