package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Situation entity - Privacy abstraction layer for donor transparency.
 *
 * This entity provides a way to show donors how their funds are being used
 * WITHOUT exposing any beneficiary personally identifiable information (PII).
 *
 * The description field contains only generic circumstance information like:
 * - "Family displaced due to eviction"
 * - "Emergency relocation due to domestic violence"
 * - "Veteran transitioning from homelessness"
 *
 * The referral_id is for INTERNAL use only and must NEVER be exposed in
 * donor-facing queries or DTOs.
 */
@Entity
@Table(name = "situations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Situation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    // Generic description - NO PII allowed
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private SituationCategory category;

    // Internal link to referral - NEVER expose to donors
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id")
    private Referral referral;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Funding relationships
    @OneToMany(mappedBy = "situation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SituationFunding> fundings = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Situation categories
    public enum SituationCategory {
        EVICTION("Eviction/Displacement"),
        DOMESTIC_VIOLENCE("Domestic Violence"),
        NATURAL_DISASTER("Natural Disaster"),
        MEDICAL_EMERGENCY("Medical Emergency"),
        VETERAN("Veteran Services"),
        FAMILY_CRISIS("Family Crisis"),
        TRANSITION("Transitional Housing"),
        JOB_LOSS("Job Loss"),
        HOUSING_INSTABILITY("Housing Instability"),
        OTHER("Other");

        private final String displayName;

        SituationCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Helper methods
    public int getTotalNightsAllocated() {
        return fundings.stream()
                .mapToInt(f -> f.getNightsAllocated() != null ? f.getNightsAllocated() : 0)
                .sum();
    }

    public int getTotalNightsUsed() {
        return fundings.stream()
                .mapToInt(f -> f.getNightsUsed() != null ? f.getNightsUsed() : 0)
                .sum();
    }

    public int getTotalNightsRemaining() {
        return getTotalNightsAllocated() - getTotalNightsUsed();
    }

    public String getCategoryDisplayName() {
        return category != null ? category.getDisplayName() : "Uncategorized";
    }
}
