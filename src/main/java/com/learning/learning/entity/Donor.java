package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "donors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Donor type - Individual person or Business/Organization
    @Enumerated(EnumType.STRING)
    @Column(name = "donor_type", nullable = false)
    private DonorType donorType = DonorType.INDIVIDUAL;

    // Business-specific fields (only used when donorType = BUSINESS)
    @Column(name = "business_name")
    private String businessName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Many-to-many relationship with charities (a donor can donate to multiple charities)
    @ManyToMany
    @JoinTable(
            name = "donor_charities",
            joinColumns = @JoinColumn(name = "donor_id"),
            inverseJoinColumns = @JoinColumn(name = "charity_id")
    )
    private Set<Charity> charities = new HashSet<>();

    // One-to-many relationship with donations
    @OneToMany(mappedBy = "donor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Donation> donations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Helper methods
    public void addCharity(Charity charity) {
        this.charities.add(charity);
    }

    public void removeCharity(Charity charity) {
        this.charities.remove(charity);
    }

    public boolean isAssociatedWithCharity(Long charityId) {
        return charities.stream()
                .anyMatch(c -> c.getId().equals(charityId));
    }

    public String getDonorName() {
        if (donorType == DonorType.BUSINESS && businessName != null) {
            return businessName;
        }
        return user != null ? user.getFullName() : "Unknown";
    }

    public String getDonorEmail() {
        return user != null ? user.getEmail() : null;
    }

    public String getDisplayName() {
        if (donorType == DonorType.BUSINESS) {
            return businessName != null ? businessName : "Unknown Business";
        }
        return user != null ? user.getFullName() : "Unknown";
    }

    public String getContactInfo() {
        if (donorType == DonorType.BUSINESS) {
            return contactName != null ? contactName : getDonorEmail();
        }
        return user != null ? user.getFullName() : null;
    }

    public boolean isBusiness() {
        return donorType == DonorType.BUSINESS;
    }

    public boolean isIndividual() {
        return donorType == DonorType.INDIVIDUAL;
    }

    // Enum for donor type
    public enum DonorType {
        INDIVIDUAL("Individual"),
        BUSINESS("Business/Organization");

        private final String displayName;

        DonorType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
