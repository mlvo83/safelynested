package com.learning.learning.controller;

import com.learning.learning.dto.BookingDto;
import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.Referral;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.service.BookingService;
import com.learning.learning.service.CharityService;
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

/**
 * Controller for Charity Facilitators.
 *
 * Charity Facilitators can:
 * - View referrals for their assigned charity only
 * - Approve/reject referrals for their charity
 * - View and manage bookings for their charity
 * - Check-in/check-out guests for their charity's bookings
 *
 * They CANNOT:
 * - See referrals/bookings from other charities
 * - Create new referrals (that's for Charity Partners)
 * - Send invites (that's for Charity Partners)
 */
@Controller
@RequestMapping("/charity-facilitator")
public class CharityFacilitatorController {

    @Autowired
    private CharityService charityService;

    @Autowired
    private ReferralService referralService;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CharityLocationRepository charityLocationRepository;

    /**
     * Charity Facilitator Dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Get charity-specific statistics
        Map<String, Object> stats = getCharityFacilitatorStats(charityId);

        // Get recent referrals for this charity
        List<Referral> recentReferrals = referralRepository.findTop10ByCharityIdOrderByCreatedAtDesc(charityId);

        // Get recent bookings for this charity
        List<Booking> recentBookings = bookingRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        if (recentBookings.size() > 10) {
            recentBookings = recentBookings.subList(0, 10);
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("role", "Charity Facilitator");
        model.addAttribute("stats", stats);
        model.addAttribute("recentReferrals", recentReferrals);
        model.addAttribute("recentBookings", recentBookings);

        return "charity-facilitator/dashboard";
    }

    /**
     * View Referrals for this Charity
     */
    @GetMapping("/referrals")
    public String viewReferrals(@RequestParam(required = false) String status, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        List<Referral> referrals;
        if (status != null && !status.isEmpty()) {
            Referral.ReferralStatus referralStatus = Referral.ReferralStatus.valueOf(status);
            referrals = referralRepository.findByCharityIdAndStatusOrderByCreatedAtDesc(charityId, referralStatus);
        } else {
            referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("referrals", referrals);
        model.addAttribute("selectedStatus", status);

        return "charity-facilitator/referrals";
    }

    /**
     * View Referral Details
     */
    @GetMapping("/referrals/{id}")
    public String viewReferralDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Ensure referral belongs to this charity
        Referral referral = referralRepository.findByIdAndCharityId(id, charityId)
                .orElse(null);

        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/referrals";
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("referral", referral);

        return "charity-facilitator/referral-details";
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

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Verify referral belongs to this charity
        Referral referral = referralRepository.findByIdAndCharityId(id, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/referrals";
        }

        try {
            referralService.approveReferral(id, username, notes);
            redirectAttributes.addFlashAttribute("success", "Referral approved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error approving referral: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/referrals";
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

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Verify referral belongs to this charity
        Referral referral = referralRepository.findByIdAndCharityId(id, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/referrals";
        }

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Rejection reason is required");
            }
            referralService.rejectReferral(id, username, reason);
            redirectAttributes.addFlashAttribute("success", "Referral rejected.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error rejecting referral: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/referrals";
    }

    /**
     * View All Bookings for this Charity
     */
    @GetMapping("/bookings")
    public String viewBookings(@RequestParam(required = false) String status, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        List<Booking> bookings;
        if (status != null && !status.isEmpty()) {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status);
            bookings = bookingRepository.findByCharityIdAndStatusOrderByCreatedAtDesc(charityId, bookingStatus);
        } else {
            bookings = bookingRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("bookings", bookings);
        model.addAttribute("selectedStatus", status);

        return "charity-facilitator/bookings";
    }

    /**
     * View Booking Details
     */
    @GetMapping("/bookings/{id}")
    public String viewBookingDetails(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        Booking booking = bookingService.getBookingById(id);

        // Verify booking belongs to this charity
        if (booking.getReferral() == null ||
            booking.getReferral().getCharity() == null ||
            !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/bookings";
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("booking", booking);

        return "charity-facilitator/booking-details";
    }

    /**
     * Show Create Booking Form
     */
    @GetMapping("/bookings/new")
    public String showCreateBookingForm(@RequestParam Long referralId, Model model, RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Verify referral belongs to this charity
        Referral referral = referralRepository.findByIdAndCharityId(referralId, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/referrals";
        }

        if (referral.getStatus() != Referral.ReferralStatus.APPROVED) {
            redirectAttributes.addFlashAttribute("error", "Can only create bookings for approved referrals.");
            return "redirect:/charity-facilitator/referrals/" + referralId;
        }

        BookingDto bookingDto = new BookingDto();
        bookingDto.setReferralId(referralId);

        // Pre-select the location the participant chose (if any)
        if (referral.getSelectedLocation() != null) {
            bookingDto.setLocationId(referral.getSelectedLocation().getId());
        }

        // Get active locations for this charity
        List<CharityLocation> locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(charityId);

        // Get available donations for this charity
        List<BookingService.AvailableDonation> availableDonations =
                bookingService.getAvailableDonationsForCharity(charityId);

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("bookingDto", bookingDto);
        model.addAttribute("referral", referral);
        model.addAttribute("locations", locations);
        model.addAttribute("availableDonations", availableDonations);

        return "charity-facilitator/booking-form";
    }

    /**
     * Create Booking
     */
    @PostMapping("/bookings/new")
    public String createBooking(
            @Valid @ModelAttribute("bookingDto") BookingDto bookingDto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Verify referral belongs to this charity
        Referral referral = referralRepository.findByIdAndCharityId(bookingDto.getReferralId(), charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/referrals";
        }

        if (bindingResult.hasErrors()) {
            List<CharityLocation> locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(charityId);
            List<BookingService.AvailableDonation> availableDonations =
                    bookingService.getAvailableDonationsForCharity(charityId);
            model.addAttribute("username", username);
            model.addAttribute("charity", charity);
            model.addAttribute("referral", referral);
            model.addAttribute("locations", locations);
            model.addAttribute("availableDonations", availableDonations);
            return "charity-facilitator/booking-form";
        }

        try {
            Booking booking = bookingService.createBooking(bookingDto, username);
            redirectAttributes.addFlashAttribute("success",
                    "Booking created successfully! Confirmation Code: " + booking.getConfirmationCode());
            return "redirect:/charity-facilitator/bookings";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating booking: " + e.getMessage());
            return "redirect:/charity-facilitator/referrals/" + bookingDto.getReferralId();
        }
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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        Booking booking = bookingService.getBookingById(id);

        // Verify booking belongs to this charity
        if (booking.getReferral() == null ||
            booking.getReferral().getCharity() == null ||
            !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/bookings";
        }

        try {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status);
            bookingService.updateBookingStatus(id, bookingStatus, completionNotes);
            redirectAttributes.addFlashAttribute("success", "Booking status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating booking: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/bookings";
    }

    /**
     * Cancel Booking
     */
    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(
            @PathVariable Long id,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        Booking booking = bookingService.getBookingById(id);

        // Verify booking belongs to this charity
        if (booking.getReferral() == null ||
            booking.getReferral().getCharity() == null ||
            !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/bookings";
        }

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Cancellation reason is required");
            }
            bookingService.cancelBooking(id, reason);
            redirectAttributes.addFlashAttribute("success", "Booking cancelled successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error cancelling booking: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/bookings";
    }

    /**
     * Check-in Guest
     */
    @PostMapping("/bookings/{id}/checkin")
    public String checkInGuest(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        Booking booking = bookingService.getBookingById(id);

        // Verify booking belongs to this charity
        if (booking.getReferral() == null ||
            booking.getReferral().getCharity() == null ||
            !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/bookings";
        }

        try {
            bookingService.checkIn(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked in successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking in guest: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/bookings/" + id;
    }

    /**
     * Check-out Guest
     */
    @PostMapping("/bookings/{id}/checkout")
    public String checkOutGuest(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        Booking booking = bookingService.getBookingById(id);

        // Verify booking belongs to this charity
        if (booking.getReferral() == null ||
            booking.getReferral().getCharity() == null ||
            !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/bookings";
        }

        try {
            bookingService.checkOut(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked out successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking out guest: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/bookings/" + id;
    }

    /**
     * Get charity-specific statistics for dashboard
     */
    private Map<String, Object> getCharityFacilitatorStats(Long charityId) {
        Map<String, Object> stats = new HashMap<>();

        // Referral stats
        stats.put("pendingReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.PENDING));
        stats.put("approvedReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.APPROVED));
        stats.put("rejectedReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.REJECTED));
        stats.put("totalReferrals", referralRepository.countByCharityId(charityId));

        // Booking stats
        stats.put("confirmedBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.CONFIRMED));
        stats.put("checkedInBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.CHECKED_IN));
        stats.put("completedBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.COMPLETED));
        stats.put("totalBookings", bookingRepository.countByCharityId(charityId));

        return stats;
    }
}
