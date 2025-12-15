package com.learning.learning.service;

import com.learning.learning.entity.ReferralInvite;
import com.learning.learning.entity.Charity;
import com.learning.learning.repository.ReferralInviteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InviteService {

    private static final Logger logger = LoggerFactory.getLogger(InviteService.class);

    @Autowired
    private ReferralInviteRepository referralInviteRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CharityService charityService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ========================================
    // QUERY METHODS (used by CharityPartnerController)
    // ========================================

    /**
     * Get invite statistics for a charity partner
     */
    // ============================================================
// DEBUG VERSION of getInviteStats with extensive logging
// Add this to InviteService.java
// ============================================================

    public Map<String, Object> getInviteStats(String username) {
        System.out.println("=== GET INVITE STATS START ===");

        Long charityId = charityService.getCharityIdForUser(username);
        System.out.println("Charity ID: " + charityId);

        List<ReferralInvite> allInvites = referralInviteRepository.findAllByCharityId(charityId);
        System.out.println("Total invites found: " + allInvites.size());

        // Print each invite for debugging
        for (ReferralInvite inv : allInvites) {
            System.out.println("  - Invite ID: " + inv.getId() +
                    ", Status: " + inv.getStatus() +
                    ", Recipient: " + inv.getRecipientName() +
                    ", Charity ID: " + (inv.getCharity() != null ? inv.getCharity().getId() : "NULL") +
                    ", Referral ID: " + (inv.getReferral() != null ? inv.getReferral().getId() : "NULL"));
        }

        Map<String, Object> stats = new HashMap<>();

        // Count by status
        long total = allInvites.size();
        long pending = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.PENDING)
                .count();
        long sent = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.SENT ||
                        i.getStatus() == ReferralInvite.InviteStatus.OPENED)
                .count();
        long opened = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.OPENED)
                .count();
        long completed = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.COMPLETED)
                .count();
        long expired = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.EXPIRED)
                .count();
        long cancelled = allInvites.stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.CANCELLED)
                .count();

        System.out.println("Stats calculated:");
        System.out.println("  Total: " + total);
        System.out.println("  Pending: " + pending);
        System.out.println("  Sent: " + sent);
        System.out.println("  Opened: " + opened);
        System.out.println("  Completed: " + completed);
        System.out.println("  Expired: " + expired);
        System.out.println("  Cancelled: " + cancelled);

        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("sent", sent);
        stats.put("opened", opened);
        stats.put("completed", completed);
        stats.put("expired", expired);
        stats.put("cancelled", cancelled);

        System.out.println("=== GET INVITE STATS END ===");

        return stats;
    }

    /**
     * Get all invites for a specific referral
     */
    public List<ReferralInvite> getInvitesForReferral(Long referralId, String username) {
        // Verify user has access to this referral's charity
        Charity charity = charityService.getCharityForUser(username);

        return referralInviteRepository.findByReferralId(referralId).stream()
                .filter(invite -> {
                    // Check if invite belongs to user's charity
                    if (invite.getCharity() != null) {
                        return invite.getCharity().getId().equals(charity.getId());
                    }
                    if (invite.getReferral() != null && invite.getReferral().getCharity() != null) {
                        return invite.getReferral().getCharity().getId().equals(charity.getId());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all invites for a charity partner
     */
    public List<ReferralInvite> getInvitesForCharity(String username) {
        Charity charity = charityService.getCharityForUser(username);
        return referralInviteRepository.findAllByCharityId(charity.getId());
    }

    /**
     * Get pending invites for a charity partner
     */
    public List<ReferralInvite> getPendingInvites(String username) {
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        return referralInviteRepository.findAllByCharityId(charityId).stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.PENDING)
                .collect(Collectors.toList());
    }

    /**
     * Get sent invites for a charity partner
     */
    public List<ReferralInvite> getSentInvites(String username) {
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        return referralInviteRepository.findAllByCharityId(charityId).stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.SENT ||
                        i.getStatus() == ReferralInvite.InviteStatus.OPENED)
                .collect(Collectors.toList());
    }

    /**
     * Get all invites for a charity partner (alias for getInvitesForCharity)
     */
    public List<ReferralInvite> getAllInvites(String username) {
        return getInvitesForCharity(username);
    }

    /**
     * Get completed invites (with location selected) for a charity partner
     */
    public List<ReferralInvite> getCompletedInvites(String username) {
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        return referralInviteRepository.findAllByCharityId(charityId).stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /**
     * Get expired invites for a charity partner
     */
    public List<ReferralInvite> getExpiredInvites(String username) {
        Charity charity = charityService.getCharityForUser(username);
        Long charityId = charity.getId();

        return referralInviteRepository.findAllByCharityId(charityId).stream()
                .filter(i -> i.getStatus() == ReferralInvite.InviteStatus.EXPIRED)
                .collect(Collectors.toList());
    }

    /**
     * Save and send an invite
     * Can be called with or without a linked referral
     */
    /**
     * Save and send invite
     */
    public ReferralInvite saveAndSendInvite(ReferralInvite invite, String username) {
        System.out.println("=== SAVE AND SEND INVITE START ===");
        System.out.println("Recipient: " + invite.getRecipientName());
        System.out.println("Initial Status: " + invite.getStatus());

        // Verify charity ownership
        Long charityId = charityService.getCharityIdForUser(username);
        Charity charity = invite.getCharity();

        if (charity == null || !charity.getId().equals(charityId)) {
            throw new RuntimeException("Unauthorized: Cannot send invite for this charity");
        }

        // Validate invite has contact method
        if ((invite.getRecipientEmail() == null || invite.getRecipientEmail().trim().isEmpty()) &&
                (invite.getRecipientPhone() == null || invite.getRecipientPhone().trim().isEmpty())) {
            throw new RuntimeException("Invite must have either email or phone number");
        }

        // Ensure required fields are set
        if (invite.getInviteToken() == null || invite.getInviteToken().isEmpty()) {
            invite.setInviteToken(UUID.randomUUID().toString());
        }
        if (invite.getExpiresAt() == null) {
            invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        }
        if (invite.getCreatedAt() == null) {
            invite.setCreatedAt(LocalDateTime.now());
        }

        // Initially set to PENDING
        invite.setStatus(ReferralInvite.InviteStatus.PENDING);
        System.out.println("Status set to PENDING before first save");

        // Save first
        invite = referralInviteRepository.save(invite);
        System.out.println("Saved with ID: " + invite.getId() + ", Status: " + invite.getStatus());

        // Build invite URL
        String inviteUrl = buildInviteUrl(invite.getInviteToken());
        System.out.println("Invite URL: " + inviteUrl);

        try {
            System.out.println("Attempting to send via: " + invite.getInviteType());

            // Send notification based on type
            switch (invite.getInviteType()) {
                case EMAIL -> {
                    System.out.println("Sending email...");
                    sendEmailInvite(invite, inviteUrl);
                    System.out.println("Email sent successfully");
                }
                case SMS -> {
                    System.out.println("Sending SMS...");
                    sendSmsInvite(invite, inviteUrl);
                    System.out.println("SMS logged (not actually sent)");
                }
                case BOTH -> {
                    System.out.println("Sending both email and SMS...");
                    sendEmailInvite(invite, inviteUrl);
                    sendSmsInvite(invite, inviteUrl);
                    System.out.println("Both sent");
                }
            }

            // *** THIS IS THE CRITICAL PART ***
            System.out.println(">>> UPDATING STATUS TO SENT <<<");
            invite.setStatus(ReferralInvite.InviteStatus.SENT);
            invite.setSentAt(LocalDateTime.now());
            invite = referralInviteRepository.save(invite);

            System.out.println(">>> AFTER UPDATE: Status = " + invite.getStatus());
            System.out.println(">>> AFTER UPDATE: SentAt = " + invite.getSentAt());
            System.out.println(">>> AFTER UPDATE: ID = " + invite.getId());

        } catch (Exception e) {
            System.err.println("!!! ERROR SENDING INVITE: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send invite: " + e.getMessage());
        }

        System.out.println("=== SAVE AND SEND INVITE END - RETURNING STATUS: " + invite.getStatus() + " ===");
        return invite;
    }


    /**
     * Send the actual notification (email or SMS)
     */
    private void sendInviteNotification(ReferralInvite invite) {
        String inviteUrl = buildInviteUrl(invite.getInviteToken());

        switch (invite.getInviteType()) {
            case EMAIL:
                if (invite.getRecipientEmail() != null && !invite.getRecipientEmail().isEmpty()) {
                    sendEmailInvite(invite, inviteUrl);
                }
                break;
            case SMS:
                if (invite.getRecipientPhone() != null && !invite.getRecipientPhone().isEmpty()) {
                    sendSmsInvite(invite, inviteUrl);
                }
                break;
            case BOTH:
                if (invite.getRecipientEmail() != null && !invite.getRecipientEmail().isEmpty()) {
                    sendEmailInvite(invite, inviteUrl);
                }
                if (invite.getRecipientPhone() != null && !invite.getRecipientPhone().isEmpty()) {
                    sendSmsInvite(invite, inviteUrl);
                }
                break;
        }
    }

    private String buildInviteUrl(String token) {
        return baseUrl + "/referral/invite/" + token;
    }

    private void sendEmailInvite(ReferralInvite invite, String inviteUrl) {
        // Get charity name
        String charityName = getCharityName(invite);

        String subject = "You're Invited - Select Your Preferred Location";
        String body = String.format(
                "Hello %s,\n\n" +
                        "%s has invited you to select a preferred location for your stay.\n\n" +
                        "Please click the link below to view available locations and make your selection:\n\n" +
                        "%s\n\n" +
                        "This link will expire in 7 days.\n\n" +
                        "%s\n\n" +
                        "Best regards,\n%s",
                invite.getRecipientName(),
                charityName,
                inviteUrl,
                invite.getMessage() != null && !invite.getMessage().isEmpty()
                        ? "Message from " + charityName + ": " + invite.getMessage()
                        : "",
                charityName
        );

        try {
            emailService.sendEmail(invite.getRecipientEmail(), subject, body);
            logger.info("Invite email sent to: {}", invite.getRecipientEmail());
        } catch (Exception e) {
            logger.error("Failed to send invite email to {}: {}", invite.getRecipientEmail(), e.getMessage());
            throw e;
        }
    }

    private void sendSmsInvite(ReferralInvite invite, String inviteUrl) {
        // Get charity name
        String charityName = getCharityName(invite);

        String message = String.format(
                "%s invites you to select your preferred location. Click here: %s",
                charityName,
                inviteUrl
        );

        // TODO: Implement SMS service integration (Twilio, etc.)
        // smsService.sendSms(invite.getRecipientPhone(), message);
        logger.info("SMS would be sent to {}: {}", invite.getRecipientPhone(), message);
    }

    /**
     * Helper to get charity name from invite (handles both direct and referral-linked invites)
     */
    private String getCharityName(ReferralInvite invite) {
        // Try direct charity first
        if (invite.getCharity() != null) {
            return invite.getCharity().getCharityName();
        }
        // Fall back to referral's charity
        if (invite.getReferral() != null && invite.getReferral().getCharity() != null) {
            return invite.getReferral().getCharity().getCharityName();
        }
        return "Our Organization";
    }

    /**
     * Resend an invite
     */
    public ReferralInvite resendInvite(Long inviteId, String username) {
        Charity charity = charityService.getCharityForUser(username);

        ReferralInvite invite = referralInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        // Verify ownership
        Long inviteCharityId = invite.getCharity() != null ? invite.getCharity().getId() :
                (invite.getReferral() != null && invite.getReferral().getCharity() != null ?
                        invite.getReferral().getCharity().getId() : null);

        if (inviteCharityId == null || !inviteCharityId.equals(charity.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Generate new token and reset expiry
        invite.setInviteToken(UUID.randomUUID().toString());
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setStatus(ReferralInvite.InviteStatus.PENDING);

        return saveAndSendInvite(invite, username);
    }

    /**
     * Cancel an invite
     */
    public void cancelInvite(Long inviteId, String username) {
        Charity charity = charityService.getCharityForUser(username);

        ReferralInvite invite = referralInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        // Verify ownership
        Long inviteCharityId = invite.getCharity() != null ? invite.getCharity().getId() :
                (invite.getReferral() != null && invite.getReferral().getCharity() != null ?
                        invite.getReferral().getCharity().getId() : null);

        if (inviteCharityId == null || !inviteCharityId.equals(charity.getId())) {
            throw new RuntimeException("Access denied");
        }

        invite.setStatus(ReferralInvite.InviteStatus.CANCELLED);
        referralInviteRepository.save(invite);

        logger.info("Invite {} cancelled by user {}", inviteId, username);
    }

    /**
     * Check if invite is expired and update status
     */
    public boolean checkAndUpdateExpiredStatus(ReferralInvite invite) {
        if (invite.getExpiresAt() != null &&
                invite.getExpiresAt().isBefore(LocalDateTime.now()) &&
                invite.getStatus() != ReferralInvite.InviteStatus.COMPLETED &&
                invite.getStatus() != ReferralInvite.InviteStatus.CANCELLED) {

            invite.setStatus(ReferralInvite.InviteStatus.EXPIRED);
            referralInviteRepository.save(invite);
            return true;
        }
        return false;
    }
}