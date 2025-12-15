package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmation_code", nullable = false, unique = true, length = 20)
    private String confirmationCode;

    @Column(name = "confirmation_number", unique = true, length = 255)
    private String confirmationNumber;

    // CHANGED: FetchType.LAZY prevents loading issues
    // CHANGED: nullable = true allows bookings to exist even if referral is deleted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id")
    private Referral referral;

    // Store referral info directly so we have it even if referral is deleted
    @Column(name = "participant_name", length = 200)
    private String participantName;

    @Column(name = "participant_phone", length = 20)
    private String participantPhone;

    @Column(name = "participant_email", length = 100)
    private String participantEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    // Store location info directly for historical purposes
    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "location_address", length = 500)
    private String locationAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by_user_id")
    private User bookedByUser;

    // Date and time fields
    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "actual_check_in")
    private LocalDateTime actualCheckIn;

    @Column(name = "actual_check_out")
    private LocalDateTime actualCheckOut;

    @Column(name = "nights")
    private Integer nights = 1;

    @Column(name = "number_of_nights")
    private Integer numberOfNights;

    // Status fields
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "booking_status", length = 255)
    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    // Payment fields
    @Column(name = "cost", precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(name = "payment_method", length = 255)
    private String paymentMethod;

    @Column(name = "payment_status", length = 255)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "paid_by", length = 255)
    private String paidBy;

    // Redemption fields
    @Column(name = "is_redeemed")
    private Boolean isRedeemed = false;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "redeemed_by", length = 100)
    private String redeemedBy;

    // Notes and instructions
    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "notes", length = 2000)
    private String notes;

    // External reference
    @Column(name = "external_booking_reference", length = 255)
    private String externalBookingReference;

    @Column(name = "request_id")
    private Long requestId;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        bookingDate = LocalDateTime.now();

        // Auto-calculate nights if check-in and check-out dates are set
        if (checkInDate != null && checkOutDate != null) {
            numberOfNights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate);
            nights = numberOfNights;
        }

        // Copy participant info from referral for historical purposes
        copyReferralInfo();
        copyLocationInfo();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // Auto-calculate nights if check-in and check-out dates are set
        if (checkInDate != null && checkOutDate != null) {
            numberOfNights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate);
            nights = numberOfNights;
        }
    }

    /**
     * Copy referral info to booking for historical record
     */
    private void copyReferralInfo() {
        if (referral != null && participantName == null) {
            this.participantName = referral.getParticipantName();
            this.participantPhone = referral.getParticipantPhone();
            this.participantEmail = referral.getParticipantEmail();
        }
    }

    /**
     * Copy location info to booking for historical record
     */
    private void copyLocationInfo() {
        if (location != null && locationName == null) {
            this.locationName = location.getLocationName();
            this.locationAddress = location.getAddress();
        }
    }

    // Helper methods to safely get participant info (works even if referral is deleted)
    public String getDisplayParticipantName() {
        if (participantName != null) {
            return participantName;
        }
        try {
            return referral != null ? referral.getParticipantName() : "Unknown";
        } catch (Exception e) {
            return participantName != null ? participantName : "Unknown";
        }
    }

    public String getDisplayLocationName() {
        if (locationName != null) {
            return locationName;
        }
        try {
            return location != null ? location.getLocationName() : "Unknown";
        } catch (Exception e) {
            return locationName != null ? locationName : "Unknown";
        }
    }

    // Check if referral still exists
    public boolean hasValidReferral() {
        try {
            return referral != null && referral.getId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    // Enums
    public enum BookingStatus {
        PENDING,
        CONFIRMED,
        CHECKED_IN,
        CHECKED_OUT,
        CANCELLED,
        NO_SHOW,
        COMPLETED
    }

    public enum PaymentStatus {
        PENDING,
        PAID,
        PARTIALLY_PAID,
        REFUNDED,
        PROGRAM_FUNDED,
        WAIVED
    }
}