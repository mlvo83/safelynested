package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "partner_locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_partner_id", nullable = false)
    private LocationPartner locationPartner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", length = 30)
    private StayPartnerApplication.PropertyType propertyType;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 50)
    private String state;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country = "USA";

    @Column(name = "number_of_bedrooms")
    private Integer numberOfBedrooms;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "nightly_rate", precision = 10, scale = 2)
    private BigDecimal nightlyRate;

    @Column(name = "amenities", columnDefinition = "TEXT")
    private String amenities;

    @Column(name = "accessibility_features", columnDefinition = "TEXT")
    private String accessibilityFeatures;

    @Column(name = "pets_allowed")
    private Boolean petsAllowed = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;

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
}
