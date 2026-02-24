package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stay_partner_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StayPartnerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_number", nullable = false, unique = true, length = 20)
    private String applicationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_type", nullable = false, length = 20)
    private ApplicantType applicantType;

    // Individual fields
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    // Business fields
    @Column(name = "business_name", length = 200)
    private String businessName;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "business_email", length = 100)
    private String businessEmail;

    @Column(name = "business_phone", length = 20)
    private String businessPhone;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    // Property fields
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    @Column(name = "property_name", nullable = false, length = 200)
    private String propertyName;

    @Column(name = "number_of_bedrooms")
    private Integer numberOfBedrooms;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Address
    @Column(name = "street_address", nullable = false, length = 500)
    private String streetAddress;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 50)
    private String state;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country = "USA";

    // Preferences
    @Column(name = "nightly_rate", precision = 10, scale = 2)
    private BigDecimal nightlyRate;

    @Column(name = "amenities", columnDefinition = "TEXT")
    private String amenities;

    @Column(name = "availability_notes", columnDefinition = "TEXT")
    private String availabilityNotes;

    @Column(name = "pets_allowed")
    private Boolean petsAllowed = false;

    @Column(name = "accessibility_features", columnDefinition = "TEXT")
    private String accessibilityFeatures;

    // Communication
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 20)
    private PreferredContactMethod preferredContactMethod = PreferredContactMethod.EMAIL;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    // Admin review
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Result
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_location_id")
    private CharityLocation resultLocation;

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
        return status == ApplicationStatus.PENDING;
    }

    public boolean isApproved() {
        return status == ApplicationStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ApplicationStatus.REJECTED;
    }

    public boolean isIndividual() {
        return applicantType == ApplicantType.INDIVIDUAL;
    }

    public boolean isBusiness() {
        return applicantType == ApplicantType.BUSINESS;
    }

    public String getApplicantDisplayName() {
        if (isBusiness() && businessName != null && !businessName.isEmpty()) {
            return businessName;
        }
        String name = "";
        if (firstName != null) name += firstName;
        if (lastName != null) name += (name.isEmpty() ? "" : " ") + lastName;
        return name.isEmpty() ? "Unknown" : name;
    }

    public String getPrimaryEmail() {
        if (isBusiness() && businessEmail != null && !businessEmail.isEmpty()) {
            return businessEmail;
        }
        return email;
    }

    public String getPrimaryPhone() {
        if (isBusiness() && businessPhone != null && !businessPhone.isEmpty()) {
            return businessPhone;
        }
        return phone;
    }

    // Enums
    public enum ApplicationStatus {
        PENDING("Pending"),
        UNDER_REVIEW("Under Review"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String displayName;

        ApplicationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ApplicantType {
        INDIVIDUAL("Individual"),
        BUSINESS("Business");

        private final String displayName;

        ApplicantType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PropertyType {
        HOTEL("Hotel"),
        MOTEL("Motel"),
        HOME("Home"),
        APARTMENT("Apartment"),
        ROOM("Room"),
        SHELTER("Shelter"),
        OTHER("Other");

        private final String displayName;

        PropertyType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PreferredContactMethod {
        EMAIL("Email"),
        PHONE("Phone"),
        EITHER("Either");

        private final String displayName;

        PreferredContactMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
