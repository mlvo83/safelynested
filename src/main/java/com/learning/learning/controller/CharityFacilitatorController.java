package com.learning.learning.controller;

import com.learning.learning.dto.BookingDto;
import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.Referral;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.service.BookingService;
import com.learning.learning.service.MultiFacilitatorService;
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
import java.util.Optional;

/**
 * Controller for Charity Facilitators, scoped per-request to a specific
 * charity via the {charityId} path variable. Both CHARITY_FACILITATOR
 * (single-charity) and MULTI_FACILITATOR (multi-charity) users share
 * this controller; per-request authorization is delegated to
 * MultiFacilitatorService.canFacilitateForCharity.
 *
 * URL pattern: /charity-facilitator/{charityId}/...
 */
@Controller
@RequestMapping("/charity-facilitator/{charityId}")
public class CharityFacilitatorController {

    @Autowired
    private MultiFacilitatorService multiFacilitatorService;

    @Autowired
    private CharityRepository charityRepository;

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

    @Autowired
    private PartnerLocationRepository partnerLocationRepository;

    /**
     * Tells templates whether the logged-in user is a multi-charity
     * facilitator, so the operating-as banner can render a "back to
     * all charities" link.
     */
    @ModelAttribute("isMultiFacilitator")
    public Boolean isMultiFacilitator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MULTI_FACILITATOR"));
    }

    /**
     * Verify the current user is authorized to facilitate for the given
     * charity, and load the Charity record. Returns Optional.empty() if
     * the user is not authorized — the caller should return a redirect
     * to /access-denied.
     */
    private Optional<Charity> authorize(String username, Long charityId) {
        if (!multiFacilitatorService.canFacilitateForCharity(username, charityId)) {
            return Optional.empty();
        }
        return charityRepository.findById(charityId);
    }

    /**
     * Charity Facilitator Dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(@PathVariable Long charityId, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        Map<String, Object> stats = getCharityFacilitatorStats(charityId);
        List<Referral> recentReferrals = referralRepository.findTop10ByCharityIdOrderByCreatedAtDesc(charityId);

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
    public String viewReferrals(@PathVariable Long charityId,
                                 @RequestParam(required = false) String status,
                                 Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        List<Referral> referrals;
        if (status != null && !status.isEmpty()) {
            Referral.ReferralStatus referralStatus = Referral.ReferralStatus.valueOf(status);
            referrals = referralRepository.findByCharityIdAndStatusOrderByCreatedAtDesc(charityId, referralStatus);
        } else {
            referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        }

        List<Long> referralIds = referrals.stream().map(Referral::getId).toList();
        Map<Long, Long> referralBookingMap = new HashMap<>();
        if (!referralIds.isEmpty()) {
            bookingRepository.findActiveBookingsByReferralIds(referralIds)
                    .forEach(b -> referralBookingMap.putIfAbsent(b.getReferral().getId(), b.getId()));
        }

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("referrals", referrals);
        model.addAttribute("referralBookingMap", referralBookingMap);
        model.addAttribute("selectedStatus", status);

        return "charity-facilitator/referrals";
    }

    /**
     * View Referral Details
     */
    @GetMapping("/referrals/{id}")
    public String viewReferralDetails(@PathVariable Long charityId,
                                       @PathVariable Long id,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals";
        }

        List<Booking> existingBookings = bookingRepository.findActiveBookingsByReferralIds(List.of(id));
        Long existingBookingId = existingBookings.isEmpty() ? null : existingBookings.get(0).getId();

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("referral", referral);
        model.addAttribute("existingBookingId", existingBookingId);

        return "charity-facilitator/referral-details";
    }

    /**
     * Approve Referral
     */
    @PostMapping("/referrals/{id}/approve")
    public String approveReferral(@PathVariable Long charityId,
                                   @PathVariable Long id,
                                   @RequestParam(required = false) String notes,
                                   RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals";
        }

        try {
            referralService.approveReferral(id, username, notes);
            redirectAttributes.addFlashAttribute("success", "Referral approved successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error approving referral: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/" + charityId + "/referrals";
    }

    /**
     * Reject Referral
     */
    @PostMapping("/referrals/{id}/reject")
    public String rejectReferral(@PathVariable Long charityId,
                                  @PathVariable Long id,
                                  @RequestParam String reason,
                                  RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals";
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

        return "redirect:/charity-facilitator/" + charityId + "/referrals";
    }

    /**
     * View All Bookings for this Charity
     */
    @GetMapping("/bookings")
    public String viewBookings(@PathVariable Long charityId,
                                @RequestParam(required = false) String status,
                                Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

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
    public String viewBookingDetails(@PathVariable Long charityId,
                                      @PathVariable Long id,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        Booking booking = bookingService.getBookingById(id);

        if (booking.getReferral() == null
                || booking.getReferral().getCharity() == null
                || !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
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
    public String showCreateBookingForm(@PathVariable Long charityId,
                                         @RequestParam Long referralId,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        Referral referral = referralRepository.findByIdAndCharityId(referralId, charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals";
        }

        if (referral.getStatus() != Referral.ReferralStatus.APPROVED) {
            redirectAttributes.addFlashAttribute("error", "Can only create bookings for approved referrals.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals/" + referralId;
        }

        BookingDto bookingDto = new BookingDto();
        bookingDto.setReferralId(referralId);

        if (referral.getSelectedLocation() != null) {
            bookingDto.setLocationId(referral.getSelectedLocation().getId());
            bookingDto.setLocationSelection("charity:" + referral.getSelectedLocation().getId());
        } else if (referral.getSelectedPartnerLocation() != null) {
            bookingDto.setPartnerLocationId(referral.getSelectedPartnerLocation().getId());
            bookingDto.setLocationSelection("partner:" + referral.getSelectedPartnerLocation().getId());
        }

        List<CharityLocation> locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(charityId);
        List<com.learning.learning.entity.PartnerLocation> partnerLocations =
                partnerLocationRepository.findActiveLinkedToCharity(charityId);
        List<BookingService.AvailableDonation> availableDonations =
                bookingService.getAvailableDonationsForCharity(charityId);

        model.addAttribute("username", username);
        model.addAttribute("charity", charity);
        model.addAttribute("bookingDto", bookingDto);
        model.addAttribute("referral", referral);
        model.addAttribute("locations", locations);
        model.addAttribute("partnerLocations", partnerLocations);
        model.addAttribute("availableDonations", availableDonations);
        addPreferredLocationToModel(model, referral);

        return "charity-facilitator/booking-form";
    }

    private void addPreferredLocationToModel(Model model, Referral referral) {
        if (referral.getSelectedLocation() != null) {
            CharityLocation cl = referral.getSelectedLocation();
            String label = cl.getLocationName();
            if (cl.getCity() != null) label += " — " + cl.getCity();
            if (cl.getState() != null) label += ", " + cl.getState();
            model.addAttribute("preferredLocationLabel", label);
            model.addAttribute("preferredLocationType", "charity");
            model.addAttribute("hasPreferredLocation", true);
        } else if (referral.getSelectedPartnerLocation() != null) {
            com.learning.learning.entity.PartnerLocation pl = referral.getSelectedPartnerLocation();
            String label = pl.getName();
            if (pl.getCity() != null) label += " — " + pl.getCity();
            if (pl.getState() != null) label += ", " + pl.getState();
            model.addAttribute("preferredLocationLabel", label);
            model.addAttribute("preferredLocationType", "partner");
            model.addAttribute("hasPreferredLocation", true);
        } else {
            model.addAttribute("hasPreferredLocation", false);
        }
    }

    /**
     * Create Booking
     */
    @PostMapping("/bookings/new")
    public String createBooking(@PathVariable Long charityId,
                                 @Valid @ModelAttribute("bookingDto") BookingDto bookingDto,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";
        Charity charity = auth.get();

        Referral referral = referralRepository.findByIdAndCharityId(bookingDto.getReferralId(), charityId).orElse(null);
        if (referral == null) {
            redirectAttributes.addFlashAttribute("error", "Referral not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/referrals";
        }

        if (bindingResult.hasErrors()) {
            List<CharityLocation> locations = charityLocationRepository.findByCharityIdAndIsActiveTrue(charityId);
            List<com.learning.learning.entity.PartnerLocation> partnerLocations =
                    partnerLocationRepository.findActiveLinkedToCharity(charityId);
            List<BookingService.AvailableDonation> availableDonations =
                    bookingService.getAvailableDonationsForCharity(charityId);
            model.addAttribute("username", username);
            model.addAttribute("charity", charity);
            model.addAttribute("referral", referral);
            model.addAttribute("locations", locations);
            model.addAttribute("partnerLocations", partnerLocations);
            model.addAttribute("availableDonations", availableDonations);
            addPreferredLocationToModel(model, referral);
            return "charity-facilitator/booking-form";
        }

        try {
            Booking booking = bookingService.createBooking(bookingDto, username);
            redirectAttributes.addFlashAttribute("success",
                    "Booking created successfully! Confirmation Code: " + booking.getConfirmationCode());
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating booking: " + e.getMessage());
            return "redirect:/charity-facilitator/" + charityId + "/referrals/" + bookingDto.getReferralId();
        }
    }

    /**
     * Update Booking Status
     */
    @PostMapping("/bookings/{id}/status")
    public String updateBookingStatus(@PathVariable Long charityId,
                                       @PathVariable Long id,
                                       @RequestParam String status,
                                       @RequestParam(required = false) String completionNotes,
                                       RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Booking booking = bookingService.getBookingById(id);
        if (booking.getReferral() == null
                || booking.getReferral().getCharity() == null
                || !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
        }

        try {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status);
            bookingService.updateBookingStatus(id, bookingStatus, completionNotes);
            redirectAttributes.addFlashAttribute("success", "Booking status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating booking: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/" + charityId + "/bookings";
    }

    /**
     * Cancel Booking
     */
    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(@PathVariable Long charityId,
                                 @PathVariable Long id,
                                 @RequestParam String reason,
                                 RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Booking booking = bookingService.getBookingById(id);
        if (booking.getReferral() == null
                || booking.getReferral().getCharity() == null
                || !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
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

        return "redirect:/charity-facilitator/" + charityId + "/bookings";
    }

    /**
     * Check-in Guest
     */
    @PostMapping("/bookings/{id}/checkin")
    public String checkInGuest(@PathVariable Long charityId,
                                @PathVariable Long id,
                                @RequestParam(required = false) String notes,
                                RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Booking booking = bookingService.getBookingById(id);
        if (booking.getReferral() == null
                || booking.getReferral().getCharity() == null
                || !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
        }

        try {
            bookingService.checkIn(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked in successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking in guest: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/" + charityId + "/bookings/" + id;
    }

    /**
     * Check-out Guest
     */
    @PostMapping("/bookings/{id}/checkout")
    public String checkOutGuest(@PathVariable Long charityId,
                                 @PathVariable Long id,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Optional<Charity> auth = authorize(username, charityId);
        if (auth.isEmpty()) return "redirect:/access-denied";

        Booking booking = bookingService.getBookingById(id);
        if (booking.getReferral() == null
                || booking.getReferral().getCharity() == null
                || !booking.getReferral().getCharity().getId().equals(charityId)) {
            redirectAttributes.addFlashAttribute("error", "Booking not found or access denied.");
            return "redirect:/charity-facilitator/" + charityId + "/bookings";
        }

        try {
            bookingService.checkOut(id, notes);
            redirectAttributes.addFlashAttribute("success", "Guest checked out successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error checking out guest: " + e.getMessage());
        }

        return "redirect:/charity-facilitator/" + charityId + "/bookings/" + id;
    }

    /**
     * Get charity-specific statistics for dashboard
     */
    private Map<String, Object> getCharityFacilitatorStats(Long charityId) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("pendingReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.PENDING));
        stats.put("approvedReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.APPROVED));
        stats.put("rejectedReferrals", referralRepository.countByCharityIdAndStatus(charityId, Referral.ReferralStatus.REJECTED));
        stats.put("totalReferrals", referralRepository.countByCharityId(charityId));

        stats.put("confirmedBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.CONFIRMED));
        stats.put("checkedInBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.CHECKED_IN));
        stats.put("completedBookings", bookingRepository.countByCharityIdAndStatus(charityId, Booking.BookingStatus.COMPLETED));
        stats.put("totalBookings", bookingRepository.countByCharityId(charityId));

        return stats;
    }
}
