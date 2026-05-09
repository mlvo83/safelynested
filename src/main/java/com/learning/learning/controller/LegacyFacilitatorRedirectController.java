package com.learning.learning.controller;

import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * Backward-compat redirects for the old un-scoped facilitator URLs
 * (pre multi-charity-facilitator feature). Anything hitting
 * /charity-facilitator/dashboard etc. is redirected to the new
 * /charity-facilitator/{charityId}/... URL.
 *
 * For single-charity facilitators (CHARITY_FACILITATOR), charityId is
 * derived from user.charity_id. For multi-facilitators, we route them
 * to the hub since they need to pick a charity first.
 *
 * These redirects are gated on the charity-facilitator role at the
 * Spring Security level (see SecurityConfig matcher for
 * /charity-facilitator/**). Do NOT add path variables — that would
 * conflict with the new /charity-facilitator/{charityId}/... mapping
 * in the main controller.
 */
@Controller
public class LegacyFacilitatorRedirectController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/charity-facilitator/dashboard")
    public String legacyDashboard(Principal principal) {
        return redirectFor(principal, "dashboard");
    }

    @GetMapping("/charity-facilitator/referrals")
    public String legacyReferrals(Principal principal) {
        return redirectFor(principal, "referrals");
    }

    @GetMapping("/charity-facilitator/bookings")
    public String legacyBookings(Principal principal) {
        return redirectFor(principal, "bookings");
    }

    private String redirectFor(Principal principal, String subPath) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return "redirect:/login";

        // Multi-facilitators land on the hub to pick a charity
        if (user.hasRole("ROLE_MULTI_FACILITATOR") || user.hasRole("MULTI_FACILITATOR")) {
            return "redirect:/multi-facilitator/hub";
        }

        // Single-charity facilitators get their charity ID injected
        if (user.getCharity() != null) {
            return "redirect:/charity-facilitator/" + user.getCharity().getId() + "/" + subPath;
        }

        return "redirect:/access-denied";
    }
}
