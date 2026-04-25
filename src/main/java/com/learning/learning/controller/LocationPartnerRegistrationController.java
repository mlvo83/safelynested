package com.learning.learning.controller;

import com.learning.learning.entity.RegistrationToken;
import com.learning.learning.service.RegistrationTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Public controller for Location Partner account registration.
 * Redeems a LOCATION_PARTNER registration token (7-day expiry, single-use)
 * and creates the partner's user account.
 */
@Controller
@RequestMapping("/location-partner/register")
public class LocationPartnerRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(LocationPartnerRegistrationController.class);

    @Autowired
    private RegistrationTokenService registrationTokenService;

    @GetMapping("/{token}")
    public String showRegistrationForm(@PathVariable String token, Model model) {
        try {
            RegistrationToken regToken = registrationTokenService.validateToken(token);
            if (regToken.getTokenType() != RegistrationToken.TokenType.LOCATION_PARTNER) {
                model.addAttribute("error", "This registration link is not valid for Location Partner signup.");
                return "public/location-partner-register";
            }
            model.addAttribute("token", token);
            model.addAttribute("email", regToken.getEmail());
            if (regToken.getLocationPartner() != null) {
                model.addAttribute("partnerName", regToken.getLocationPartner().getDisplayName());
            }
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "public/location-partner-register";
    }

    @PostMapping("/{token}")
    public String processRegistration(
            @PathVariable String token,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            if (!password.equals(confirmPassword)) {
                throw new RuntimeException("Passwords do not match.");
            }

            registrationTokenService.registerUserFromToken(token, username, password, firstName, lastName, phone);

            redirectAttributes.addFlashAttribute("success",
                    "Your Location Partner account has been created successfully. You can now log in.");
            return "redirect:/login";

        } catch (RuntimeException e) {
            logger.error("Location Partner registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("token", token);
            model.addAttribute("username", username);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("phone", phone);
            try {
                RegistrationToken regToken = registrationTokenService.validateToken(token);
                model.addAttribute("email", regToken.getEmail());
                if (regToken.getLocationPartner() != null) {
                    model.addAttribute("partnerName", regToken.getLocationPartner().getDisplayName());
                }
            } catch (RuntimeException ignored) {
                // token no longer valid — leave fields as-is
            }
            return "public/location-partner-register";
        }
    }
}
