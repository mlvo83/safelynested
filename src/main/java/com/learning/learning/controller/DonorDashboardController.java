package com.learning.learning.controller;

import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donation;
import com.learning.learning.entity.Donor;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.service.DonationService;
import com.learning.learning.service.DonorDashboardService;
import com.learning.learning.service.DonorService;
import com.learning.learning.service.SituationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

/**
 * Controller for the Donor's own dashboard view.
 * Donors can view their donations, stats, and impact.
 * This is a READ-ONLY view - donors cannot modify data.
 */
@Controller
@RequestMapping("/donor")
@PreAuthorize("hasRole('DONOR')")
public class DonorDashboardController {

    @Autowired
    private DonorService donorService;

    @Autowired
    private DonationService donationService;

    @Autowired
    private DonorDashboardService donorDashboardService;

    @Autowired
    private SituationService situationService;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // ========================================
    // DASHBOARD
    // ========================================

    /**
     * Main donor dashboard - shows overall stats and recent activity
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        // Get overall stats across all charities
        DonorDashboardService.DonorDashboardStats stats = donorDashboardService.getDashboardStats(donor.getId());

        // Get recent donations (limit to 5)
        List<Donation> recentDonations = donationService.getDonationsForDonor(donor.getId());
        List<Donation> limitedDonations = recentDonations.stream().limit(5).toList();

        // Get charities the donor supports
        Set<Charity> charities = donor.getCharities();

        model.addAttribute("donor", donor);
        model.addAttribute("stats", stats);
        model.addAttribute("recentDonations", limitedDonations);
        model.addAttribute("charities", charities);
        model.addAttribute("username", username);

        return "donor/dashboard";
    }

    // ========================================
    // DONATIONS
    // ========================================

    /**
     * View all donations
     */
    @GetMapping("/donations")
    public String listDonations(
            @RequestParam(required = false) Long charityId,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        List<Donation> donations;
        Charity selectedCharity = null;

        if (charityId != null) {
            // Filter by charity
            donations = donationService.getDonationsForDonorAtCharity(donor.getId(), charityId);
            selectedCharity = charityRepository.findById(charityId).orElse(null);
        } else {
            // All donations
            donations = donationService.getDonationsForDonor(donor.getId());
        }

        // Get stats
        DonorDashboardService.DonorDashboardStats stats;
        if (charityId != null) {
            stats = donorDashboardService.getDashboardStatsForCharity(donor.getId(), charityId);
        } else {
            stats = donorDashboardService.getDashboardStats(donor.getId());
        }

        // Compute usage per donation
        java.util.Map<Long, java.math.BigDecimal> donationAmountUsed = new java.util.HashMap<>();
        for (Donation donation : donations) {
            java.math.BigDecimal used = bookingRepository.sumFundedAmountByDonationId(donation.getId());
            donationAmountUsed.put(donation.getId(), used);
        }

        model.addAttribute("donor", donor);
        model.addAttribute("donations", donations);
        model.addAttribute("stats", stats);
        model.addAttribute("charities", donor.getCharities());
        model.addAttribute("selectedCharityId", charityId);
        model.addAttribute("selectedCharity", selectedCharity);
        model.addAttribute("donationAmountUsed", donationAmountUsed);

        return "donor/donations";
    }

    /**
     * View single donation detail
     */
    @GetMapping("/donations/{id}")
    public String viewDonation(@PathVariable Long id, Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        // Get donation and verify it belongs to this donor
        Donation donation = donationService.getDonationById(id);
        if (!donation.getDonor().getId().equals(donor.getId())) {
            return "redirect:/donor/donations?error=Access+denied";
        }

        // Get situations funded by this specific donation (if any)
        List<SituationService.DonorSituationView> situations =
                situationService.getSituationsForDonation(id);

        // Get bookings funded by this donation (privacy-safe: no participant PII)
        List<Booking> fundedBookings = bookingRepository.findByFundingDonationId(id);

        model.addAttribute("donor", donor);
        model.addAttribute("donation", donation);
        model.addAttribute("situations", situations);
        model.addAttribute("fundedBookings", fundedBookings);

        return "donor/donation-detail";
    }

    // ========================================
    // IMPACT / SITUATIONS
    // ========================================

    /**
     * View impact - situations funded by donor
     */
    @GetMapping("/impact")
    public String viewImpact(Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        // Get aggregated situation summaries
        List<SituationService.DonorSituationSummary> situations =
                situationService.getUniqueSituationsFundedByDonor(donor.getId());

        // Get overall stats
        DonorDashboardService.DonorDashboardStats stats = donorDashboardService.getDashboardStats(donor.getId());

        // Get all bookings funded by this donor's donations
        List<Donation> donations = donationService.getDonationsForDonor(donor.getId());
        List<Booking> fundedBookings = new java.util.ArrayList<>();
        java.math.BigDecimal totalAmountFunded = java.math.BigDecimal.ZERO;
        for (Donation donation : donations) {
            List<Booking> bookings = bookingRepository.findByFundingDonationId(donation.getId());
            for (Booking b : bookings) {
                if (b.getBookingStatus() != Booking.BookingStatus.CANCELLED) {
                    fundedBookings.add(b);
                    if (b.getFundedAmount() != null) {
                        totalAmountFunded = totalAmountFunded.add(b.getFundedAmount());
                    }
                }
            }
        }

        model.addAttribute("donor", donor);
        model.addAttribute("situations", situations);
        model.addAttribute("stats", stats);
        model.addAttribute("fundedBookings", fundedBookings);
        model.addAttribute("totalAmountFunded", totalAmountFunded);

        return "donor/impact";
    }

    // ========================================
    // FUNDING REPORT
    // ========================================

    /**
     * View funding report - which donations funded which stays (privacy-safe)
     */
    @GetMapping("/funding-report")
    public String fundingReport(Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        List<Donation> donations = donationService.getDonationsForDonor(donor.getId());

        // Build donation -> bookings map (privacy-safe: no participant PII)
        Map<Long, List<Booking>> donationBookings = new LinkedHashMap<>();
        Map<Long, BigDecimal> donationAmountUsed = new HashMap<>();
        int totalStaysFunded = 0;
        BigDecimal totalAmountFundedAll = BigDecimal.ZERO;
        BigDecimal totalAmountUsedAll = BigDecimal.ZERO;

        for (Donation donation : donations) {
            List<Booking> bookings = bookingRepository.findByFundingDonationId(donation.getId())
                    .stream()
                    .filter(b -> b.getBookingStatus() != Booking.BookingStatus.CANCELLED)
                    .collect(java.util.stream.Collectors.toList());
            donationBookings.put(donation.getId(), bookings);

            BigDecimal used = bookingRepository.sumFundedAmountByDonationId(donation.getId());
            donationAmountUsed.put(donation.getId(), used);

            totalStaysFunded += bookings.size();
            if (donation.getNetAmount() != null) {
                totalAmountFundedAll = totalAmountFundedAll.add(donation.getNetAmount());
            }
            totalAmountUsedAll = totalAmountUsedAll.add(used);
        }

        BigDecimal totalAmountRemaining = totalAmountFundedAll.subtract(totalAmountUsedAll);
        if (totalAmountRemaining.compareTo(BigDecimal.ZERO) < 0) {
            totalAmountRemaining = BigDecimal.ZERO;
        }

        DonorDashboardService.DonorDashboardStats stats = donorDashboardService.getDashboardStats(donor.getId());

        model.addAttribute("donor", donor);
        model.addAttribute("donations", donations);
        model.addAttribute("donationBookings", donationBookings);
        model.addAttribute("donationAmountUsed", donationAmountUsed);
        model.addAttribute("totalStaysFunded", totalStaysFunded);
        model.addAttribute("totalAmountFunded", totalAmountFundedAll);
        model.addAttribute("totalAmountUsed", totalAmountUsedAll);
        model.addAttribute("totalAmountRemaining", totalAmountRemaining);
        model.addAttribute("stats", stats);

        return "donor/funding-report";
    }

    // ========================================
    // PROFILE
    // ========================================

    /**
     * View donor profile
     */
    @GetMapping("/profile")
    public String viewProfile(Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        model.addAttribute("donor", donor);
        model.addAttribute("charities", donor.getCharities());

        return "donor/profile";
    }

    // ========================================
    // CHARITIES
    // ========================================

    /**
     * View charities the donor supports
     */
    @GetMapping("/charities")
    public String listCharities(Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        // Get stats per charity
        Set<Charity> charities = donor.getCharities();

        model.addAttribute("donor", donor);
        model.addAttribute("charities", charities);

        return "donor/charities";
    }

    /**
     * View donations for a specific charity
     */
    @GetMapping("/charities/{id}")
    public String viewCharity(@PathVariable Long id, Model model, Principal principal) {
        String username = principal.getName();
        Donor donor = donorService.getDonorByUsername(username);

        // Verify donor is associated with this charity
        if (!donor.isAssociatedWithCharity(id)) {
            return "redirect:/donor/charities?error=Access+denied";
        }

        Charity charity = charityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        // Get donations for this charity
        List<Donation> donations = donationService.getDonationsForDonorAtCharity(donor.getId(), id);

        // Get stats for this charity
        DonorDashboardService.DonorDashboardStats stats =
                donorDashboardService.getDashboardStatsForCharity(donor.getId(), id);

        model.addAttribute("donor", donor);
        model.addAttribute("charity", charity);
        model.addAttribute("donations", donations);
        model.addAttribute("stats", stats);

        return "donor/charity-detail";
    }
}
