package com.learning.learning.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingDto {

    @NotNull(message = "Referral ID is required")
    private Long referralId;

    // One of locationId / partnerLocationId must be set (server-side validation).
    // Removed @NotNull because the form now selects a CharityLocation OR a PartnerLocation.
    private Long locationId;

    private Long partnerLocationId;

    /**
     * The single dropdown sends a prefixed value:
     *   "charity:{id}"  → CharityLocation
     *   "partner:{id}"  → PartnerLocation
     * BookingService parses this on submit.
     */
    private String locationSelection;

    @NotNull(message = "Check-in date is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate checkOutDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime checkInTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime checkOutTime;

    @Min(value = 1, message = "Number of nights must be at least 1")
    private Integer nights;

    // Payment fields
    private BigDecimal cost;

    private String paymentMethod;

    private String paymentStatus;

    private String paidBy;

    // Notes and instructions
    private String specialInstructions;

    private String notes;

    // External reference
    private String externalBookingReference;

    // Status
    private String bookingStatus;

    // Donation funding
    private Long fundingDonationId;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;
}
