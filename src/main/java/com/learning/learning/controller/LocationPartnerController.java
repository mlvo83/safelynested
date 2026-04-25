package com.learning.learning.controller;

import com.learning.learning.entity.LocationAvailability;
import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.service.LocationAvailabilityService;
import com.learning.learning.service.LocationPartnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Dashboard and availability management for logged-in Location Partners.
 * All endpoints are scoped to the logged-in user.
 */
@Controller
@RequestMapping("/location-partner")
@PreAuthorize("hasRole('LOCATION_PARTNER')")
public class LocationPartnerController {

    private static final Logger logger = LoggerFactory.getLogger(LocationPartnerController.class);

    @Autowired
    private LocationPartnerService locationPartnerService;

    @Autowired
    private LocationAvailabilityService availabilityService;

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        List<PartnerLocation> locations = locationPartnerService.getLocationsForPartner(partner);

        PartnerLocation primary = locations.isEmpty() ? null : locations.get(0);
        List<LocationAvailability> upcoming = primary == null
                ? List.of()
                : availabilityService.getAvailabilityForLocation(primary.getId());

        model.addAttribute("partner", partner);
        model.addAttribute("locations", locations);
        model.addAttribute("primaryLocation", primary);
        model.addAttribute("upcomingWindows", upcoming);
        return "location-partner/dashboard";
    }

    @GetMapping("/availability")
    public String availability(Principal principal, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        List<PartnerLocation> locations = locationPartnerService.getLocationsForPartner(partner);

        PartnerLocation primary = locations.isEmpty() ? null : locations.get(0);
        List<LocationAvailability> windows = primary == null
                ? List.of()
                : availabilityService.getAvailabilityForLocation(primary.getId());

        model.addAttribute("partner", partner);
        model.addAttribute("primaryLocation", primary);
        model.addAttribute("windows", windows);
        return "location-partner/availability";
    }

    @PostMapping("/availability")
    public String addAvailability(
            Principal principal,
            @RequestParam Long locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            PartnerLocation location = locationPartnerService.getLocationForPartner(partner, locationId);
            availabilityService.addWindow(location, startDate, endDate, startTime, endTime, notes);
            redirectAttributes.addFlashAttribute("success", "Availability added.");
        } catch (RuntimeException e) {
            logger.warn("Failed to add availability: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/location-partner/availability";
    }

    @PostMapping("/availability/{windowId}/delete")
    public String deleteAvailability(
            Principal principal,
            @PathVariable Long windowId,
            @RequestParam Long locationId,
            RedirectAttributes redirectAttributes) {

        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            PartnerLocation location = locationPartnerService.getLocationForPartner(partner, locationId);
            availabilityService.deleteWindow(location, windowId);
            redirectAttributes.addFlashAttribute("success", "Availability removed.");
        } catch (RuntimeException e) {
            logger.warn("Failed to delete availability: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/location-partner/availability";
    }
}
