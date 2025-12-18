package com.learning.learning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email service using Resend API (HTTP-based)
 * Use this for cloud deployments like Render that block SMTP ports
 */
@Service
public class    ResendEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    private final RestTemplate restTemplate;

    public ResendEmailService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if Resend is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Send an HTML email using Resend API
     */
    public boolean sendHtmlEmail(String to, String subject, String htmlBody) {
        if (!isConfigured()) {
            logger.warn("Resend API key not configured - skipping email to: {}", to);
            return false;
        }

        logger.info("Sending HTML email via Resend to: {}, from: {}", to, fromEmail);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from", fromEmail);
            body.put("to", List.of(to));
            body.put("subject", subject);
            body.put("html", htmlBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    RESEND_API_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Email sent successfully via Resend to: {}, response: {}", to, response.getBody());
                return true;
            } else {
                logger.error("Resend API returned non-success status: {}, body: {}", response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (HttpClientErrorException e) {
            logger.error("Resend API client error ({}): {} - Response: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (HttpServerErrorException e) {
            logger.error("Resend API server error ({}): {} - Response: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.error("Failed to send email via Resend to: {} - Error: {}", to, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send a plain text email using Resend API
     */
    public boolean sendPlainTextEmail(String to, String subject, String textBody) {
        if (!isConfigured()) {
            logger.warn("Resend API key not configured - skipping email to: {}", to);
            return false;
        }

        logger.info("Sending plain text email via Resend to: {}, from: {}", to, fromEmail);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from", fromEmail);
            body.put("to", List.of(to));
            body.put("subject", subject);
            body.put("text", textBody);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    RESEND_API_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Plain text email sent via Resend to: {}, response: {}", to, response.getBody());
                return true;
            } else {
                logger.error("Resend API returned non-success status: {}, body: {}", response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (HttpClientErrorException e) {
            logger.error("Resend API client error ({}): {} - Response: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (HttpServerErrorException e) {
            logger.error("Resend API server error ({}): {} - Response: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.error("Failed to send email via Resend to: {} - Error: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
