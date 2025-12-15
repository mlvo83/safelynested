package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.CharityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for Location Admin role
 * Location Admins can manage locations for ANY charity partner
 */
@Controller
@RequestMapping("/location-admin")
public class LocationAdminController {

    private static final Logger logger = LoggerFactory.getLogger(LocationAdminController.class);

    @Autowired
    private CharityLocationRepository locationRepository;

    @Autowired
    private CharityRepository charityRepository;

    // ========================================
    // DASHBOARD
    // ========================================

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        // Get all charities
        List<Charity> charities = charityRepository.findAll();

        // Get all locations
        List<CharityLocation> allLocations = locationRepository.findAllOrderByCharityAndName();

        // Calculate statistics
        long totalLocations = locationRepository.count();
        long activeLocations = allLocations.stream().filter(l -> l.getIsActive() != null && l.getIsActive()).count();
        long inactiveLocations = totalLocations - activeLocations;

        int totalCapacity = allLocations.stream()
                .filter(l -> l.getCapacity() != null)
                .mapToInt(CharityLocation::getCapacity)
                .sum();

        // Locations per charity stats
        Map<String, Long> locationsPerCharity = new HashMap<>();
        for (Charity charity : charities) {
            long count = locationRepository.countByCharityId(charity.getId());
            locationsPerCharity.put(charity.getCharityName(), count);
        }

        model.addAttribute("charities", charities);
        model.addAttribute("totalLocations", totalLocations);
        model.addAttribute("activeLocations", activeLocations);
        model.addAttribute("inactiveLocations", inactiveLocations);
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("locationsPerCharity", locationsPerCharity);
        model.addAttribute("recentLocations", allLocations.stream().limit(10).toList());

        return "location-admin/dashboard";
    }

    // ========================================
    // LOCATIONS LIST
    // ========================================

    @GetMapping("/locations")
    public String listLocations(
            @RequestParam(required = false) Long charityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model
    ) {
        logger.info("Listing locations - charityId: {}, status: {}, search: {}", charityId, status, search);

        List<CharityLocation> locations;

        // Filter by charity if specified
        if (charityId != null && charityId > 0) {
            logger.info("Filtering by charity ID: {}", charityId);

            if ("active".equals(status)) {
                locations = locationRepository.findByCharityIdAndIsActiveTrue(charityId);
            } else if ("inactive".equals(status)) {
                locations = locationRepository.findByCharityIdAndIsActiveFalse(charityId);
            } else {
                locations = locationRepository.findByCharityIdOrderByLocationNameAsc(charityId);
            }

            logger.info("Found {} locations for charity {}", locations.size(), charityId);
        } else {
            // All locations (no charity filter)
            logger.info("Loading all locations (no charity filter)");
            locations = locationRepository.findAllOrderByCharityAndName();

            // Apply status filter
            if ("active".equals(status)) {
                locations = locations.stream()
                        .filter(l -> l.getIsActive() != null && l.getIsActive())
                        .toList();
            } else if ("inactive".equals(status)) {
                locations = locations.stream()
                        .filter(l -> l.getIsActive() == null || !l.getIsActive())
                        .toList();
            }
        }

        // Apply search filter (works on top of charity/status filters)
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

        List<Charity> charities = charityRepository.findAll();

        model.addAttribute("locations", locations);
        model.addAttribute("charities", charities);
        model.addAttribute("selectedCharityId", charityId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("search", search);

        logger.info("Returning {} locations to view", locations.size());

        return "location-admin/locations";
    }

    // ========================================
    // ADD LOCATION
    // ========================================

    @GetMapping("/locations/new")
    public String showAddLocationForm(
            @RequestParam(required = false) Long charityId,
            Model model
    ) {
        List<Charity> charities = charityRepository.findAll();

        model.addAttribute("location", new CharityLocation());
        model.addAttribute("charities", charities);
        model.addAttribute("selectedCharityId", charityId);
        model.addAttribute("isEdit", false);

        return "location-admin/location-form";
    }

    @PostMapping("/locations/new")
    public String createLocation(
            @RequestParam Long charityId,
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
            RedirectAttributes redirectAttributes
    ) {
        try {
            Optional<Charity> charityOpt = charityRepository.findById(charityId);
            if (charityOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Charity not found");
                return "redirect:/location-admin/locations/new";
            }

            CharityLocation location = new CharityLocation();
            location.setCharity(charityOpt.get());
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
            logger.info("Location created: {} for charity: {}", locationName, charityOpt.get().getCharityName());

            redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' created successfully!");
            return "redirect:/location-admin/locations";

        } catch (Exception e) {
            logger.error("Error creating location", e);
            redirectAttributes.addFlashAttribute("error", "Error creating location: " + e.getMessage());
            return "redirect:/location-admin/locations/new?charityId=" + charityId;
        }
    }

    // ========================================
    // EDIT LOCATION
    // ========================================

    @GetMapping("/locations/{id}/edit")
    public String showEditLocationForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<CharityLocation> locationOpt = locationRepository.findById(id);

        if (locationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/location-admin/locations";
        }

        List<Charity> charities = charityRepository.findAll();

        model.addAttribute("location", locationOpt.get());
        model.addAttribute("charities", charities);
        model.addAttribute("selectedCharityId", locationOpt.get().getCharity().getId());
        model.addAttribute("isEdit", true);

        return "location-admin/location-form";
    }

    @PostMapping("/locations/{id}/edit")
    public String updateLocation(
            @PathVariable Long id,
            @RequestParam Long charityId,
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
            RedirectAttributes redirectAttributes
    ) {
        try {
            Optional<CharityLocation> locationOpt = locationRepository.findById(id);
            if (locationOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Location not found");
                return "redirect:/location-admin/locations";
            }

            Optional<Charity> charityOpt = charityRepository.findById(charityId);
            if (charityOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Charity not found");
                return "redirect:/location-admin/locations/" + id + "/edit";
            }

            CharityLocation location = locationOpt.get();
            location.setCharity(charityOpt.get());
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
            logger.info("Location updated: {}", locationName);

            redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' updated successfully!");
            return "redirect:/location-admin/locations";

        } catch (Exception e) {
            logger.error("Error updating location", e);
            redirectAttributes.addFlashAttribute("error", "Error updating location: " + e.getMessage());
            return "redirect:/location-admin/locations/" + id + "/edit";
        }
    }

    // ========================================
    // VIEW LOCATION DETAILS
    // ========================================

    @GetMapping("/locations/{id}")
    public String viewLocation(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<CharityLocation> locationOpt = locationRepository.findById(id);

        if (locationOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Location not found");
            return "redirect:/location-admin/locations";
        }

        model.addAttribute("location", locationOpt.get());
        return "location-admin/location-view";
    }

    // ========================================
    // TOGGLE LOCATION STATUS
    // ========================================

    @PostMapping("/locations/{id}/toggle-status")
    public String toggleLocationStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<CharityLocation> locationOpt = locationRepository.findById(id);
            if (locationOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Location not found");
                return "redirect:/location-admin/locations";
            }

            CharityLocation location = locationOpt.get();
            boolean newStatus = !(location.getIsActive() != null && location.getIsActive());
            location.setIsActive(newStatus);
            locationRepository.save(location);

            String statusText = newStatus ? "activated" : "deactivated";
            redirectAttributes.addFlashAttribute("success", "Location '" + location.getLocationName() + "' " + statusText);
            logger.info("Location {} status changed to: {}", location.getLocationName(), statusText);

            return "redirect:/location-admin/locations";

        } catch (Exception e) {
            logger.error("Error toggling location status", e);
            redirectAttributes.addFlashAttribute("error", "Error changing location status");
            return "redirect:/location-admin/locations";
        }
    }

    // ========================================
    // DELETE LOCATION
    // ========================================

    @PostMapping("/locations/{id}/delete")
    public String deleteLocation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<CharityLocation> locationOpt = locationRepository.findById(id);
            if (locationOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Location not found");
                return "redirect:/location-admin/locations";
            }

            String locationName = locationOpt.get().getLocationName();
            locationRepository.deleteById(id);

            redirectAttributes.addFlashAttribute("success", "Location '" + locationName + "' deleted");
            logger.info("Location deleted: {}", locationName);

            return "redirect:/location-admin/locations";

        } catch (Exception e) {
            logger.error("Error deleting location", e);
            redirectAttributes.addFlashAttribute("error", "Cannot delete location. It may have associated bookings.");
            return "redirect:/location-admin/locations";
        }
    }
}