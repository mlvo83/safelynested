package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "charities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Charity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charity_name", nullable = false, unique = true, length = 200)
    private String charityName;

    @Column(name = "organization_type", length = 100)
    private String organizationType;

    @Column(name = "ein_tax_id", length = 20)
    private String einTaxId;

    // Contact Information
    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    // Address
    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country = "USA";

    // Settings
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "max_referrals_per_month")
    private Integer maxReferralsPerMonth = 50;

    @Column(name = "allowed_zip_codes", columnDefinition = "TEXT")
    private String allowedZipCodes; // Comma-separated list

    // Description and notes
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "mission_statement", columnDefinition = "TEXT")
    private String missionStatement;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
