package com.learning.learning.controller;




import com.learning.learning.dto.BookingDto;
import com.learning.learning.entity.Booking;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.Referral;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.service.BookingService;
import com.learning.learning.service.ReferralService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/facilitator")
public class FacilitatorController {

    @Autowired
    private ReferralService referralService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CharityLocationRepository charityLocationRepository;

    /**
     * Facilitator Dashboard - Overview
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // Get statistics
        ReferralService.ReferralStatistics referralStats = referralService.getStatistics();
        BookingService.BookingStatistics bookingStats = bookingService.getStatistics();

        model.addAttribute("username", username);
        model.addAttribute("role", "Facilitator");
        model.addAttribute("referralStats", referralStats);
        model.addAttribute("bookingStats", bookingStats);

        return "facilitator/dashboard";
    }

    /**
     * View All Referrals
     */
    @GetMapping("/referrals")
    public String viewReferrals(@RequestParam(required = false) String status, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        List<Referral> referrals;
        if (status != null && !status.isEmpty()) {
            referrals = referralService.getReferralsByStatus(Referral.ReferralStatus.valueOf(status));
        } else {
            referrals = referralService.getAllReferrals();
        }

        // Build referralId -> bookingId map for approved referrals
        List<Long> referralIds = referrals.stream().map(Referral::getId).toList();
        Map<Long, Long> referralBookingMap = new HashMap<>();
        if (!referralIds.isEmpty()) {
            bookingRepository.findActiveBookingsByReferralIds(referralIds)
                    .forEach(b -> referralBookingMap.putIfAbsent(b.getReferral().getId(), b.getId()));
        }

        model.addAttribute("username", username);
        model.addAttribute("referrals", referrals);
        model.addAttribute("referralBookingMap", referralBookingMap);
        model.addAttribute("selectedStatus", status);

        return "facilitator/referrals";
    }

    /**
     * View Referral Details
     */
    @GetMapping("/referrals/{id}")
    public String viewReferralDetails(@PathVariable Long id, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Referral referral = referralService.getReferralById(id);

        // Check if this referral already has a booking
        List<Booking> existingBookings = bookingRepository.findActiveBookingsByReferralIds(List.of(id));
        Long existingBookingId = existingBookings.isEmpty() ? null : existingBookings.get(0).getId();

        model.addAttribute("username", username);
        model.addAttribute("referral", referral);
        model.addAttribute("existingBookingId", existingBookingId);

        return "facilitator/referral-details";
    }

    /**
     * Approve Referral
     */
    @PostMapping("/referrals/{id}/approve")
    public String approveReferral(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        try {
            referralService.approveReferral(id, username, notes);
            redirectAttributes.addFlashAttribute("success", "Referral approved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error approving referral: " + e.getMessage());
        }

        return "redirect:/facilitator/referrals";
    }

    /**
     * Reject Referral
     */
    @PostMapping("/referrals/{id}/reject")
    public String rejectReferral(
            @PathVariable Long id,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Rejection reason is required");
            }
            referralService.rejectReferral(id, username, reason);
            redirectAttributes.addFlashAttribute("success", "Referral rejected successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error rejecting referral: " + e.getMessage());
        }

        return "redirect:/facilitator/referrals";
    }

    /**
     * Show Create Booking Form
     * UPDATED: Now includes locations in the model
     */
    @GetMapping("/bookings/new")
    public String showCreateBookingForm(@RequestParam Long referralId, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Referral referral = referralService.getReferralById(referralId);

        if (referral.getStatus() != Referral.ReferralStatus.APPROVED) {
            throw new RuntimeException("Can only create bookings for approved referrals");
        }

        BookingDto bookingDto = new BookingDto();
        bookingDto.setReferralId(referralId);

        // Pre-select the location the participant chose (if any)
        if (referral.getSelectedLocation() != null) {
            bookingDto.setLocationId(referral.getSelectedLocation().getId());
        }

        // Get active locations for the referral's charity
        List<CharityLocation> locations;
        if (referral.getCharity() != null) {
            locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(referral.getCharity().getId());
        } else {
            locations = charityLocationRepository.findAllActiveLocationsOrderByCharityAndName();
        }

        // Get available donations for this charity
        List<BookingService.AvailableDonation> availableDonations = java.util.Collections.emptyList();
        if (referral.getCharity() != null) {
            availableDonations = bookingService.getAvailableDonationsForCharity(referral.getCharity().getId());
        }

        model.addAttribute("username", username);
        model.addAttribute("bookingDto", bookingDto);
        model.addAttribute("referral", referral);
        model.addAttribute("locations", locations);
        model.addAttribute("availableDonations", availableDonations);

        return "facilitator/booking-form";
    }

    /**
     * Create Booking
     * UPDATED: Uses new comprehensive booking service
     */
    @PostMapping("/bookings/new")
    public String createBooking(
            @Valid @ModelAttribute("bookingDto") BookingDto bookingDto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        if (bindingResult.hasErrors()) {
            Referral referral = referralService.getReferralById(bookingDto.getReferralId());
            List<CharityLocation> locations;
            List<BookingService.AvailableDonation> availableDonations = java.util.Collections.emptyList();
            if (referral.getCharity() != null) {
                locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(referral.getCharity().getId());
                availableDonations = bookingService.getAvailableDonationsForCharity(referral.getCharity().getId());
            } else {
                locations = charityLocationRepository.findAllActiveLocationsOrderByCharityAndName();
            }
            model.addAttribute("username", username);
            model.addAttribute("referral", referral);
            model.addAttribute("locations", locations);
            model.addAttribute("availableDonations", availableDonations);
            return "facilitator/booking-form";
        }

        try {
            Booking booking = bookingService.createBooking(bookingDto, username);
            redirectAttributes.addFlashAttribute("success",
                    "Booking created successfully! Confirmation Code: " + booking.getConfirmationCode());
            return "redirect:/facilitator/bookings";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating booking: " + e.getMessage());
            return "redirect:/facilitator/referrals/" + bookingDto.getReferralId();
        }
    }

    /**
     * View All Bookings
     */
    @GetMapping("/bookings")
    public String viewBookings(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        List<Booking> bookings = bookingService.getAllBookings();

        model.addAttribute("username", username);
        model.addAttribute("bookings", bookings);

        return "facilitator/bookings";
    }

    /**
     * View Booking Details
     */
    @GetMapping("/bookings/{id}")
    public String viewBookingDetails(@PathVariable Long id, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Booking booking = bookingService.getBookingById(id);

        model.addAttribute("username", username);
        model.addAttribute("booking", booking);

        return "facilitator/booking-details";
    }

    /**
     * Update Booking Status
     */
    @PostMapping("/bookings/{id}/status")
    public String updateBookingStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String completionNotes,
            RedirectAttributes redirectAttributes) {

        try {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status);
            bookingService.updateBookingStatus(id, bookingStatus, completionNotes);
            redirectAttributes.addFlashAttribute("success", "Booking status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating booking: " + e.getMessage());
        }

        return "redirect:/facilitator/bookings";
    }

    /**
     * Cancel Booking
     */
    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(
            @PathVariable Long id,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Cancellation reason is required");
            }
            bookingService.cancelBooking(id, reason);
            redirectAttributes.addFlashAttribute("success", "Booking cancelled successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error cancelling booking: " + e.getMessage());
        }

        return "redirect:/facilitator/bookings";
    }

    /**
     * Check-in Guest
     */
    @PostMapping("/bookings/{id}/checkin")
    public String checkInGuest(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            bookingService.checkIn(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked in successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking in guest: " + e.getMessage());
        }

        return "redirect:/facilitator/bookings/" + id;
    }

    /**
     * Check-out Guest
     */
    @PostMapping("/bookings/{id}/checkout")
    public String checkOutGuest(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            bookingService.checkOut(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked out successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking out guest: " + e.getMessage());
        }

        return "redirect:/facilitator/bookings/" + id;
    }
}
