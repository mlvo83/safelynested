package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.StayPartnerApplication;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.UserRepository;
import com.learning.learning.service.StayPartnerApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

/**
 * Admin controller for reviewing Stay Partner applications.
 * Only accessible by ADMIN role.
 */
@Controller
@RequestMapping("/admin/stay-partner-applications")
@PreAuthorize("hasRole('ADMIN')")
public class StayPartnerAdminController {

    private static final Logger logger = LoggerFactory.getLogger(StayPartnerAdminController.class);

    @Autowired
    private StayPartnerApplicationService applicationService;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * List all applications with optional status filter
     */
    @GetMapping
    public String listApplications(
            @RequestParam(required = false) String status,
            Model model) {

        List<StayPartnerApplication> applications;
        if (status != null && !status.isEmpty()) {
            try {
                StayPartnerApplication.ApplicationStatus appStatus =
                        StayPartnerApplication.ApplicationStatus.valueOf(status.toUpperCase());
                applications = applicationService.getApplicationsByStatus(appStatus);
            } catch (IllegalArgumentException e) {
                applications = applicationService.getAllApplications();
            }
        } else {
            applications = applicationService.getAllApplications();
        }

        long pendingCount = applicationService.countPendingApplications();

        model.addAttribute("applications", applications);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("totalApplications", applications.size());
        model.addAttribute("currentStatus", status);

        return "admin/stay-partner-applications";
    }

    /**
     * View application detail
     */
    @GetMapping("/{id}")
    public String viewApplication(@PathVariable Long id, Model model) {
        StayPartnerApplication application = applicationService.getApplicationById(id);
        List<Charity> charities = charityRepository.findAllActiveOrderByName();

        model.addAttribute("app", application);
        model.addAttribute("charities", charities);
        return "admin/stay-partner-application-detail";
    }

    /**
     * Approve application with charity assignment
     */
    @PostMapping("/{id}/approve")
    public String approveApplication(
            @PathVariable Long id,
            @RequestParam Long charityId,
            @RequestParam(required = false) String adminNotes,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            Charity charity = charityRepository.findById(charityId)
                    .orElseThrow(() -> new RuntimeException("Charity not found"));

            StayPartnerApplication application = applicationService.approveApplicationWithCharity(
                    id, adminUser, adminNotes, charity);
            redirectAttributes.addFlashAttribute("success",
                    "Application " + application.getApplicationNumber() + " approved successfully! Location created under " + charity.getCharityName() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/stay-partner-applications/" + id;
    }

    /**
     * Reject application with reason
     */
    @PostMapping("/{id}/reject")
    public String rejectApplication(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            StayPartnerApplication application = applicationService.rejectApplication(
                    id, adminUser, rejectionReason);
            redirectAttributes.addFlashAttribute("success",
                    "Application " + application.getApplicationNumber() + " rejected.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/stay-partner-applications/" + id;
    }
}
