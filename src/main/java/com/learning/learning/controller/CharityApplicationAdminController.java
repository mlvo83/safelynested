package com.learning.learning.controller;

import com.learning.learning.entity.CharityApplication;
import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import com.learning.learning.service.CharityApplicationService;
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

@Controller
@RequestMapping("/admin/charity-applications")
@PreAuthorize("hasRole('ADMIN')")
public class CharityApplicationAdminController {

    private static final Logger logger = LoggerFactory.getLogger(CharityApplicationAdminController.class);

    @Autowired
    private CharityApplicationService applicationService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String listApplications(
            @RequestParam(required = false) String status,
            Model model) {

        List<CharityApplication> applications;
        if (status != null && !status.isEmpty()) {
            try {
                CharityApplication.ApplicationStatus appStatus =
                        CharityApplication.ApplicationStatus.valueOf(status.toUpperCase());
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

        return "admin/charity-applications";
    }

    @GetMapping("/{id}")
    public String viewApplication(@PathVariable Long id, Model model) {
        CharityApplication application = applicationService.getApplicationById(id);
        model.addAttribute("app", application);
        return "admin/charity-application-detail";
    }

    @PostMapping("/{id}/approve")
    public String approveApplication(
            @PathVariable Long id,
            @RequestParam(required = false) String adminNotes,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            CharityApplication application = applicationService.approveApplication(id, adminUser, adminNotes);
            redirectAttributes.addFlashAttribute("success",
                    "Application " + application.getApplicationNumber() + " approved! Charity '" +
                    application.getCharityName() + "' created and registration email sent to " +
                    application.getContactEmail() + ".");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/charity-applications/" + id;
    }

    @PostMapping("/{id}/reject")
    public String rejectApplication(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            CharityApplication application = applicationService.rejectApplication(id, adminUser, rejectionReason);
            redirectAttributes.addFlashAttribute("success",
                    "Application " + application.getApplicationNumber() + " rejected.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/charity-applications/" + id;
    }
}
