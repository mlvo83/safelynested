package com.learning.learning.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "referrals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referral_number", unique = true, length = 50)
    private String referralNumber;

    // Multi-tenant support - Link to charity
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charity_id")
    private Charity charity;

    // Participant Information
    @Column(name = "participant_name", nullable = false, length = 200)
    private String participantName;

    @Column(name = "participant_age")
    private Integer participantAge;

    @Column(name = "participant_email", length = 100)
    private String participantEmail;

    @Column(name = "participant_phone", length = 20)
    private String participantPhone;

    @Column(name = "needs_description", columnDefinition = "TEXT")
    private String needsDescription;

    // Referral Details
    @Column(name = "referred_by_charity", length = 200)
    private String referredByCharity; // Deprecated - use charity relationship

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "referred_by_user_id", nullable = false)
    private User referredByUser;

    @Column(name = "urgency_level", length = 20)
    @Enumerated(EnumType.STRING)
    private UrgencyLevel urgencyLevel = UrgencyLevel.MEDIUM;

    // Status
    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private ReferralStatus status = ReferralStatus.PENDING;

    // Document tracking
    @Column(name = "documents_required")
    private Boolean documentsRequired = false;

    @Column(name = "documents_uploaded")
    private Boolean documentsUploaded = false;

    // Zip codes allowed for this referral
    @Column(name = "allowed_zip_codes", columnDefinition = "TEXT")
    private String allowedZipCodes; // Comma-separated list

    // Selected location by participant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_location_id")
    private CharityLocation selectedLocation;

    @Column(name = "location_selected_at")
    private LocalDateTime locationSelectedAt;

    // Approval tracking
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user")
    private User approvedByUser;

    @Column(name = "facilitator_notes", columnDefinition = "TEXT")
    private String facilitatorNotes;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum ReferralStatus {
        PENDING,
        APPROVED,
        REJECTED,
        COMPLETED,
        CANCELLED
    }

    public enum UrgencyLevel {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    // Helper methods
    public boolean isEditable() {
        return status == ReferralStatus.PENDING;
    }

    public boolean canBeApproved() {
        return status == ReferralStatus.PENDING &&
                (!documentsRequired || documentsUploaded);
    }
}
