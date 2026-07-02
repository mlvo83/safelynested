package com.learning.learning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies Cloudflare Turnstile CAPTCHA tokens for the public charity
 * application form.
 *
 * The CAPTCHA is only enforced when both the site key and secret key are
 * configured (app.turnstile.site-key / app.turnstile.secret-key). When they
 * are blank the form still works and verification is skipped, so local
 * development and pre-key deployments are unaffected.
 */
@Service
public class TurnstileVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(TurnstileVerificationService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Value("${app.turnstile.site-key:}")
    private String siteKey;

    @Value("${app.turnstile.secret-key:}")
    private String secretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /** True only when both keys are configured — otherwise the CAPTCHA is off. */
    public boolean isEnabled() {
        return siteKey != null && !siteKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /** Public site key, injected into the form widget. */
    public String getSiteKey() {
        return siteKey == null ? "" : siteKey;
    }

    /**
     * Verifies a Turnstile token with Cloudflare.
     *
     * @return true if the challenge passed (or if the CAPTCHA is not configured).
     *         When Cloudflare cannot be reached we fail open (return true) so a
     *         Cloudflare outage never blocks legitimate applicants — the honeypot,
     *         time-trap, rate limiter and content validation remain as backstops.
     */
    public boolean verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return true; // CAPTCHA not configured — don't block submissions
        }
        if (token == null || token.isBlank()) {
            logger.warn("Turnstile token missing on submission from IP {}", remoteIp);
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secretKey);
            form.add("response", token);
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(VERIFY_URL, request, Map.class);

            Object success = response.getBody() != null ? response.getBody().get("success") : null;
            boolean passed = Boolean.TRUE.equals(success);
            if (!passed) {
                logger.warn("Turnstile verification failed for IP {}: {}", remoteIp,
                        response.getBody() != null ? response.getBody().get("error-codes") : "no response body");
            }
            return passed;
        } catch (Exception e) {
            // Fail open on network/Cloudflare errors so real users aren't locked out.
            logger.error("Turnstile verification error (allowing submission through): {}", e.getMessage());
            return true;
        }
    }
}
