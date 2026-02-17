package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donation;
import com.learning.learning.entity.Donor;
import com.learning.learning.entity.DonorSetupRequest;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.UserRepository;
import com.learning.learning.service.DonationService;
import com.learning.learning.service.DonorDashboardService;
import com.learning.learning.service.DonorService;
import com.learning.learning.service.DonorSetupRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

/**
 * Controller for Admin management of Donors and Donations.
 * Only accessible by ADMIN role.
 */
@Controller
@RequestMapping("/admin/donors")
@PreAuthorize("hasRole('ADMIN')")
public class DonorController {

    @Autowired
    private DonorService donorService;

    @Autowired
    private DonationService donationService;

    @Autowired
    private DonorDashboardService donorDashboardService;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private DonorSetupRequestService donorSetupRequestService;

    @Autowired
    private UserRepository userRepository;

    // ========================================
    // DONOR LIST
    // ========================================

    @GetMapping
    public String listDonors(Model model) {
        List<Donor> donors = donorService.getAllDonors();
        model.addAttribute("donors", donors);
        return "admin/donors";
    }

    @GetMapping("/search")
    public String searchDonors(@RequestParam String q, Model model) {
        List<Donor> donors = donorService.searchDonors(q);
        model.addAttribute("donors", donors);
        model.addAttribute("searchTerm", q);
        return "admin/donors";
    }

    // ========================================
    // DONOR CREATE
    // ========================================

    @GetMapping("/new")
    public String showCreateDonorForm(Model model) {
        model.addAttribute("donor", new Donor());
        model.addAttribute("charities", charityRepository.findByIsActiveTrue());
        model.addAttribute("isEdit", false);
        return "admin/donor-form";
    }

    @PostMapping("/new")
    public String createDonor(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String donorType,
            // Individual fields
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            // Business fields
            @RequestParam(required = false) String businessName,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) String emailBusiness,
            @RequestParam(required = false) String phoneBusiness,
            // Charity assignment
            @RequestParam(required = false) List<Long> charityIds,
            RedirectAttributes redirectAttributes) {

        try {
            Donor donor;
            Donor.DonorType type = Donor.DonorType.valueOf(donorType);

            if (type == Donor.DonorType.BUSINESS) {
                // Create business donor - use business email/phone
                donor = donorService.createBusinessDonor(
                        username, password, emailBusiness, phoneBusiness,
                        businessName, contactName, taxId);
            } else {
                // Create individual donor
                donor = donorService.createDonor(username, password, email, firstName, lastName, phone);
            }

            // Assign to selected charities
            if (charityIds != null && !charityIds.isEmpty()) {
                for (Long charityId : charityIds) {
                    donorService.assignCharity(donor.getId(), charityId);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Donor created successfully!");
            return "redirect:/admin/donors/" + donor.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/donors/new";
        }
    }

    // ========================================
    // DONOR DETAIL VIEW
    // ========================================

    @GetMapping("/{id}")
    public String viewDonor(@PathVariable Long id, Model model) {
        Donor donor = donorService.getDonorById(id);
        List<Donation> donations = donationService.getDonationsForDonor(id);
        DonorDashboardService.DonorDashboardStats stats = donorDashboardService.getDashboardStats(id);

        model.addAttribute("donor", donor);
        model.addAttribute("donations", donations);
        model.addAttribute("stats", stats);
        model.addAttribute("charities", charityRepository.findByIsActiveTrue());

        return "admin/donor-detail";
    }

    // ========================================
    // DONOR EDIT
    // ========================================

    @GetMapping("/{id}/edit")
    public String showEditDonorForm(@PathVariable Long id, Model model) {
        Donor donor = donorService.getDonorById(id);
        model.addAttribute("donor", donor);
        model.addAttribute("charities", charityRepository.findByIsActiveTrue());
        model.addAttribute("isEdit", true);
        return "admin/donor-form";
    }

    @PostMapping("/{id}/edit")
    public String updateDonor(
            @PathVariable Long id,
            @RequestParam String donorType,
            // Individual fields
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            // Business fields
            @RequestParam(required = false) String businessName,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String taxId,
            @RequestParam(required = false) String emailBusiness,
            @RequestParam(required = false) String phoneBusiness,
            // Other
            @RequestParam(required = false) String verificationNotes,
            @RequestParam(required = false) List<Long> charityIds,
            RedirectAttributes redirectAttributes) {

        try {
            Donor.DonorType type = Donor.DonorType.valueOf(donorType);

            if (type == Donor.DonorType.BUSINESS) {
                donorService.updateBusinessDonor(id, emailBusiness, phoneBusiness,
                        businessName, contactName, taxId, verificationNotes);
            } else {
                donorService.updateDonor(id, firstName, lastName, email, phone, verificationNotes);
            }

            // Update charity assignments
            donorService.updateCharityAssignments(id, charityIds);

            redirectAttributes.addFlashAttribute("success", "Donor updated successfully!");
            return "redirect:/admin/donors/" + id;
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/donors/" + id + "/edit";
        }
    }

    // ========================================
    // DONOR VERIFICATION
    // ========================================

    @PostMapping("/{id}/verify")
    public String verifyDonor(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            donorService.verifyDonor(id, notes);
            redirectAttributes.addFlashAttribute("success", "Donor verified successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    @PostMapping("/{id}/unverify")
    public String unverifyDonor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            donorService.unverifyDonor(id);
            redirectAttributes.addFlashAttribute("success", "Donor verification removed.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    // ========================================
    // DONOR ACTIVATION
    // ========================================

    @PostMapping("/{id}/activate")
    public String activateDonor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            donorService.activateDonor(id);
            redirectAttributes.addFlashAttribute("success", "Donor account activated.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateDonor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            donorService.deactivateDonor(id);
            redirectAttributes.addFlashAttribute("success", "Donor account deactivated.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    // ========================================
    // CHARITY ASSIGNMENT
    // ========================================

    @PostMapping("/{id}/assign-charity")
    public String assignCharity(
            @PathVariable Long id,
            @RequestParam Long charityId,
            RedirectAttributes redirectAttributes) {

        try {
            donorService.assignCharity(id, charityId);
            redirectAttributes.addFlashAttribute("success", "Charity assigned to donor.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    @PostMapping("/{id}/remove-charity")
    public String removeCharity(
            @PathVariable Long id,
            @RequestParam Long charityId,
            RedirectAttributes redirectAttributes) {

        try {
            donorService.removeCharity(id, charityId);
            redirectAttributes.addFlashAttribute("success", "Charity removed from donor.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/donors/" + id;
    }

    // ========================================
    // DONATION RECORDING
    // ========================================

    @GetMapping("/{id}/record-donation")
    public String showRecordDonationForm(@PathVariable Long id, Model model) {
        Donor donor = donorService.getDonorById(id);
        model.addAttribute("donor", donor);
        model.addAttribute("charities", donor.getCharities());
        return "admin/donation-form";
    }

    @PostMapping("/{id}/record-donation")
    public String recordDonation(
            @PathVariable Long id,
            @RequestParam Long charityId,
            @RequestParam BigDecimal grossAmount,
            @RequestParam(required = false) String notes,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            // Calculate fee breakdown for display
            DonationService.DonationBreakdown breakdown = donationService.calculateFees(grossAmount);

            // Record the donation
            Donation donation = donationService.recordDonation(
                    id, charityId, grossAmount, notes, principal.getName());

            redirectAttributes.addFlashAttribute("success",
                    String.format("Donation recorded: $%.2f gross, $%.2f net, %d nights funded",
                            donation.getGrossAmount(),
                            donation.getNetAmount(),
                            donation.getNightsFunded()));

            return "redirect:/admin/donors/" + id;
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/donors/" + id + "/record-donation";
        }
    }

    // ========================================
    // DONATION VERIFICATION
    // ========================================

    @PostMapping("/donations/{donationId}/verify")
    public String verifyDonation(
            @PathVariable Long donationId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            Donation donation = donationService.verifyDonation(donationId, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Donation verified successfully!");
            return "redirect:/admin/donors/" + donation.getDonor().getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/donors";
        }
    }

    @PostMapping("/donations/{donationId}/reject")
    public String rejectDonation(
            @PathVariable Long donationId,
            @RequestParam String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            Donation donation = donationService.rejectDonation(donationId, reason, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Donation rejected.");
            return "redirect:/admin/donors/" + donation.getDonor().getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/donors";
        }
    }

    // ========================================
    // FEE CALCULATOR (AJAX)
    // ========================================

    @GetMapping("/calculate-fees")
    @ResponseBody
    public DonationService.DonationBreakdown calculateFees(@RequestParam BigDecimal amount) {
        return donationService.calculateFees(amount);
    }

    @GetMapping("/calculate-nights")
    @ResponseBody
    public int calculateNights(
            @RequestParam BigDecimal netAmount,
            @RequestParam Long charityId) {
        return donationService.calculateNightsFunded(netAmount, charityId);
    }

    // ========================================
    // DONOR SETUP REQUESTS (Admin Review)
    // ========================================

    @GetMapping("/setup-requests")
    public String listSetupRequests(
            @RequestParam(required = false) String status,
            Model model) {

        List<DonorSetupRequest> requests;
        if (status != null && !status.isEmpty()) {
            try {
                DonorSetupRequest.RequestStatus requestStatus =
                        DonorSetupRequest.RequestStatus.valueOf(status.toUpperCase());
                requests = donorSetupRequestService.getRequestsByStatus(requestStatus);
            } catch (IllegalArgumentException e) {
                requests = donorSetupRequestService.getAllRequests();
            }
        } else {
            requests = donorSetupRequestService.getAllRequests();
        }

        long pendingCount = donorSetupRequestService.countPendingRequests();

        model.addAttribute("requests", requests);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("totalRequests", requests.size());
        model.addAttribute("currentStatus", status);

        return "admin/donor-setup-requests";
    }

    @GetMapping("/setup-requests/{id}")
    public String viewSetupRequest(@PathVariable Long id, Model model) {
        DonorSetupRequest request = donorSetupRequestService.getRequestById(id);
        model.addAttribute("request", request);
        return "admin/donor-setup-request-detail";
    }

    @PostMapping("/setup-requests/{id}/approve")
    public String approveSetupRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String adminNotes,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            DonorSetupRequest request = donorSetupRequestService.approveRequest(id, adminUser, adminNotes);
            redirectAttributes.addFlashAttribute("success",
                    "Request " + request.getRequestNumber() + " approved successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/donors/setup-requests/" + id;
    }

    @PostMapping("/setup-requests/{id}/reject")
    public String rejectSetupRequest(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            DonorSetupRequest request = donorSetupRequestService.rejectRequest(id, adminUser, rejectionReason);
            redirectAttributes.addFlashAttribute("success",
                    "Request " + request.getRequestNumber() + " rejected.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/donors/setup-requests/" + id;
    }
}
