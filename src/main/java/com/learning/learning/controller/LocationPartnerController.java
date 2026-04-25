package com.learning.learning.controller;

import com.learning.learning.entity.LocationAvailability;
import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.StayPartnerApplication;
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

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Dashboard, properties, and availability management for logged-in Location Partners.
 * All endpoints are scoped to the logged-in user — a partner cannot see another
 * partner's data even by guessing IDs.
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

    // ============================================================
    // DASHBOARD
    // ============================================================

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        List<PartnerLocation> locations = locationPartnerService.getLocationsForPartner(partner);

        long activeCount = locations.stream().filter(pl -> Boolean.TRUE.equals(pl.getIsActive())).count();

        model.addAttribute("partner", partner);
        model.addAttribute("locations", locations);
        model.addAttribute("activeCount", activeCount);
        return "location-partner/dashboard";
    }

    // ============================================================
    // PROPERTIES
    // ============================================================

    @GetMapping("/properties")
    public String properties(Principal principal, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        model.addAttribute("partner", partner);
        model.addAttribute("locations", locationPartnerService.getLocationsForPartner(partner));
        return "location-partner/properties";
    }

    @GetMapping("/properties/new")
    public String newPropertyForm(Principal principal, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        model.addAttribute("partner", partner);
        model.addAttribute("propertyTypes", StayPartnerApplication.PropertyType.values());
        model.addAttribute("editing", false);
        return "location-partner/property-form";
    }

    @PostMapping("/properties")
    public String createProperty(
            Principal principal,
            @RequestParam String name,
            @RequestParam(required = false) String propertyType,
            @RequestParam String address,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam String zipCode,
            @RequestParam(required = false, defaultValue = "USA") String country,
            @RequestParam(required = false) Integer numberOfBedrooms,
            @RequestParam(required = false) Integer maxGuests,
            @RequestParam(required = false) BigDecimal nightlyRate,
            @RequestParam(required = false) String amenities,
            @RequestParam(required = false) String accessibilityFeatures,
            @RequestParam(required = false) Boolean petsAllowed,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {

        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            StayPartnerApplication.PropertyType type = parsePropertyType(propertyType);
            PartnerLocation pl = locationPartnerService.createPropertyForPartner(
                    partner, name, type, address, city, state, zipCode, country,
                    numberOfBedrooms, maxGuests, nightlyRate, amenities,
                    accessibilityFeatures, petsAllowed, description);
            redirectAttributes.addFlashAttribute("success",
                    "Property '" + pl.getName() + "' added. Contact admin to link it to charities so it can be booked.");
            return "redirect:/location-partner/properties";
        } catch (RuntimeException e) {
            logger.warn("Failed to create property: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/location-partner/properties/new";
        }
    }

    @GetMapping("/properties/{id}/edit")
    public String editPropertyForm(Principal principal, @PathVariable Long id, Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        PartnerLocation pl = locationPartnerService.getLocationForPartner(partner, id);
        model.addAttribute("partner", partner);
        model.addAttribute("propertyTypes", StayPartnerApplication.PropertyType.values());
        model.addAttribute("location", pl);
        model.addAttribute("editing", true);
        return "location-partner/property-form";
    }

    @PostMapping("/properties/{id}")
    public String updateProperty(
            Principal principal,
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) Integer numberOfBedrooms,
            @RequestParam(required = false) Integer maxGuests,
            @RequestParam(required = false) BigDecimal nightlyRate,
            @RequestParam(required = false) String amenities,
            @RequestParam(required = false) String accessibilityFeatures,
            @RequestParam(required = false) Boolean petsAllowed,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {

        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            StayPartnerApplication.PropertyType type = parsePropertyType(propertyType);
            locationPartnerService.updatePropertyForPartner(
                    partner, id, name, type, numberOfBedrooms, maxGuests, nightlyRate,
                    amenities, accessibilityFeatures, petsAllowed, description);
            redirectAttributes.addFlashAttribute("success", "Property updated.");
        } catch (RuntimeException e) {
            logger.warn("Failed to update property: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/location-partner/properties";
    }

    @PostMapping("/properties/{id}/deactivate")
    public String deactivateProperty(Principal principal, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            locationPartnerService.setActive(partner, id, false);
            redirectAttributes.addFlashAttribute("success", "Property deactivated. Charities will no longer see it.");
        } catch (RuntimeException e) {
            logger.warn("Failed to deactivate property: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/location-partner/properties";
    }

    @PostMapping("/properties/{id}/activate")
    public String activateProperty(Principal principal, @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
            locationPartnerService.setActive(partner, id, true);
            redirectAttributes.addFlashAttribute("success", "Property reactivated.");
        } catch (RuntimeException e) {
            logger.warn("Failed to activate property: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/location-partner/properties";
    }

    // ============================================================
    // AVAILABILITY (now scoped to a chosen property)
    // ============================================================

    @GetMapping("/availability")
    public String availability(Principal principal,
                                @RequestParam(required = false) Long propertyId,
                                Model model) {
        LocationPartner partner = locationPartnerService.getPartnerForUser(principal.getName());
        List<PartnerLocation> locations = locationPartnerService.getLocationsForPartner(partner);

        PartnerLocation selected = null;
        if (propertyId != null) {
            selected = locationPartnerService.getLocationForPartner(partner, propertyId);
        } else {
            // Default to first active property if any, otherwise first overall
            selected = locations.stream()
                    .filter(pl -> Boolean.TRUE.equals(pl.getIsActive()))
                    .findFirst()
                    .orElse(locations.isEmpty() ? null : locations.get(0));
        }

        List<LocationAvailability> windows = selected == null
                ? List.of()
                : availabilityService.getAvailabilityForLocation(selected.getId());

        model.addAttribute("partner", partner);
        model.addAttribute("locations", locations);
        model.addAttribute("selectedLocation", selected);
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
        return "redirect:/location-partner/availability?propertyId=" + locationId;
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
        return "redirect:/location-partner/availability?propertyId=" + locationId;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private StayPartnerApplication.PropertyType parsePropertyType(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return StayPartnerApplication.PropertyType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
