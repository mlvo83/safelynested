package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "donor_setup_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DonorSetupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true, length = 20)
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "existing_donor_id")
    private Donor existingDonor;

    // New donor fields
    @Enumerated(EnumType.STRING)
    @Column(name = "donor_type", length = 20)
    private Donor.DonorType donorType;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    // Address
    @Column(name = "street_address", length = 500)
    private String streetAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    // Business fields
    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    // Preferences
    @Enumerated(EnumType.STRING)
    @Column(name = "anonymity_preference", length = 20)
    private AnonymityPreference anonymityPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 20)
    private PreferredContactMethod preferredContactMethod;

    // Notes
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Review tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // Result donor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_donor_id")
    private Donor resultDonor;

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

    // Helper methods
    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }

    public boolean isLinkExisting() {
        return requestType == RequestType.LINK_EXISTING;
    }

    public boolean isCreateNew() {
        return requestType == RequestType.CREATE_NEW;
    }

    public String getDonorDisplayName() {
        if (isLinkExisting() && existingDonor != null) {
            return existingDonor.getDisplayName();
        }
        if (donorType == Donor.DonorType.BUSINESS && companyName != null) {
            return companyName;
        }
        String name = "";
        if (firstName != null) name += firstName;
        if (lastName != null) name += (name.isEmpty() ? "" : " ") + lastName;
        return name.isEmpty() ? "Unknown" : name;
    }

    // Enums
    public enum RequestType {
        LINK_EXISTING("Link Existing Donor"),
        CREATE_NEW("Create New Donor");

        private final String displayName;

        RequestType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RequestStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String displayName;

        RequestStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum AnonymityPreference {
        FULL_NAME("Full Name"),
        FIRST_NAME_ONLY("First Name Only"),
        ANONYMOUS("Anonymous");

        private final String displayName;

        AnonymityPreference(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PreferredContactMethod {
        EMAIL("Email"),
        PHONE("Phone"),
        MAIL("Mail"),
        NO_CONTACT("No Contact");

        private final String displayName;

        PreferredContactMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
