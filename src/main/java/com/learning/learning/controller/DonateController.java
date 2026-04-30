package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donation;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.DonationRepository;
import com.learning.learning.service.DonationService;
import com.learning.learning.service.StripeService;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/donate")
public class DonateController {

    private static final Logger logger = LoggerFactory.getLogger(DonateController.class);

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private DonationService donationService;

    @Autowired
    private StripeService stripeService;

    @Value("${stripe.api-key.publishable}")
    private String stripePublishableKey;

    @GetMapping
    public String showDonatePage(Model model) {
        List<Charity> charities = charityRepository.findAllActiveVerifiedOrderByName();
        model.addAttribute("charities", charities);
        model.addAttribute("stripePublishableKey", stripePublishableKey);
        return "public/donate";
    }

    @GetMapping("/{charityId}")
    public String showDonateForCharity(@PathVariable Long charityId, Model model) {
        Charity charity = charityRepository.findById(charityId).orElse(null);
        if (charity == null || !Boolean.TRUE.equals(charity.getIsActive())) {
            return "redirect:/donate";
        }
        List<Charity> charities = charityRepository.findAllActiveVerifiedOrderByName();
        model.addAttribute("selectedCharity", charity);
        model.addAttribute("charities", charities);
        model.addAttribute("stripePublishableKey", stripePublishableKey);
        return "public/donate";
    }

    @PostMapping("/create-session")
    public String createCheckoutSession(
            @RequestParam Long charityId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "false") boolean coverFees,
            @RequestParam(required = false) String donorEmail,
            @RequestParam(required = false) String donorName,
            RedirectAttributes redirectAttributes) {
        try {
            String checkoutUrl = stripeService.createCheckoutSession(
                    charityId, amount, coverFees, donorEmail, donorName);
            return "redirect:" + checkoutUrl;
        } catch (Exception e) {
            logger.error("Failed to create Stripe checkout session: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Unable to process donation: " + e.getMessage());
            return "redirect:/donate/" + charityId;
        }
    }

    @GetMapping("/success")
    public String donationSuccess(@RequestParam("session_id") String sessionId, Model model) {
        try {
            Session session = stripeService.retrieveSession(sessionId);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("customerEmail", session.getCustomerEmail());

            // Amount from Stripe is in cents
            BigDecimal amountTotal = new BigDecimal(session.getAmountTotal())
                    .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
            model.addAttribute("amountTotal", amountTotal);

            // Try to find the donation (webhook may not have fired yet)
            donationRepository.findByStripeSessionId(sessionId).ifPresent(donation -> {
                model.addAttribute("donation", donation);
                model.addAttribute("charityName", donation.getCharity().getCharityName());
            });

            // If donation not found yet, get charity name from metadata
            if (!model.containsAttribute("charityName")) {
                String charityIdStr = session.getMetadata().get("charity_id");
                if (charityIdStr != null) {
                    charityRepository.findById(Long.parseLong(charityIdStr)).ifPresent(charity ->
                            model.addAttribute("charityName", charity.getCharityName()));
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving Stripe session {}: {}", sessionId, e.getMessage());
            model.addAttribute("error", "Unable to retrieve donation details.");
        }
        return "public/donate-success";
    }

    @GetMapping("/cancel")
    public String donationCancel() {
        return "public/donate-cancel";
    }

    @GetMapping("/calculate-fees")
    @ResponseBody
    public DonationService.DonationBreakdown calculateFees(@RequestParam BigDecimal amount) {
        return donationService.calculateFees(amount);
    }
}
