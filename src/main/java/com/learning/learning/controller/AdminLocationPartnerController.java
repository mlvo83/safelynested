package com.learning.learning.controller;

import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.PartnerLocationCharity;
import com.learning.learning.entity.User;
import com.learning.learning.repository.LocationPartnerRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import com.learning.learning.repository.UserRepository;
import com.learning.learning.service.PartnerLocationAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin pages for managing Location Partners and the
 * many-to-many link between their properties and charities.
 */
@Controller
@RequestMapping("/admin/location-partners")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLocationPartnerController {

    private static final Logger logger = LoggerFactory.getLogger(AdminLocationPartnerController.class);

    @Autowired
    private LocationPartnerRepository locationPartnerRepository;

    @Autowired
    private PartnerLocationRepository partnerLocationRepository;

    @Autowired
    private PartnerLocationAssignmentService assignmentService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String list(Model model) {
        List<LocationPartner> partners = locationPartnerRepository.findAllByOrderByCreatedAtDesc();

        // For each partner, gather property + total charity-link count for the list view
        Map<Long, Integer> linkCounts = new HashMap<>();
        Map<Long, List<PartnerLocation>> locationsByPartner = new HashMap<>();
        for (LocationPartner partner : partners) {
            List<PartnerLocation> locations = partnerLocationRepository.findByLocationPartnerId(partner.getId());
            locationsByPartner.put(partner.getId(), locations);
            int total = 0;
            for (PartnerLocation loc : locations) {
                total += (int) assignmentService.countLinksForLocation(loc.getId());
            }
            linkCounts.put(partner.getId(), total);
        }

        model.addAttribute("partners", partners);
        model.addAttribute("locationsByPartner", locationsByPartner);
        model.addAttribute("linkCounts", linkCounts);
        return "admin/location-partners";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        LocationPartner partner = locationPartnerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location Partner not found: " + id));
        List<PartnerLocation> locations = partnerLocationRepository.findByLocationPartnerId(id);

        // Per location: existing links + dropdown of eligible (not-yet-linked) charities
        Map<Long, List<PartnerLocationCharity>> linksByLocation = new HashMap<>();
        Map<Long, List<com.learning.learning.entity.Charity>> eligibleByLocation = new HashMap<>();
        for (PartnerLocation loc : locations) {
            linksByLocation.put(loc.getId(), assignmentService.getLinksForLocation(loc.getId()));
            eligibleByLocation.put(loc.getId(), assignmentService.getEligibleCharities(loc.getId()));
        }

        model.addAttribute("partner", partner);
        model.addAttribute("locations", locations);
        model.addAttribute("linksByLocation", linksByLocation);
        model.addAttribute("eligibleByLocation", eligibleByLocation);
        return "admin/location-partner-detail";
    }

    @PostMapping("/{id}/locations/{locationId}/charities")
    public String addCharityLink(
            @PathVariable Long id,
            @PathVariable Long locationId,
            @RequestParam Long charityId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            // Verify the location belongs to this partner before any mutation
            PartnerLocation location = partnerLocationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Partner location not found."));
            if (location.getLocationPartner() == null
                    || !location.getLocationPartner().getId().equals(id)) {
                throw new RuntimeException("That location does not belong to this partner.");
            }

            User adminUser = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));

            assignmentService.linkCharity(locationId, charityId, adminUser);
            redirectAttributes.addFlashAttribute("success", "Charity linked to this property.");
        } catch (RuntimeException e) {
            logger.warn("Failed to link charity: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/location-partners/" + id;
    }

    @PostMapping("/{id}/locations/{locationId}/charities/{charityId}/remove")
    public String removeCharityLink(
            @PathVariable Long id,
            @PathVariable Long locationId,
            @PathVariable Long charityId,
            RedirectAttributes redirectAttributes) {

        try {
            PartnerLocation location = partnerLocationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Partner location not found."));
            if (location.getLocationPartner() == null
                    || !location.getLocationPartner().getId().equals(id)) {
                throw new RuntimeException("That location does not belong to this partner.");
            }

            assignmentService.unlinkCharity(locationId, charityId);
            redirectAttributes.addFlashAttribute("success", "Charity unlinked from this property.");
        } catch (RuntimeException e) {
            logger.warn("Failed to unlink charity: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/location-partners/" + id;
    }
}
