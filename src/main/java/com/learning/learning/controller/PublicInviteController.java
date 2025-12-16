package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.ReferralInvite;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.ReferralInviteRepository;
import com.learning.learning.service.ReferralService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Public controller for handling invite acceptance and location selection.
 * These endpoints do NOT require authentication - they are accessed by participants
 * clicking on invite links.
 */
@Controller
@RequestMapping("/referral/invite")
public class PublicInviteController {

    private static final Logger logger = LoggerFactory.getLogger(PublicInviteController.class);

    @Autowired
    private ReferralInviteRepository inviteRepository;

    @Autowired
    private CharityLocationRepository locationRepository;

    @Autowired
    private ReferralService referralService;

    /**
     * Display the invite acceptance page with available locations
     * URL: /invite/{token}
     */
    @GetMapping("/{token}")
    public String viewInvite(@PathVariable String token, Model model) {
        logger.info("Invite accessed with token: {}", token);

        // Find the invite by token
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            logger.warn("Invalid invite token: {}", token);
            model.addAttribute("error", "Invalid or expired invite link.");
            return "public/invite-error";
        }

        ReferralInvite invite = inviteOpt.get();

        // Get charity (either directly from invite or via referral)
        Charity charity = invite.getCharity();
        if (charity == null && invite.getReferral() != null) {
            charity = invite.getReferral().getCharity();
        }

        if (charity == null) {
            logger.error("Invite {} has no associated charity", invite.getId());
            model.addAttribute("error", "This invite is not properly configured. Please contact support.");
            return "public/invite-error";
        }

        // Check if invite is expired
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Expired invite token: {}", token);
            model.addAttribute("error", "This invite has expired. Please contact the charity for a new invite.");
            model.addAttribute("charityName", charity.getCharityName());
            return "public/invite-error";
        }

        // Check if invite is cancelled
        if (invite.getStatus() == ReferralInvite.InviteStatus.CANCELLED) {
            model.addAttribute("error", "This invite has been cancelled.");
            return "public/invite-error";
        }

        // Check if already completed
        if (invite.getStatus() == ReferralInvite.InviteStatus.COMPLETED) {
            model.addAttribute("invite", invite);
            model.addAttribute("charity", charity);
            model.addAttribute("alreadyCompleted", true);
            return "public/invite-completed";
        }

        // Update status to OPENED if not already
        if (invite.getStatus() == ReferralInvite.InviteStatus.SENT ||
                invite.getStatus() == ReferralInvite.InviteStatus.PENDING) {
            invite.setStatus(ReferralInvite.InviteStatus.OPENED);
            invite.setOpenedAt(LocalDateTime.now());
            inviteRepository.save(invite);
            logger.info("Invite {} marked as opened", invite.getId());
        }

        // Get available locations for this charity
        Long charityId = charity.getId();
        List<CharityLocation> availableLocations = locationRepository
                .findByCharityIdAndIsActiveTrue(charityId);

        // Sort by location name
        availableLocations.sort((a, b) -> {
            if (a.getLocationName() == null) return 1;
            if (b.getLocationName() == null) return -1;
            return a.getLocationName().compareToIgnoreCase(b.getLocationName());
        });

        model.addAttribute("invite", invite);
        model.addAttribute("locations", availableLocations);
        model.addAttribute("charity", charity);
        model.addAttribute("referral", invite.getReferral());  // May be null

        return "public/invite-select-location";
    }

    /**
     * Handle location selection by participant
     * URL: POST /invite/{token}/select-location
     */
    @PostMapping("/{token}/select-location")
    public String selectLocation(
            @PathVariable String token,
            @RequestParam Long locationId,
            @RequestParam(required = false) String participantNotes,
            RedirectAttributes redirectAttributes
    ) {
        logger.info("Location selection for token: {}, locationId: {}", token, locationId);

        // Find the invite
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid invite link.");
            return "redirect:/referral/invite/" + token;
        }

        ReferralInvite invite = inviteOpt.get();

        // Validate invite status
        if (invite.getStatus() == ReferralInvite.InviteStatus.CANCELLED ||
                invite.getStatus() == ReferralInvite.InviteStatus.EXPIRED) {
            redirectAttributes.addFlashAttribute("error", "This invite is no longer valid.");
            return "redirect:/referral/invite/" + token;
        }

        // Find and validate the selected location
        Optional<CharityLocation> locationOpt = locationRepository.findById(locationId);

        if (locationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Selected location not found.");
            return "redirect:/referral/invite/" + token;
        }

        CharityLocation location = locationOpt.get();

        // Get the charity for this invite
        Charity inviteCharity = invite.getCharity();
        if (inviteCharity == null && invite.getReferral() != null) {
            inviteCharity = invite.getReferral().getCharity();
        }

        // Verify location belongs to the same charity
        if (inviteCharity == null || !location.getCharity().getId().equals(inviteCharity.getId())) {
            logger.warn("Location {} does not belong to invite's charity", locationId);
            redirectAttributes.addFlashAttribute("error", "Invalid location selection.");
            return "redirect:/referral/invite/" + token;
        }

        // Verify location is active
        if (!location.getIsActive()) {
            redirectAttributes.addFlashAttribute("error", "This location is no longer available.");
            return "redirect:/referral/invite/" + token;
        }

        // Update the invite with selected location
        invite.setSelectedLocation(location);
        invite.setLocationSelectedAt(LocalDateTime.now());
        invite.setParticipantNotes(participantNotes);
        invite.setStatus(ReferralInvite.InviteStatus.COMPLETED);
        invite.setCompletedAt(LocalDateTime.now());

        // Create or update referral based on whether invite was linked to one
        Referral referral;
        if (invite.getReferral() != null) {
            // Invite was linked to an existing referral - update it with location selection
            referral = referralService.updateReferralWithLocationSelection(
                    invite.getReferral(),
                    location,
                    participantNotes
            );
            logger.info("Updated existing referral {} with location selection from invite {}",
                    referral.getReferralNumber(), invite.getId());
        } else {
            // Invite was NOT linked to a referral - create a new one
            referral = referralService.createReferralFromInvite(invite, location);
            // Link the new referral to the invite
            invite.setReferral(referral);
            logger.info("Created new referral {} from invite {}",
                    referral.getReferralNumber(), invite.getId());
        }

        inviteRepository.save(invite);

        logger.info("Invite {} completed with location {} selected, referral {} submitted to facilitator",
                invite.getId(), location.getLocationName(), referral.getReferralNumber());

        return "redirect:/referral/invite/" + token + "/confirmation";
    }

    /**
     * Display confirmation page after location selection
     * URL: /invite/{token}/confirmation
     */
    @GetMapping("/{token}/confirmation")
    public String showConfirmation(@PathVariable String token, Model model) {
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            model.addAttribute("error", "Invalid invite link.");
            return "public/invite-error";
        }

        ReferralInvite invite = inviteOpt.get();

        if (!invite.hasSelectedLocation()) {
            return "redirect:/referral/invite/" + token;
        }

        // Get charity (either directly or via referral)
        Charity charity = invite.getCharity();
        if (charity == null && invite.getReferral() != null) {
            charity = invite.getReferral().getCharity();
        }

        model.addAttribute("invite", invite);
        model.addAttribute("location", invite.getSelectedLocation());
        model.addAttribute("charity", charity);
        model.addAttribute("referral", invite.getReferral());

        return "public/invite-confirmation";
    }

    /**
     * Allow participant to change their location selection (if still allowed)
     * URL: /invite/{token}/change
     */
    @GetMapping("/{token}/change")
    public String changeLocation(@PathVariable String token, Model model) {
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            model.addAttribute("error", "Invalid invite link.");
            return "public/invite-error";
        }

        ReferralInvite invite = inviteOpt.get();

        // Get charity name for error messages
        Charity charity = invite.getCharity();
        if (charity == null && invite.getReferral() != null) {
            charity = invite.getReferral().getCharity();
        }
        String charityName = charity != null ? charity.getCharityName() : "the organization";

        // Check if change is allowed (e.g., within 24 hours of selection)
        if (invite.getLocationSelectedAt() != null) {
            LocalDateTime changeDeadline = invite.getLocationSelectedAt().plusHours(24);
            if (LocalDateTime.now().isAfter(changeDeadline)) {
                model.addAttribute("error", "The deadline to change your location selection has passed. Please contact the charity directly.");
                model.addAttribute("charityName", charityName);
                return "public/invite-error";
            }
        }

        // Reset status to allow re-selection
        invite.setStatus(ReferralInvite.InviteStatus.OPENED);
        inviteRepository.save(invite);

        return "redirect:/referral/invite/" + token;
    }
}
