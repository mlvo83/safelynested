package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id")
    private Referral referral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_id")
    private ReferralInvite invite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private Donor donor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    // For documents uploaded by participants via public invite link
    @Column(name = "uploaded_by_participant")
    private Boolean uploadedByParticipant = false;

    @Column(name = "participant_name", length = 255)
    private String participantName;

    // File information
    @Column(name = "document_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    // Metadata
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    // Timestamp
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    // Document Types Enum
    public enum DocumentType {
        // Beneficiary document types
        ID_CARD("Government-issued ID"),
        DRIVERS_LICENSE("Driver's License"),
        PASSPORT("Passport"),
        BIRTH_CERTIFICATE("Birth Certificate"),
        SOCIAL_SECURITY_CARD("Social Security Card"),
        PROOF_OF_ADDRESS("Proof of Address"),
        UTILITY_BILL("Utility Bill"),
        INCOME_VERIFICATION("Income Verification"),
        PAY_STUB("Pay Stub"),
        TAX_RETURN("Tax Return"),
        BANK_STATEMENT("Bank Statement"),
        LEASE_AGREEMENT("Lease Agreement"),
        EVICTION_NOTICE("Eviction Notice"),
        MEDICAL_RECORDS("Medical Records"),
        SCHOOL_RECORDS("School Records"),
        EMPLOYMENT_LETTER("Employment Letter"),
        REFERENCE_LETTER("Reference Letter"),

        // Donor document types
        DONATION_RECEIPT("Donation Receipt"),
        VERIFICATION_OF_FUNDS("Verification of Funds"),
        DONOR_AGREEMENT("Donor Agreement"),
        TAX_EXEMPTION_LETTER("Tax Exemption Letter"),
        WIRE_TRANSFER_CONFIRMATION("Wire Transfer Confirmation"),
        CHECK_COPY("Check Copy"),

        OTHER("Other Document");

        private final String displayName;

        DocumentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}