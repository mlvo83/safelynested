package com.learning.learning.service;

import com.learning.learning.entity.Referral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending email notifications asynchronously
 * Separated from ReferralService to allow @Async to work properly
 *
 * Supports two email backends:
 * - Resend API (for cloud deployments like Render that block SMTP)
 * - SMTP via JavaMailSender (for local development)
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private ResendEmailService resendEmailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@safelynested.org}")
    private String fromEmail;

    /**
     * Check if email service is configured (either Resend or SMTP)
     */
    public boolean isEmailConfigured() {
        return resendEmailService.isConfigured() || mailSender != null;
    }

    /**
     * Check if we should use Resend (preferred for cloud deployments)
     */
    private boolean useResend() {
        return resendEmailService.isConfigured();
    }

    /**
     * Send approval notification emails (async)
     */
    @Async
    public void sendApprovalNotifications(Referral referral) {
        logger.info("Starting async email notifications for referral: {}", referral.getReferralNumber());

        // Send to participant (if email exists)
        if (referral.getParticipantEmail() != null && !referral.getParticipantEmail().isEmpty()) {
            sendParticipantApprovalEmail(referral);
        }

        // Send to charity partner who created the referral
        if (referral.getReferredByUser() != null && referral.getReferredByUser().getEmail() != null) {
            sendCharityPartnerApprovalEmail(referral);
        }

        logger.info("Completed async email notifications for referral: {}", referral.getReferralNumber());
    }

    /**
     * Send rejection notification emails (async)
     */
    @Async
    public void sendRejectionNotifications(Referral referral, String reason) {
        logger.info("Starting async rejection notification for referral: {}", referral.getReferralNumber());

        // Send to charity partner who created the referral
        if (referral.getReferredByUser() != null && referral.getReferredByUser().getEmail() != null) {
            sendCharityPartnerRejectionEmail(referral, reason);
        }

        logger.info("Completed async rejection notification for referral: {}", referral.getReferralNumber());
    }

    /**
     * Send approval email to participant
     */
    public void sendParticipantApprovalEmail(Referral referral) {
        String to = referral.getParticipantEmail();
        String subject = "Good News! Your Referral Has Been Approved - SafelyNested";
        String htmlContent = buildParticipantApprovalEmailHtml(referral);

        // Try Resend first (for cloud deployments)
        if (useResend()) {
            resendEmailService.sendHtmlEmail(to, subject, htmlContent);
            return;
        }

        // Fall back to SMTP
        if (mailSender == null) {
            logger.warn("No email service configured - skipping participant approval email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Approval email sent to participant: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send approval email to participant: {}", to, e);
        } catch (Exception e) {
            logger.error("Unexpected error sending email to participant: {}", to, e);
        }
    }

    /**
     * Send approval notification to charity partner
     */
    public void sendCharityPartnerApprovalEmail(Referral referral) {
        String to = referral.getReferredByUser().getEmail();
        String subject = "Referral Approved: " + referral.getReferralNumber() + " - SafelyNested";
        String htmlContent = buildCharityPartnerApprovalEmailHtml(referral);

        // Try Resend first (for cloud deployments)
        if (useResend()) {
            resendEmailService.sendHtmlEmail(to, subject, htmlContent);
            return;
        }

        // Fall back to SMTP
        if (mailSender == null) {
            logger.warn("No email service configured - skipping charity partner approval email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Approval notification sent to charity partner: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send approval notification to charity partner: {}", to, e);
        } catch (Exception e) {
            logger.error("Unexpected error sending email to charity partner: {}", to, e);
        }
    }

    /**
     * Send rejection notification to charity partner
     */
    public void sendCharityPartnerRejectionEmail(Referral referral, String reason) {
        String to = referral.getReferredByUser().getEmail();
        String subject = "Referral Update: " + referral.getReferralNumber() + " - SafelyNested";
        String htmlContent = buildCharityPartnerRejectionEmailHtml(referral, reason);

        // Try Resend first (for cloud deployments)
        if (useResend()) {
            resendEmailService.sendHtmlEmail(to, subject, htmlContent);
            return;
        }

        // Fall back to SMTP
        if (mailSender == null) {
            logger.warn("No email service configured - skipping charity partner rejection email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Rejection notification sent to charity partner: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send rejection notification to charity partner: {}", to, e);
        } catch (Exception e) {
            logger.error("Unexpected error sending rejection email: {}", to, e);
        }
    }

    // ========================================
    // EMAIL TEMPLATE BUILDERS
    // ========================================

    private String buildParticipantApprovalEmailHtml(Referral referral) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .success-badge { background: #d1fae5; color: #065f46; padding: 10px 20px; border-radius: 20px; display: inline-block; font-weight: bold; margin: 20px 0; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Great News!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        
                        <div class="success-badge">✓ Your Referral Has Been Approved</div>
                        
                        <p>We're pleased to inform you that your referral request has been reviewed and approved by our team.</p>
                        
                        <div class="info-box">
                            <p><strong>Referral Number:</strong> %s</p>
                            <p><strong>Status:</strong> Approved</p>
                            <p><strong>Approved On:</strong> %s</p>
                        </div>
                        
                        <h3>What Happens Next?</h3>
                        <p>A shelter coordinator will be reaching out to you shortly with available accommodation options and next steps. Please keep your phone nearby and check your email regularly.</p>
                        
                        <p>If you have any questions or need immediate assistance, please contact the referring charity or our support team.</p>
                        
                        <p>We're here to help you through this process.</p>
                        
                        <p>Warm regards,<br><strong>The SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>This email was sent by SafelyNested Shelter Referral System.<br>
                        Please do not reply directly to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                referral.getParticipantName(),
                referral.getReferralNumber(),
                referral.getApprovedAt() != null ? referral.getApprovedAt().toLocalDate().toString() : "Today"
        );
    }

    private String buildCharityPartnerApprovalEmailHtml(Referral referral) {
        String viewUrl = baseUrl + "/charity-partner/referrals/" + referral.getId();

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .success-badge { background: #d1fae5; color: #065f46; padding: 10px 20px; border-radius: 20px; display: inline-block; font-weight: bold; margin: 20px 0; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #10b981; }
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Referral Approved ✓</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        
                        <div class="success-badge">Referral Approved</div>
                        
                        <p>Good news! The referral you submitted has been approved by our facilitator team.</p>
                        
                        <div class="info-box">
                            <p><strong>Referral Number:</strong> %s</p>
                            <p><strong>Participant:</strong> %s</p>
                            <p><strong>Status:</strong> ✓ Approved</p>
                            <p><strong>Approved On:</strong> %s</p>
                            %s
                        </div>
                        
                        <p>The participant will be notified and a booking can now be created for available shelter space.</p>
                        
                        <a href="%s" class="btn">View Referral Details</a>
                        
                        <p style="margin-top: 30px;">Thank you for your continued partnership in helping those in need.</p>
                        
                        <p>Best regards,<br><strong>SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested Shelter Referral System<br>
                        <a href="%s">%s</a></p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                referral.getReferralNumber(),
                referral.getParticipantName(),
                referral.getApprovedAt() != null ? referral.getApprovedAt().toLocalDate().toString() : "Today",
                referral.getFacilitatorNotes() != null && !referral.getFacilitatorNotes().isEmpty()
                        ? "<p><strong>Notes:</strong> " + referral.getFacilitatorNotes() + "</p>" : "",
                viewUrl,
                baseUrl,
                baseUrl
        );
    }

    private String buildCharityPartnerRejectionEmailHtml(Referral referral, String reason) {
        String viewUrl = baseUrl + "/charity-partner/referrals/" + referral.getId();

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .status-badge { background: #fee2e2; color: #991b1b; padding: 10px 20px; border-radius: 20px; display: inline-block; font-weight: bold; margin: 20px 0; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #ef4444; }
                    .reason-box { background: #fef3c7; padding: 15px; border-radius: 8px; margin: 20px 0; }
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Referral Update</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        
                        <div class="status-badge">Referral Not Approved</div>
                        
                        <p>We regret to inform you that the referral you submitted could not be approved at this time.</p>
                        
                        <div class="info-box">
                            <p><strong>Referral Number:</strong> %s</p>
                            <p><strong>Participant:</strong> %s</p>
                            <p><strong>Status:</strong> Not Approved</p>
                        </div>
                        
                        <div class="reason-box">
                            <p><strong>Reason:</strong></p>
                            <p>%s</p>
                        </div>
                        
                        <p>If you believe this decision was made in error or have additional information to provide, please contact our facilitator team or submit a new referral with updated information.</p>
                        
                        <a href="%s" class="btn">View Referral Details</a>
                        
                        <p style="margin-top: 30px;">We appreciate your understanding and continued efforts to help those in need.</p>
                        
                        <p>Best regards,<br><strong>SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested Shelter Referral System<br>
                        <a href="%s">%s</a></p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                referral.getReferralNumber(),
                referral.getParticipantName(),
                reason,
                viewUrl,
                baseUrl,
                baseUrl
        );
    }

    /**
     * Generic method to send a simple text email
     */
    public void sendEmail(String to, String subject, String body) {
        // Try Resend first (for cloud deployments)
        if (useResend()) {
            boolean success = resendEmailService.sendPlainTextEmail(to, subject, body);
            if (!success) {
                throw new RuntimeException("Failed to send email via Resend");
            }
            return;
        }

        // Fall back to SMTP
        if (mailSender == null) {
            logger.warn("No email service configured - skipping email to: {}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);  // false = plain text

            mailSender.send(message);
            logger.info("Email sent to: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Generic method to send an HTML email
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        // Try Resend first (for cloud deployments)
        if (useResend()) {
            boolean success = resendEmailService.sendHtmlEmail(to, subject, htmlBody);
            if (!success) {
                throw new RuntimeException("Failed to send email via Resend");
            }
            return;
        }

        // Fall back to SMTP
        if (mailSender == null) {
            logger.warn("No email service configured - skipping email to: {}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);  // true = HTML

            mailSender.send(message);
            logger.info("HTML email sent to: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending HTML email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}