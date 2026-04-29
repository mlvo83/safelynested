package com.learning.learning.controller;

import com.learning.learning.entity.CharityApplication;
import com.learning.learning.entity.RegistrationToken;
import com.learning.learning.service.CharityApplicationService;
import com.learning.learning.service.RegistrationTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/charity-application")
public class CharityApplicationController {

    private static final Logger logger = LoggerFactory.getLogger(CharityApplicationController.class);

    @Autowired
    private CharityApplicationService applicationService;

    @Autowired
    private RegistrationTokenService registrationTokenService;

    @GetMapping("/apply")
    public String showApplicationForm(Model model) {
        return "public/charity-application-apply";
    }

    @PostMapping("/apply")
    public String submitApplication(
            @RequestParam String charityName,
            @RequestParam(required = false) String organizationType,
            @RequestParam(required = false) String einTaxId,
            @RequestParam String contactName,
            @RequestParam String contactEmail,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false, defaultValue = "USA") String country,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String missionStatement,
            @RequestParam(required = false) String website,
            @RequestParam(required = false) Integer estimatedReferralsPerMonth,
            @RequestParam(required = false) Boolean agreeToTerms,
            RedirectAttributes redirectAttributes) {

        try {
            // Server-side enforcement of the Terms checkbox
            if (!Boolean.TRUE.equals(agreeToTerms)) {
                throw new RuntimeException("You must agree to the Terms and Conditions to submit an application.");
            }

            CharityApplication application = new CharityApplication();
            application.setCharityName(charityName);
            application.setOrganizationType(organizationType);
            application.setEinTaxId(einTaxId);
            application.setContactName(contactName);
            application.setContactEmail(contactEmail.trim().toLowerCase());
            application.setContactPhone(contactPhone);
            application.setAddress(address);
            application.setCity(city);
            application.setState(state);
            application.setZipCode(zipCode);
            application.setCountry(country);
            application.setDescription(description);
            application.setMissionStatement(missionStatement);
            application.setWebsite(website);
            application.setEstimatedReferralsPerMonth(estimatedReferralsPerMonth);

            CharityApplication saved = applicationService.submitApplication(application);
            return "redirect:/charity-application/confirmation/" + saved.getApplicationNumber();

        } catch (RuntimeException e) {
            logger.error("Error submitting charity application: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/charity-application/apply";
        }
    }

    @GetMapping("/confirmation/{applicationNumber}")
    public String showConfirmation(@PathVariable String applicationNumber, Model model) {
        model.addAttribute("applicationNumber", applicationNumber);
        return "public/charity-application-confirmation";
    }

    @GetMapping("/status")
    public String showStatusForm() {
        return "public/charity-application-status";
    }

    @PostMapping("/status")
    public String checkStatus(
            @RequestParam String applicationNumber,
            @RequestParam String email,
            Model model) {

        CharityApplication application = applicationService.lookupStatus(
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
        return "public/charity-application-status";
    }

    @GetMapping("/register/{token}")
    public String showRegistrationForm(@PathVariable String token, Model model) {
        try {
            RegistrationToken regToken = registrationTokenService.validateToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", regToken.getEmail());
            model.addAttribute("charityName", regToken.getCharity().getCharityName());
            model.addAttribute("tokenType", regToken.getTokenType().name());
            return "public/charity-application-register";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "public/charity-application-register";
        }
    }

    @PostMapping("/register/{token}")
    public String processRegistration(
            @PathVariable String token,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Boolean agreeToTerms,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Server-side enforcement of the Terms checkbox
            if (!Boolean.TRUE.equals(agreeToTerms)) {
                throw new RuntimeException("You must agree to the Terms and Conditions to create an account.");
            }
            // Validate passwords match
            if (!password.equals(confirmPassword)) {
                throw new RuntimeException("Passwords do not match.");
            }

            registrationTokenService.registerUserFromToken(token, username, password, firstName, lastName, phone);

            redirectAttributes.addFlashAttribute("success",
                    "Your account has been created successfully! You can now log in.");
            return "redirect:/login";

        } catch (RuntimeException e) {
            logger.error("Registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            // Repopulate form data
            model.addAttribute("token", token);
            model.addAttribute("username", username);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("phone", phone);
            try {
                RegistrationToken regToken = registrationTokenService.validateToken(token);
                model.addAttribute("email", regToken.getEmail());
                model.addAttribute("charityName", regToken.getCharity().getCharityName());
                model.addAttribute("tokenType", regToken.getTokenType().name());
            } catch (RuntimeException ex) {
                model.addAttribute("error", ex.getMessage());
            }
            return "public/charity-application-register";
        }
    }
}
