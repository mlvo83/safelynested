package com.learning.learning.service;

import com.learning.learning.dto.ReferralDto;
import com.learning.learning.entity.CharityLocation;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.ReferralInvite;
import com.learning.learning.entity.User;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
public class ReferralService {

    private static final Logger logger = LoggerFactory.getLogger(ReferralService.class);

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@safelynested.org}")
    private String fromEmail;

    /**
     * Create a new referral
     */
    @Transactional
    public Referral createReferral(ReferralDto referralDto, String username) {
        User referringUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Referral referral = new Referral();
        referral.setReferralNumber(generateReferralNumber());
        referral.setParticipantName(referralDto.getParticipantName());
        referral.setParticipantEmail(referralDto.getParticipantEmail());
        referral.setParticipantPhone(referralDto.getParticipantPhone());
        referral.setParticipantAge(referralDto.getParticipantAge());
        referral.setNeedsDescription(referralDto.getNeedsDescription());
        referral.setUrgencyLevel(Referral.UrgencyLevel.valueOf(referralDto.getUrgencyLevel()));
        referral.setStatus(Referral.ReferralStatus.PENDING);
        referral.setReferredByUser(referringUser);
        referral.setReferredByCharity(referralDto.getReferredByCharity());

        return referralRepository.save(referral);
    }

    /**
     * Get all referrals
     */
    public List<Referral> getAllReferrals() {
        return referralRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get referrals by status
     */
    public List<Referral> getReferralsByStatus(Referral.ReferralStatus status) {
        return referralRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Get referral by ID
     */
    public Referral getReferralById(Long id) {
        return referralRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Referral not found"));
    }

    /**
     * Approve a referral
     */
    @Transactional
    public Referral approveReferral(Long referralId, String facilitatorUsername, String notes) {
        Referral referral = getReferralById(referralId);

        if (referral.getStatus() != Referral.ReferralStatus.PENDING) {
            throw new RuntimeException("Only pending referrals can be approved");
        }

        User facilitator = userRepository.findByUsername(facilitatorUsername)
                .orElseThrow(() -> new RuntimeException("Facilitator not found"));

        referral.setStatus(Referral.ReferralStatus.APPROVED);
        referral.setApprovedByUser(facilitator);
        referral.setApprovedAt(LocalDateTime.now());
        referral.setFacilitatorNotes(notes);

        Referral savedReferral = referralRepository.save(referral);

        // Send notification emails (async - won't block the approval)
        sendApprovalNotifications(savedReferral);

        return savedReferral;
    }

    /**
     * Reject a referral
     */
    @Transactional
    public Referral rejectReferral(Long referralId, String facilitatorUsername, String reason) {
        Referral referral = getReferralById(referralId);

        if (referral.getStatus() != Referral.ReferralStatus.PENDING) {
            throw new RuntimeException("Only pending referrals can be rejected");
        }

        User facilitator = userRepository.findByUsername(facilitatorUsername)
                .orElseThrow(() -> new RuntimeException("Facilitator not found"));

        referral.setStatus(Referral.ReferralStatus.REJECTED);
        referral.setApprovedByUser(facilitator);
        referral.setApprovedAt(LocalDateTime.now());
        referral.setRejectedReason(reason);

        Referral savedReferral = referralRepository.save(referral);

        // Send notification emails (async - won't block the rejection)
        sendRejectionNotifications(savedReferral, reason);

        return savedReferral;
    }

    // ========================================
    // INVITE TO REFERRAL CREATION
    // ========================================

    /**
     * Create a new referral from a completed invite.
     * This is called when a participant selects a location via an invite link.
     *
     * @param invite The completed invite with selected location
     * @param selectedLocation The location selected by the participant
     * @return The created referral
     */
    @Transactional
    public Referral createReferralFromInvite(ReferralInvite invite, CharityLocation selectedLocation) {
        logger.info("Creating referral from invite {} for participant {}",
                invite.getId(), invite.getRecipientName());

        Referral referral = new Referral();
        referral.setReferralNumber(generateReferralNumber());
        referral.setParticipantName(invite.getRecipientName());
        referral.setParticipantEmail(invite.getRecipientEmail());
        referral.setParticipantPhone(invite.getRecipientPhone());
        referral.setCharity(invite.getCharity());
        referral.setReferredByUser(invite.getCreatedBy());
        referral.setReferredByCharity(invite.getCharity().getCharityName());
        referral.setUrgencyLevel(Referral.UrgencyLevel.MEDIUM);
        referral.setStatus(Referral.ReferralStatus.PENDING);
        referral.setSelectedLocation(selectedLocation);
        referral.setLocationSelectedAt(LocalDateTime.now());
        referral.setAllowedZipCodes(invite.getAllowedZipCodes());

        // Add participant notes as needs description if provided
        if (invite.getParticipantNotes() != null && !invite.getParticipantNotes().isEmpty()) {
            referral.setNeedsDescription("Participant notes: " + invite.getParticipantNotes());
        }

        Referral savedReferral = referralRepository.save(referral);
        logger.info("Created referral {} from invite {}", savedReferral.getReferralNumber(), invite.getId());

        return savedReferral;
    }

    /**
     * Update an existing referral with the selected location from a completed invite.
     * This is called when an invite was linked to an existing referral.
     *
     * @param referral The existing referral to update
     * @param selectedLocation The location selected by the participant
     * @param participantNotes Optional notes from the participant
     * @return The updated referral
     */
    @Transactional
    public Referral updateReferralWithLocationSelection(Referral referral, CharityLocation selectedLocation, String participantNotes) {
        logger.info("Updating referral {} with selected location {}",
                referral.getReferralNumber(), selectedLocation.getLocationName());

        referral.setSelectedLocation(selectedLocation);
        referral.setLocationSelectedAt(LocalDateTime.now());

        // Append participant notes to needs description if provided
        if (participantNotes != null && !participantNotes.isEmpty()) {
            String existingDescription = referral.getNeedsDescription();
            if (existingDescription != null && !existingDescription.isEmpty()) {
                referral.setNeedsDescription(existingDescription + "\n\nParticipant notes: " + participantNotes);
            } else {
                referral.setNeedsDescription("Participant notes: " + participantNotes);
            }
        }

        Referral savedReferral = referralRepository.save(referral);
        logger.info("Updated referral {} with location selection", savedReferral.getReferralNumber());

        return savedReferral;
    }

    // ========================================
    // EMAIL NOTIFICATION METHODS
    // ========================================

    /**
     * Send approval notification emails
     */
    private void sendApprovalNotifications(Referral referral) {
        // Send to participant (if email exists)
        if (referral.getParticipantEmail() != null && !referral.getParticipantEmail().isEmpty()) {
            sendParticipantApprovalEmail(referral);
        }

        // Send to charity partner who created the referral
        if (referral.getReferredByUser() != null && referral.getReferredByUser().getEmail() != null) {
            sendCharityPartnerApprovalEmail(referral);
        }
    }

    /**
     * Send rejection notification emails
     */
    private void sendRejectionNotifications(Referral referral, String reason) {
        // Send to charity partner who created the referral
        if (referral.getReferredByUser() != null && referral.getReferredByUser().getEmail() != null) {
            sendCharityPartnerRejectionEmail(referral, reason);
        }
    }

    /**
     * Send approval email to participant
     */
    @Async
    protected void sendParticipantApprovalEmail(Referral referral) {
        if (mailSender == null) {
            logger.warn("Mail sender not configured - skipping participant approval email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(referral.getParticipantEmail());
            helper.setSubject("Good News! Your Referral Has Been Approved - SafelyNested");

            String htmlContent = buildParticipantApprovalEmailHtml(referral);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Approval email sent to participant: {}", referral.getParticipantEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send approval email to participant: {}", referral.getParticipantEmail(), e);
        }
    }

    /**
     * Send approval notification to charity partner
     */
    @Async
    protected void sendCharityPartnerApprovalEmail(Referral referral) {
        if (mailSender == null) {
            logger.warn("Mail sender not configured - skipping charity partner approval email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(referral.getReferredByUser().getEmail());
            helper.setSubject("Referral Approved: " + referral.getReferralNumber() + " - SafelyNested");

            String htmlContent = buildCharityPartnerApprovalEmailHtml(referral);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Approval notification sent to charity partner: {}", referral.getReferredByUser().getEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send approval notification to charity partner: {}",
                    referral.getReferredByUser().getEmail(), e);
        }
    }

    /**
     * Send rejection notification to charity partner
     */
    @Async
    protected void sendCharityPartnerRejectionEmail(Referral referral, String reason) {
        if (mailSender == null) {
            logger.warn("Mail sender not configured - skipping charity partner rejection email");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(referral.getReferredByUser().getEmail());
            helper.setSubject("Referral Update: " + referral.getReferralNumber() + " - SafelyNested");

            String htmlContent = buildCharityPartnerRejectionEmailHtml(referral, reason);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("Rejection notification sent to charity partner: {}", referral.getReferredByUser().getEmail());

        } catch (MessagingException e) {
            logger.error("Failed to send rejection notification to charity partner: {}",
                    referral.getReferredByUser().getEmail(), e);
        }
    }

    // ========================================
    // EMAIL TEMPLATE BUILDERS
    // ========================================

    /**
     * Build HTML email for participant approval
     */
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

    /**
     * Build HTML email for charity partner approval notification
     */
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

    /**
     * Build HTML email for charity partner rejection notification
     */
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

    // ========================================
    // STATISTICS
    // ========================================

    /**
     * Get statistics
     */
    public ReferralStatistics getStatistics() {
        ReferralStatistics stats = new ReferralStatistics();
        stats.setPending(referralRepository.countByStatus(Referral.ReferralStatus.PENDING));
        stats.setApproved(referralRepository.countByStatus(Referral.ReferralStatus.APPROVED));
        stats.setRejected(referralRepository.countByStatus(Referral.ReferralStatus.REJECTED));
        stats.setTotal(referralRepository.count());
        return stats;
    }

    /**
     * Generate unique referral number
     */
    private String generateReferralNumber() {
        String prefix = "REF-" + Year.now().getValue() + "-";
        long count = referralRepository.count() + 1;
        return prefix + String.format("%03d", count);
    }

    // Inner class for statistics
    public static class ReferralStatistics {
        private Long pending;
        private Long approved;
        private Long rejected;
        private Long total;

        // Getters and setters
        public Long getPending() { return pending; }
        public void setPending(Long pending) { this.pending = pending; }

        public Long getApproved() { return approved; }
        public void setApproved(Long approved) { this.approved = approved; }

        public Long getRejected() { return rejected; }
        public void setRejected(Long rejected) { this.rejected = rejected; }

        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
    }
}