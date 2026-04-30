package com.learning.learning.controller;

import com.learning.learning.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    @Autowired
    private StripeService stripeService;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            stripeService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok("OK");
        } catch (SignatureVerificationException e) {
            logger.error("Invalid Stripe webhook signature", e);
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Error processing Stripe webhook", e);
            return ResponseEntity.status(500).body("Webhook processing failed");
        }
    }
}
