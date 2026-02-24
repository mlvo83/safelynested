package com.learning.learning.controller;

import com.learning.learning.entity.StayPartnerApplication;
import com.learning.learning.service.StayPartnerApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/**
 * Public controller for Stay Partner applications.
 * These endpoints do NOT require authentication.
 */
@Controller
@RequestMapping("/stay-partner")
public class StayPartnerController {

    private static final Logger logger = LoggerFactory.getLogger(StayPartnerController.class);

    @Autowired
    private StayPartnerApplicationService applicationService;

    /**
     * Display the application form
     */
    @GetMapping("/apply")
    public String showApplicationForm(Model model) {
        model.addAttribute("applicantTypes", StayPartnerApplication.ApplicantType.values());
        model.addAttribute("propertyTypes", StayPartnerApplication.PropertyType.values());
        model.addAttribute("contactMethods", StayPartnerApplication.PreferredContactMethod.values());
        return "public/stay-partner-apply";
    }

    /**
     * Process the application form submission
     */
    @PostMapping("/apply")
    public String submitApplication(
            @RequestParam String applicantType,
            // Individual fields
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            // Business fields
            @RequestParam(required = false) String businessName,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) String businessEmail,
            @RequestParam(required = false) String businessPhone,
            @RequestParam(required = false) String taxId,
            // Property fields
            @RequestParam String propertyType,
            @RequestParam String propertyName,
            @RequestParam(required = false) Integer numberOfBedrooms,
            @RequestParam(required = false) Integer maxGuests,
            @RequestParam(required = false) String description,
            // Address
            @RequestParam String streetAddress,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam String zipCode,
            @RequestParam(required = false, defaultValue = "USA") String country,
            // Preferences
            @RequestParam(required = false) BigDecimal nightlyRate,
            @RequestParam(required = false) String amenities,
            @RequestParam(required = false) String availabilityNotes,
            @RequestParam(required = false) Boolean petsAllowed,
            @RequestParam(required = false) String accessibilityFeatures,
            // Communication
            @RequestParam(required = false, defaultValue = "EMAIL") String preferredContactMethod,
            @RequestParam(required = false) String additionalNotes,
            RedirectAttributes redirectAttributes) {

        try {
            StayPartnerApplication application = new StayPartnerApplication();

            // Applicant type
            application.setApplicantType(StayPartnerApplication.ApplicantType.valueOf(applicantType));

            // Individual fields
            application.setFirstName(firstName);
            application.setLastName(lastName);
            application.setEmail(email);
            application.setPhone(phone);

            // Business fields
            application.setBusinessName(businessName);
            application.setContactName(contactName);
            application.setBusinessEmail(businessEmail);
            application.setBusinessPhone(businessPhone);
            application.setTaxId(taxId);

            // Property
            application.setPropertyType(StayPartnerApplication.PropertyType.valueOf(propertyType));
            application.setPropertyName(propertyName);
            application.setNumberOfBedrooms(numberOfBedrooms);
            application.setMaxGuests(maxGuests);
            application.setDescription(description);

            // Address
            application.setStreetAddress(streetAddress);
            application.setCity(city);
            application.setState(state);
            application.setZipCode(zipCode);
            application.setCountry(country);

            // Preferences
            application.setNightlyRate(nightlyRate);
            application.setAmenities(amenities);
            application.setAvailabilityNotes(availabilityNotes);
            application.setPetsAllowed(petsAllowed != null ? petsAllowed : false);
            application.setAccessibilityFeatures(accessibilityFeatures);

            // Communication
            application.setPreferredContactMethod(
                    StayPartnerApplication.PreferredContactMethod.valueOf(preferredContactMethod));
            application.setAdditionalNotes(additionalNotes);

            StayPartnerApplication saved = applicationService.submitApplication(application);
            return "redirect:/stay-partner/confirmation/" + saved.getApplicationNumber();

        } catch (RuntimeException e) {
            logger.error("Error submitting stay partner application: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/stay-partner/apply";
        }
    }

    /**
     * Show confirmation page after successful submission
     */
    @GetMapping("/confirmation/{applicationNumber}")
    public String showConfirmation(@PathVariable String applicationNumber, Model model) {
        model.addAttribute("applicationNumber", applicationNumber);
        return "public/stay-partner-confirmation";
    }

    /**
     * Show status check form
     */
    @GetMapping("/status")
    public String showStatusForm() {
        return "public/stay-partner-status";
    }

    /**
     * Process status check
     */
    @PostMapping("/status")
    public String checkStatus(
            @RequestParam String applicationNumber,
            @RequestParam String email,
            Model model) {

        StayPartnerApplication application = applicationService.lookupStatus(
                applicationNumber.trim().toUpperCase(), email.trim().toLowerCase());

        if (application != null) {
            model.addAttribute("app", application);
            model.addAttribute("found", true);
        } else {
            model.addAttribute("found", false);
            model.addAttribute("notFoundMessage",
                    "No application found with that number and email combination. Please check your details and try again.");
        }

        model.addAttribute("applicationNumber", applicationNumber);
        model.addAttribute("email", email);
        return "public/stay-partner-status";
    }
}
