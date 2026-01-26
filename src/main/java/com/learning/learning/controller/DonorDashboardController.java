package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donation;
import com.learning.learning.entity.Donor;
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

import java.security.Principal;
import java.util.List;
import java.util.Set;

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

        model.addAttribute("donor", donor);
        model.addAttribute("donations", donations);
        model.addAttribute("stats", stats);
        model.addAttribute("charities", donor.getCharities());
        model.addAttribute("selectedCharityId", charityId);
        model.addAttribute("selectedCharity", selectedCharity);

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

        model.addAttribute("donor", donor);
        model.addAttribute("donation", donation);
        model.addAttribute("situations", situations);

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

        model.addAttribute("donor", donor);
        model.addAttribute("situations", situations);
        model.addAttribute("stats", stats);

        return "donor/impact";
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
