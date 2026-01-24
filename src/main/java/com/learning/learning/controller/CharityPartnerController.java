package com.learning.learning.controller;

import com.learning.learning.entity.*;
import com.learning.learning.repository.*;
import com.learning.learning.service.CharityService;
import com.learning.learning.service.DocumentService;
import com.learning.learning.service.DonationService;
import com.learning.learning.service.DonorDashboardService;
import com.learning.learning.service.DonorService;
import com.learning.learning.service.InviteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/charity-partner")
public class CharityPartnerController {

    @Autowired
    private CharityService charityService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private DonorService donorService;

    @Autowired
    private DonationService donationService;

    @Autowired
    private DonorDashboardService donorDashboardService;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private CharityLocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    // ========================================
    // DASHBOARD
    // ========================================

    /**
     * Charity partner dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Get dashboard statistics
        Map<String, Object> stats = charityService.getCharityDashboardStats(charityId);
        model.addAttribute("stats", stats);

        // Get recent referrals
        List<Referral> recentReferrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        model.addAttribute("recentReferrals", recentReferrals.stream().limit(10).toList());

        // Get charity info
        model.addAttribute("charity", charity);
        model.addAttribute("username", username);
        model.addAttribute("role", "Charity Partner");

        // Get remaining referrals this month
        int remainingReferrals = charityService.getRemainingReferralsThisMonth(charityId);
        model.addAttribute("remainingReferrals", remainingReferrals);

        // Get invite stats
        Map<String, Object> inviteStats = inviteService.getInviteStats(username);
        model.addAttribute("inviteStats", inviteStats);

        return "charity-partner/dashboard";
    }

    // ========================================
    // REFERRALS - LIST AND VIEW
    // ========================================

    /**
     * List all referrals for charity
     */
    @GetMapping("/referrals")
    public String listReferrals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);  // Get full charity object
        Long charityId = charity.getId();

        List<Referral> referrals;

        if (search != null && !search.isEmpty()) {
            referrals = referralRepository.searchReferralsByCharity(charityId, search);
        } else if (status != null && !status.isEmpty()) {
            try {
                Referral.ReferralStatus referralStatus = Referral.ReferralStatus.valueOf(status.toUpperCase());
                referrals = referralRepository.findByCharityIdAndStatusOrderByCreatedAtDesc(charityId, referralStatus);
            } catch (IllegalArgumentException e) {
                referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
            }
        } else {
            referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        }

        model.addAttribute("charity", charity);  // ADD THIS LINE - needed for sidebar
        model.addAttribute("referrals", referrals);
        model.addAttribute("statuses", Referral.ReferralStatus.values());
        model.addAttribute("currentStatus", status);
        model.addAttribute("searchTerm", search);

        return "charity-partner/referrals";
    }

    /**
     * View single referral (read-only after submission)
     */
    @GetMapping("/referrals/{id}")
    public String viewReferral(@PathVariable Long id, Model model, Principal principal) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));

        model.addAttribute("referral", referral);

        // Get documents for this referral
        List<Document> documents = documentService.getDocumentsForReferral(id, username);
        model.addAttribute("documents", documents);

        // Get invites for this referral
        List<ReferralInvite> invites = inviteService.getInvitesForReferral(id, username);
        model.addAttribute("invites", invites);

        return "charity-partner/referral-view";
    }

    // ========================================
    // REFERRALS - CREATE AND EDIT
    // ========================================

    /**
     * Show create referral form
     */
    @GetMapping("/referrals/new")
    public String showCreateReferralForm(Model model, Principal principal) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        // Check if charity can create more referrals
        if (!charityService.canCreateReferral(charityId)) {
            model.addAttribute("error", "Monthly referral limit reached");
            return "redirect:/charity-partner/referrals";
        }

        model.addAttribute("referral", new Referral());
        model.addAttribute("urgencyLevels", Referral.UrgencyLevel.values());

        return "charity-partner/referral-form";
    }

    /**
     * Create new referral
     */
    @PostMapping("/referrals/new")
    public String createReferral(
            @ModelAttribute Referral referral,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if charity can create more referrals
        if (!charityService.canCreateReferral(charity.getId())) {
            redirectAttributes.addFlashAttribute("error", "Monthly referral limit reached");
            return "redirect:/charity-partner/referrals";
        }

        // Set charity and user
        referral.setCharity(charity);
        referral.setReferredByUser(user);
        referral.setStatus(Referral.ReferralStatus.PENDING);

        // Generate referral number
        referral.setReferralNumber(generateReferralNumber());

        referralRepository.save(referral);

        redirectAttributes.addFlashAttribute("success", "Referral created successfully");
        return "redirect:/charity-partner/referrals/" + referral.getId();
    }

    /**
     * Show edit referral form (only for PENDING status)
     */
    @GetMapping("/referrals/{id}/edit")
    public String showEditReferralForm(@PathVariable Long id, Model model, Principal principal) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));

        if (!referral.isEditable()) {
            model.addAttribute("error", "Referral cannot be edited after submission");
            return "redirect:/charity-partner/referrals/" + id;
        }

        model.addAttribute("referral", referral);
        model.addAttribute("urgencyLevels", Referral.UrgencyLevel.values());

        return "charity-partner/referral-form";
    }

    /**
     * Update referral (only for PENDING status)
     */
    @PostMapping("/referrals/{id}/edit")
    public String updateReferral(
            @PathVariable Long id,
            @ModelAttribute Referral updatedReferral,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        Referral existingReferral = referralRepository.findByIdAndCharityId(id, charityId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));

        if (!existingReferral.isEditable()) {
            redirectAttributes.addFlashAttribute("error", "Referral cannot be edited after submission");
            return "redirect:/charity-partner/referrals/" + id;
        }

        // Update fields
        existingReferral.setParticipantName(updatedReferral.getParticipantName());
        existingReferral.setParticipantAge(updatedReferral.getParticipantAge());
        existingReferral.setParticipantEmail(updatedReferral.getParticipantEmail());
        existingReferral.setParticipantPhone(updatedReferral.getParticipantPhone());
        existingReferral.setNeedsDescription(updatedReferral.getNeedsDescription());
        existingReferral.setUrgencyLevel(updatedReferral.getUrgencyLevel());
        existingReferral.setDocumentsRequired(updatedReferral.getDocumentsRequired());
        existingReferral.setAllowedZipCodes(updatedReferral.getAllowedZipCodes());

        referralRepository.save(existingReferral);

        redirectAttributes.addFlashAttribute("success", "Referral updated successfully");
        return "redirect:/charity-partner/referrals/" + id;
    }

    /**
     * Cancel referral
     */
    @PostMapping("/referrals/{id}/cancel")
    public String cancelReferral(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        Referral referral = referralRepository.findByIdAndCharityId(id, charityId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));

        if (referral.getStatus() == Referral.ReferralStatus.COMPLETED) {
            redirectAttributes.addFlashAttribute("error", "Cannot cancel completed referral");
            return "redirect:/charity-partner/referrals/" + id;
        }

        referral.setStatus(Referral.ReferralStatus.CANCELLED);
        referralRepository.save(referral);

        redirectAttributes.addFlashAttribute("success", "Referral cancelled successfully");
        return "redirect:/charity-partner/referrals";
    }

    // ========================================
    // DOCUMENTS
    // ========================================

    /**
     * List all documents for charity
     */
    @GetMapping("/documents")
    public String listDocuments(Model model, Principal principal) {
        String username = principal.getName();

        List<Document> documents = documentService.getDocumentsForCharity(username);
        model.addAttribute("documents", documents);
        model.addAttribute("documentTypes", Document.DocumentType.values());

        return "charity-partner/documents";
    }

    /**
     * Show document upload form
     */
    @GetMapping("/documents/upload")
    public String showUploadForm(
            @RequestParam(required = false) Long referralId,
            @RequestParam(required = false) Long inviteId,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        // Get referrals for dropdown
        List<Referral> referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
        model.addAttribute("referrals", referrals);

        // Get invites for dropdown
        List<ReferralInvite> invites = inviteService.getInvitesForCharity(username);
        model.addAttribute("invites", invites);

        model.addAttribute("documentTypes", Document.DocumentType.values());
        model.addAttribute("selectedReferralId", referralId);
        model.addAttribute("selectedInviteId", inviteId);

        return "charity-partner/document-upload";
    }

    /**
     * Upload document
     */
    @PostMapping("/documents/upload")
    public String uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long referralId,
            @RequestParam(required = false) Long inviteId,
            @RequestParam Document.DocumentType documentType,
            @RequestParam(required = false) String description,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();

        try {
            Document document;

            // If inviteId is provided, upload to invite
            if (inviteId != null) {
                document = documentService.uploadDocumentForInvite(file, inviteId, documentType, description, username);
                redirectAttributes.addFlashAttribute("success", "Document uploaded to invite successfully");
                return "redirect:/charity-partner/invites/" + inviteId;
            }

            // Otherwise, upload to referral or general
            document = documentService.uploadDocument(file, referralId, documentType, description, username);
            redirectAttributes.addFlashAttribute("success", "Document uploaded successfully");

            if (referralId != null) {
                return "redirect:/charity-partner/referrals/" + referralId;
            }
            return "redirect:/charity-partner/documents";

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload document: " + e.getMessage());
            return "redirect:/charity-partner/documents/upload";
        }
    }

    /**
     * Download document
     */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, Principal principal) {
        String username = principal.getName();

        try {
            Document document = documentService.getDocumentWithAccessCheck(id, username);
            Resource resource = documentService.downloadDocument(id, username);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new RuntimeException("Error downloading file", e);
        }
    }

    /**
     * Delete document
     */
    @PostMapping("/documents/{id}/delete")
    public String deleteDocument(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();

        try {
            documentService.deleteDocument(id, username);
            redirectAttributes.addFlashAttribute("success", "Document deleted successfully");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }

        return "redirect:/charity-partner/documents";
    }

    // ========================================
    // INVITES
    // ========================================

    /**
     * List all invites for charity
     */
    /**
     * List all invites for charity
     */
    @GetMapping("/invites")
    public String listInvites(
            @RequestParam(required = false) String status,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);  // GET CHARITY

        List<ReferralInvite> invites;

        if (status != null && !status.isEmpty()) {
            try {
                ReferralInvite.InviteStatus inviteStatus = ReferralInvite.InviteStatus.valueOf(status.toUpperCase());
                invites = switch (inviteStatus) {
                    case PENDING -> inviteService.getPendingInvites(username);
                    case SENT -> inviteService.getSentInvites(username);
                    case COMPLETED -> inviteService.getCompletedInvites(username);
                    case EXPIRED -> inviteService.getExpiredInvites(username);
                    default -> inviteService.getInvitesForCharity(username);
                };
            } catch (IllegalArgumentException e) {
                invites = inviteService.getInvitesForCharity(username);
            }
        } else {
            invites = inviteService.getInvitesForCharity(username);
        }

        model.addAttribute("charity", charity);  // ADD THIS - needed for sidebar
        model.addAttribute("invites", invites);
        model.addAttribute("statuses", ReferralInvite.InviteStatus.values());
        model.addAttribute("currentStatus", status);

        // Get invite stats
        Map<String, Object> inviteStats = inviteService.getInviteStats(username);
        model.addAttribute("inviteStats", inviteStats);

        return "charity-partner/invites";
    }

    /**
     * Show send invite form
     * Referral is now OPTIONAL - charity can send invite directly to a participant
     */
    @GetMapping("/invites/send")
    public String showSendInviteForm(
            @RequestParam(required = false) Long referralId,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Get ALL referrals (not just approved) - referral linking is optional
        List<Referral> referrals = referralRepository.findByCharityIdOrderByCreatedAtDesc(charityId);

        // Get charity's locations for display
        List<CharityLocation> locations = locationRepository.findByCharityIdAndIsActiveTrue(charityId);

        model.addAttribute("charity", charity);
        model.addAttribute("referrals", referrals);
        model.addAttribute("locations", locations);
        model.addAttribute("inviteTypes", ReferralInvite.InviteType.values());
        model.addAttribute("selectedReferralId", referralId);

        // Pre-fill recipient info if referral selected
        if (referralId != null) {
            referralRepository.findByIdAndCharityId(referralId, charityId)
                    .ifPresent(referral -> {
                        model.addAttribute("recipientName", referral.getParticipantName());
                        model.addAttribute("recipientEmail", referral.getParticipantEmail());
                        model.addAttribute("recipientPhone", referral.getParticipantPhone());
                    });
        }

        return "charity-partner/invite-form";
    }

    /**
     * Send invite
     * Referral is now OPTIONAL - can send invite without linking to a referral
     */
    /**
     * Send invite
     * Referral is now OPTIONAL - can send invite without linking to a referral
     */
    @PostMapping("/invites/send")
    public String sendInvite(
            @RequestParam(required = false) Long referralId,  // NOW OPTIONAL
            @RequestParam String recipientName,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String recipientPhone,
            @RequestParam ReferralInvite.InviteType inviteType,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String allowedZipCodes,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);

        // Get the user for createdBy field
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate at least one contact method
        if ((recipientEmail == null || recipientEmail.trim().isEmpty()) &&
                (recipientPhone == null || recipientPhone.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide either an email or phone number");
            return "redirect:/charity-partner/invites/send";
        }

        try {
            // Create invite - referral can be null
            ReferralInvite invite = new ReferralInvite();
            invite.setRecipientName(recipientName);
            invite.setRecipientEmail(recipientEmail);
            invite.setRecipientPhone(recipientPhone);
            invite.setInviteType(inviteType);
            invite.setMessage(message);
            invite.setAllowedZipCodes(allowedZipCodes);
            invite.setStatus(ReferralInvite.InviteStatus.PENDING);
            invite.setInviteToken(UUID.randomUUID().toString());
            invite.setExpiresAt(LocalDateTime.now().plusDays(7)); // 7 day expiry
            invite.setCreatedAt(LocalDateTime.now());

            // SET THE CREATED BY USER - THIS WAS MISSING!
            invite.setCreatedBy(user);

            // Link to referral if provided
            if (referralId != null) {
                Referral referral = referralRepository.findByIdAndCharityId(referralId, charity.getId())
                        .orElse(null);
                invite.setReferral(referral);
            }

            // Set the charity directly on invite (for invites without referral)
            invite.setCharity(charity);

            // Save and send
            inviteService.saveAndSendInvite(invite, username);

            redirectAttributes.addFlashAttribute("success",
                    "Invite sent successfully to " + recipientName);

            if (referralId != null) {
                return "redirect:/charity-partner/referrals/" + referralId;
            }
            return "redirect:/charity-partner/invites";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to send invite: " + e.getMessage());
            return "redirect:/charity-partner/invites/send" +
                    (referralId != null ? "?referralId=" + referralId : "");
        }
    }

    /**
     * Resend invite
     */
    @PostMapping("/invites/{id}/resend")
    public String resendInvite(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();

        try {
            inviteService.resendInvite(id, username);
            redirectAttributes.addFlashAttribute("success", "Invite resent successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to resend invite: " + e.getMessage());
        }

        return "redirect:/charity-partner/invites";
    }

    /**
     * Cancel invite
     */
    @PostMapping("/invites/{id}/cancel")
    public String cancelInvite(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();

        try {
            inviteService.cancelInvite(id, username);
            redirectAttributes.addFlashAttribute("success", "Invite cancelled successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to cancel invite: " + e.getMessage());
        }

        return "redirect:/charity-partner/invites";
    }

    // ========================================
    // LOCATIONS
    // ========================================

    /**
     * List all locations for charity
     */
    /**
     * List all locations for charity
     */
    @GetMapping("/locations")
    public String listLocations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        List<CharityLocation> locations;

        // Filter by status
        if ("active".equals(status)) {
            locations = locationRepository.findByCharityIdAndIsActiveTrue(charityId);
        } else if ("inactive".equals(status)) {
            locations = locationRepository.findByCharityIdAndIsActiveFalse(charityId);
        } else {
            locations = locationRepository.findByCharityIdOrderByLocationNameAsc(charityId);
        }

        // Apply search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.toLowerCase().trim();
            locations = locations.stream()
                    .filter(l ->
                            (l.getLocationName() != null && l.getLocationName().toLowerCase().contains(searchLower)) ||
                                    (l.getCity() != null && l.getCity().toLowerCase().contains(searchLower)) ||
                                    (l.getState() != null && l.getState().toLowerCase().contains(searchLower)) ||
                                    (l.getZipCode() != null && l.getZipCode().contains(search.trim()))
                    )
                    .toList();
        }

        // Statistics
        long totalLocations = locationRepository.countByCharityId(charityId);
        long activeLocations = locationRepository.countByCharityIdAndIsActive(charityId, true);

        model.addAttribute("charity", charity);
        model.addAttribute("locations", locations);
        model.addAttribute("totalLocations", totalLocations);
        model.addAttribute("activeLocations", activeLocations);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("search", search);

        return "charity-partner/locations";
    }

    /**
     * Show create location form
     */
    /**
     * Show create location form
     */
    @GetMapping("/locations/new")
    public String showCreateLocationForm(Model model, Principal principal) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);

        model.addAttribute("location", new CharityLocation());
        model.addAttribute("charity", charity);
        model.addAttribute("isEdit", false);

        return "charity-partner/location-form";
    }

    /**
     * Create new location
     */
    /**
     * Create new location
     */
    @PostMapping("/locations/new")
    public String createLocation(
            @RequestParam String locationName,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String servicesOffered,
            @RequestParam(required = false) String hoursOfOperation,
            @RequestParam(required = false) Boolean isActive,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);

        // Check for duplicate name
        if (locationRepository.existsByCharityIdAndLocationName(charity.getId(), locationName)) {
            redirectAttributes.addFlashAttribute("error", "Location name already exists");
            return "redirect:/charity-partner/locations/new";
        }

        CharityLocation location = new CharityLocation();
        location.setCharity(charity);
        location.setLocationName(locationName);
        location.setAddress(address);
        location.setCity(city);
        location.setState(state);
        location.setZipCode(zipCode);
        location.setCountry(country != null ? country : "USA");
        location.setPhone(phone);
        location.setEmail(email);
        location.setCapacity(capacity);
        location.setDescription(description);
        location.setServicesOffered(servicesOffered);
        location.setHoursOfOperation(hoursOfOperation);
        location.setIsActive(isActive != null ? isActive : true);

        locationRepository.save(location);

        redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' created successfully!");
        return "redirect:/charity-partner/locations";
    }

    /**
     * Show edit location form
     */
    /**
     * Show edit location form
     */
    @GetMapping("/locations/{id}/edit")
    public String showEditLocationForm(@PathVariable Long id, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        CharityLocation location = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElse(null);

        if (location == null) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/charity-partner/locations";
        }

        model.addAttribute("location", location);
        model.addAttribute("charity", charity);
        model.addAttribute("isEdit", true);

        return "charity-partner/location-form";
    }
    /**
     * Update location
     */
    @PostMapping("/locations/{id}/edit")
    public String updateLocation(
            @PathVariable Long id,
            @RequestParam String locationName,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String servicesOffered,
            @RequestParam(required = false) String hoursOfOperation,
            @RequestParam(required = false) Boolean isActive,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        CharityLocation existingLocation = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElse(null);

        if (existingLocation == null) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/charity-partner/locations";
        }

        // Check for duplicate name (excluding current location)
        if (!existingLocation.getLocationName().equals(locationName)) {
            if (locationRepository.existsByCharityIdAndLocationName(charityId, locationName)) {
                redirectAttributes.addFlashAttribute("error", "Location name already exists");
                return "redirect:/charity-partner/locations/" + id + "/edit";
            }
        }

        // Update fields
        existingLocation.setLocationName(locationName);
        existingLocation.setAddress(address);
        existingLocation.setCity(city);
        existingLocation.setState(state);
        existingLocation.setZipCode(zipCode);
        existingLocation.setCountry(country != null ? country : "USA");
        existingLocation.setPhone(phone);
        existingLocation.setEmail(email);
        existingLocation.setCapacity(capacity);
        existingLocation.setDescription(description);
        existingLocation.setServicesOffered(servicesOffered);
        existingLocation.setHoursOfOperation(hoursOfOperation);
        existingLocation.setIsActive(isActive != null ? isActive : true);

        locationRepository.save(existingLocation);

        redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' updated successfully!");
        return "redirect:/charity-partner/locations";
    }

    /**
     * Toggle location active status
     */
    @PostMapping("/locations/{id}/toggle-active")
    public String toggleLocationActive(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        CharityLocation location = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElseThrow(() -> new RuntimeException("Location not found"));

        location.setIsActive(!location.getIsActive());
        locationRepository.save(location);

        String status = location.getIsActive() ? "activated" : "deactivated";
        redirectAttributes.addFlashAttribute("success", "Location " + status + " successfully");
        return "redirect:/charity-partner/locations";
    }

    /**
     * Delete location
     */


    // ========================================
    // DONORS (READ-ONLY VIEW)
    // ========================================

    /**
     * List donors associated with this charity (read-only)
     */
    @GetMapping("/donors")
    public String listDonors(
            @RequestParam(required = false) String search,
            Model model,
            Principal principal
    ) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        List<Donor> donors;
        if (search != null && !search.trim().isEmpty()) {
            donors = donorService.searchDonorsForCharity(charityId, search);
        } else {
            donors = donorService.getDonorsForCharity(charityId);
        }

        // Get stats
        long donorCount = donorService.countDonorsForCharity(charityId);

        model.addAttribute("charity", charity);
        model.addAttribute("donors", donors);
        model.addAttribute("donorCount", donorCount);
        model.addAttribute("searchTerm", search);

        return "charity-partner/donors";
    }

    /**
     * View single donor detail (read-only)
     */
    @GetMapping("/donors/{id}")
    public String viewDonor(@PathVariable Long id, Model model, Principal principal) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        // Verify donor is associated with this charity
        Donor donor = donorService.getDonorById(id);
        if (!donor.isAssociatedWithCharity(charityId)) {
            return "redirect:/charity-partner/donors?error=Access+denied";
        }

        // Get donations for this donor at this charity only
        List<Donation> donations = donationService.getDonationsForDonorAtCharity(id, charityId);

        // Get stats for this charity only
        DonorDashboardService.DonorDashboardStats stats = donorDashboardService.getDashboardStatsForCharity(id, charityId);

        model.addAttribute("charity", charity);
        model.addAttribute("donor", donor);
        model.addAttribute("donations", donations);
        model.addAttribute("stats", stats);

        return "charity-partner/donor-view";
    }

    // ========================================
    // CHARITY PROFILE
    // ========================================

    /**
     * View charity profile
     */
    @GetMapping("/profile")
    public String viewProfile(Model model, Principal principal) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);

        model.addAttribute("charity", charity);
        return "charity-partner/profile";
    }

    /**
     * Show edit profile form
     */
    @GetMapping("/profile/edit")
    public String showEditProfileForm(Model model, Principal principal) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);

        model.addAttribute("charity", charity);
        return "charity-partner/profile-edit";
    }

    /**
     * Update charity profile
     */
    @PostMapping("/profile/edit")
    public String updateProfile(
            @ModelAttribute Charity updatedCharity,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Charity existingCharity = charityService.getCharityForUser(username);

        try {
            charityService.updateCharity(existingCharity.getId(), updatedCharity, username);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update profile: " + e.getMessage());
        }

        return "redirect:/charity-partner/profile";
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Generate unique referral number
     */
    private String generateReferralNumber() {
        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }


    /**
     * Toggle location active status
     */
    @PostMapping("/locations/{id}/toggle-status")
    public String toggleLocationStatus(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        CharityLocation location = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElse(null);

        if (location == null) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/charity-partner/locations";
        }

        boolean newStatus = !(location.getIsActive() != null && location.getIsActive());
        location.setIsActive(newStatus);
        locationRepository.save(location);

        String statusText = newStatus ? "activated" : "deactivated";
        redirectAttributes.addFlashAttribute("success", "Location '" + location.getLocationName() + "' " + statusText);
        return "redirect:/charity-partner/locations";
    }

    /**
     * Delete location
     */
    @PostMapping("/locations/{id}/delete")
    public String deleteLocation(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        String username = principal.getName();
        Long charityId = charityService.getCharityIdForUser(username);

        CharityLocation location = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElse(null);

        if (location == null) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/charity-partner/locations";
        }

        String locationName = location.getLocationName();
        locationRepository.delete(location);

        redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' deleted");
        return "redirect:/charity-partner/locations";
    }
    /**
     * View single location
     */
    @GetMapping("/locations/{id}")
    public String viewLocation(@PathVariable Long id, Model model, Principal principal, RedirectAttributes redirectAttributes) {
        String username = principal.getName();
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        CharityLocation location = locationRepository.findById(id)
                .filter(loc -> loc.getCharity().getId().equals(charityId))
                .orElse(null);

        if (location == null) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/charity-partner/locations";
        }

        model.addAttribute("location", location);
        model.addAttribute("charity", charity);

        return "charity-partner/location-view";
    }
}