package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.Document;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.ReferralInvite;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import com.learning.learning.repository.PartnerLocationCharityRepository;
import com.learning.learning.repository.ReferralInviteRepository;
import com.learning.learning.service.DocumentService;
import com.learning.learning.service.ReferralService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
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
    private PartnerLocationRepository partnerLocationRepository;

    @Autowired
    private PartnerLocationCharityRepository partnerLocationCharityRepository;

    @Autowired
    private ReferralService referralService;

    @Autowired
    private DocumentService documentService;

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

        // Partner properties linked to this charity (offered alongside charity locations)
        List<PartnerLocation> partnerLocations =
                partnerLocationRepository.findActiveLinkedToCharity(charityId);

        model.addAttribute("invite", invite);
        model.addAttribute("locations", availableLocations);
        model.addAttribute("partnerLocations", partnerLocations);
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
            @RequestParam(required = false) String locationSelection,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String participantNotes,
            RedirectAttributes redirectAttributes
    ) {
        logger.info("Location selection for token: {}, locationSelection: '{}', locationId: {}",
                token, locationSelection, locationId);

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

        // Get the charity for this invite
        Charity inviteCharity = invite.getCharity();
        if (inviteCharity == null && invite.getReferral() != null) {
            inviteCharity = invite.getReferral().getCharity();
        }
        if (inviteCharity == null) {
            redirectAttributes.addFlashAttribute("error", "This invite is not properly configured.");
            return "redirect:/referral/invite/" + token;
        }

        // Resolve which kind of location was chosen.
        // The form posts a prefixed value via locationSelection ("charity:{id}" or
        // "partner:{id}"). Legacy clients may post locationId directly.
        String type = "charity";
        Long resolvedId = locationId;
        if (locationSelection != null && !locationSelection.isBlank()) {
            int colon = locationSelection.indexOf(':');
            if (colon < 0) {
                redirectAttributes.addFlashAttribute("error", "Invalid location selection.");
                return "redirect:/referral/invite/" + token;
            }
            type = locationSelection.substring(0, colon).trim().toLowerCase();
            try {
                resolvedId = Long.parseLong(locationSelection.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                redirectAttributes.addFlashAttribute("error", "Invalid location id.");
                return "redirect:/referral/invite/" + token;
            }
        }
        if (resolvedId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a location.");
            return "redirect:/referral/invite/" + token;
        }

        if ("partner".equals(type)) {
            return selectPartnerLocation(token, invite, inviteCharity, resolvedId, participantNotes, redirectAttributes);
        }
        return selectCharityLocation(token, invite, inviteCharity, resolvedId, participantNotes, redirectAttributes);
    }

    private String selectCharityLocation(String token, ReferralInvite invite, Charity inviteCharity,
                                          Long locationId, String participantNotes,
                                          RedirectAttributes redirectAttributes) {
        Optional<CharityLocation> locationOpt = locationRepository.findById(locationId);
        if (locationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Selected location not found.");
            return "redirect:/referral/invite/" + token;
        }
        CharityLocation location = locationOpt.get();

        if (!location.getCharity().getId().equals(inviteCharity.getId())) {
            logger.warn("Location {} does not belong to invite's charity", locationId);
            redirectAttributes.addFlashAttribute("error", "Invalid location selection.");
            return "redirect:/referral/invite/" + token;
        }
        if (!location.getIsActive()) {
            redirectAttributes.addFlashAttribute("error", "This location is no longer available.");
            return "redirect:/referral/invite/" + token;
        }

        invite.setSelectedLocation(location);
        invite.setSelectedPartnerLocation(null);
        invite.setLocationSelectedAt(LocalDateTime.now());
        invite.setParticipantNotes(participantNotes);
        invite.setStatus(ReferralInvite.InviteStatus.COMPLETED);
        invite.setCompletedAt(LocalDateTime.now());

        Referral referral;
        if (invite.getReferral() != null) {
            referral = referralService.updateReferralWithLocationSelection(
                    invite.getReferral(), location, participantNotes);
        } else {
            referral = referralService.createReferralFromInvite(invite, location);
            invite.setReferral(referral);
        }
        inviteRepository.save(invite);

        logger.info("Invite {} completed with charity location {} selected, referral {}",
                invite.getId(), location.getLocationName(), referral.getReferralNumber());
        return "redirect:/referral/invite/" + token + "/confirmation";
    }

    private String selectPartnerLocation(String token, ReferralInvite invite, Charity inviteCharity,
                                          Long partnerLocationId, String participantNotes,
                                          RedirectAttributes redirectAttributes) {
        Optional<PartnerLocation> plOpt = partnerLocationRepository.findById(partnerLocationId);
        if (plOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Selected partner location not found.");
            return "redirect:/referral/invite/" + token;
        }
        PartnerLocation pl = plOpt.get();

        if (!Boolean.TRUE.equals(pl.getIsActive())) {
            redirectAttributes.addFlashAttribute("error", "That property is not currently available.");
            return "redirect:/referral/invite/" + token;
        }
        // Verify the partner property is linked to the invite's charity
        if (!partnerLocationCharityRepository.existsByPartnerLocationIdAndCharityId(
                pl.getId(), inviteCharity.getId())) {
            logger.warn("Partner location {} not linked to invite's charity {}", pl.getId(), inviteCharity.getId());
            redirectAttributes.addFlashAttribute("error", "That property is not available for this invite.");
            return "redirect:/referral/invite/" + token;
        }

        invite.setSelectedLocation(null);
        invite.setSelectedPartnerLocation(pl);
        invite.setLocationSelectedAt(LocalDateTime.now());
        invite.setParticipantNotes(participantNotes);
        invite.setStatus(ReferralInvite.InviteStatus.COMPLETED);
        invite.setCompletedAt(LocalDateTime.now());

        Referral referral;
        if (invite.getReferral() != null) {
            referral = referralService.updateReferralWithLocationSelection(
                    invite.getReferral(), pl, participantNotes);
        } else {
            referral = referralService.createReferralFromInvite(invite, pl);
            invite.setReferral(referral);
        }
        inviteRepository.save(invite);

        logger.info("Invite {} completed with partner location {} selected, referral {}",
                invite.getId(), pl.getName(), referral.getReferralNumber());
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

        // Flatten the selected location into simple string model attributes
        // so the template doesn't have to branch on entity type. CharityLocation
        // has locationName/phone/hoursOfOperation; PartnerLocation has name and
        // no phone/hours, so those are null for partner-picks.
        if (invite.getSelectedLocation() != null) {
            CharityLocation cl = invite.getSelectedLocation();
            model.addAttribute("locationName", cl.getLocationName());
            model.addAttribute("locationAddress", cl.getAddress());
            model.addAttribute("locationCity", cl.getCity());
            model.addAttribute("locationState", cl.getState());
            model.addAttribute("locationZipCode", cl.getZipCode());
            model.addAttribute("locationPhone", cl.getPhone());
            model.addAttribute("locationHours", cl.getHoursOfOperation());
            model.addAttribute("isPartnerLocation", false);
        } else if (invite.getSelectedPartnerLocation() != null) {
            PartnerLocation pl = invite.getSelectedPartnerLocation();
            model.addAttribute("locationName", pl.getName());
            model.addAttribute("locationAddress", pl.getAddress());
            model.addAttribute("locationCity", pl.getCity());
            model.addAttribute("locationState", pl.getState());
            model.addAttribute("locationZipCode", pl.getZipCode());
            model.addAttribute("locationPhone", null);
            model.addAttribute("locationHours", null);
            model.addAttribute("isPartnerLocation", true);
        }

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

    /**
     * Show document upload form for participants
     * URL: /invite/{token}/documents
     */
    @GetMapping("/{token}/documents")
    public String showDocumentUploadForm(@PathVariable String token, Model model) {
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            model.addAttribute("error", "Invalid invite link.");
            return "public/invite-error";
        }

        ReferralInvite invite = inviteOpt.get();

        // Check if invite is valid
        if (invite.isExpired()) {
            model.addAttribute("error", "This invite has expired.");
            return "public/invite-error";
        }

        if (invite.getStatus() == ReferralInvite.InviteStatus.CANCELLED) {
            model.addAttribute("error", "This invite has been cancelled.");
            return "public/invite-error";
        }

        // Get charity
        Charity charity = invite.getCharity();
        if (charity == null && invite.getReferral() != null) {
            charity = invite.getReferral().getCharity();
        }

        // Get existing documents for this invite
        List<Document> documents = documentService.getDocumentsForInviteToken(token);

        model.addAttribute("invite", invite);
        model.addAttribute("charity", charity);
        model.addAttribute("documents", documents);
        model.addAttribute("documentTypes", Document.DocumentType.values());

        return "public/invite-document-upload";
    }

    /**
     * Handle document upload from participant
     * URL: POST /invite/{token}/documents/upload
     */
    @PostMapping("/{token}/documents/upload")
    public String uploadDocument(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam Document.DocumentType documentType,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes
    ) {
        Optional<ReferralInvite> inviteOpt = inviteRepository.findByInviteToken(token);

        if (inviteOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid invite link.");
            return "redirect:/referral/invite/" + token + "/documents";
        }

        ReferralInvite invite = inviteOpt.get();

        // Check if invite is valid
        if (invite.isExpired() || invite.getStatus() == ReferralInvite.InviteStatus.CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "This invite is no longer valid.");
            return "redirect:/referral/invite/" + token + "/documents";
        }

        try {
            Document document = documentService.uploadDocumentByParticipant(
                    file,
                    token,
                    documentType,
                    description,
                    invite.getRecipientName()
            );

            logger.info("Participant uploaded document {} for invite {}", document.getId(), invite.getId());
            redirectAttributes.addFlashAttribute("success", "Document uploaded successfully!");

        } catch (IOException e) {
            logger.error("Failed to upload document for invite {}: {}", invite.getId(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to upload document: " + e.getMessage());
        }

        return "redirect:/referral/invite/" + token + "/documents";
    }
}
