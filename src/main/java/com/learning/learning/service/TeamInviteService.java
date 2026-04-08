package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.TeamInviteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TeamInviteService {

    private static final Logger logger = LoggerFactory.getLogger(TeamInviteService.class);

    @Autowired
    private TeamInviteRepository teamInviteRepository;

    @Autowired
    private RegistrationTokenService registrationTokenService;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public TeamInvite sendTeamInvite(Charity charity, User invitedBy, String email,
                                      String firstName, String lastName, String message) {
        // Validate email domain
        String allowedDomain = charity.getAllowedEmailDomain();
        if (allowedDomain != null && !allowedDomain.isEmpty()) {
            String inviteDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
            if (!inviteDomain.equals(allowedDomain.toLowerCase())) {
                throw new RuntimeException("Team members must have an @" + allowedDomain +
                        " email address. The email " + email + " is not allowed.");
            }
        }

        // Check for existing pending invite
        if (teamInviteRepository.existsByEmailAndCharityIdAndStatus(
                email, charity.getId(), TeamInvite.InviteStatus.PENDING)) {
            throw new RuntimeException("A pending invitation already exists for " + email + ".");
        }

        // Create team invite
        TeamInvite invite = new TeamInvite();
        invite.setCharity(charity);
        invite.setInvitedBy(invitedBy);
        invite.setEmail(email.trim().toLowerCase());
        invite.setFirstName(firstName);
        invite.setLastName(lastName);
        invite.setMessage(message);
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setStatus(TeamInvite.InviteStatus.PENDING);

        invite = teamInviteRepository.save(invite);
        logger.info("Created team invite for {} to charity {}", email, charity.getCharityName());

        // Create registration token
        RegistrationToken regToken = registrationTokenService.createTeamMemberToken(charity, invite, email);

        // Send invite email
        try {
            sendInviteEmail(invite, regToken, charity, invitedBy);
        } catch (Exception e) {
            logger.error("Failed to send team invite email to {}: {}", email, e.getMessage());
        }

        return invite;
    }

    public List<TeamInvite> getInvitesForCharity(Long charityId) {
        return teamInviteRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
    }

    public long getPendingInviteCount(Long charityId) {
        return teamInviteRepository.countByCharityIdAndStatus(charityId, TeamInvite.InviteStatus.PENDING);
    }

    @Transactional
    public void cancelInvite(Long inviteId, Long charityId) {
        TeamInvite invite = teamInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        if (!invite.getCharity().getId().equals(charityId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (!invite.isPending()) {
            throw new RuntimeException("Only pending invites can be cancelled.");
        }

        invite.setStatus(TeamInvite.InviteStatus.CANCELLED);
        teamInviteRepository.save(invite);
        logger.info("Cancelled team invite {} for {}", inviteId, invite.getEmail());
    }

    private void sendInviteEmail(TeamInvite invite, RegistrationToken token, Charity charity, User invitedBy) {
        String registrationUrl = baseUrl + "/charity-application/register/" + token.getToken();
        String subject = "You've been invited to join " + charity.getCharityName() + " on SafelyNested";

        String personalMessage = "";
        if (invite.getMessage() != null && !invite.getMessage().isEmpty()) {
            personalMessage = """
                <div style="background: #f0f9ff; padding: 15px; border-radius: 8px; margin: 15px 0; border-left: 4px solid #667eea;">
                    <p style="margin: 0; font-style: italic;">"%s"</p>
                    <p style="margin: 5px 0 0; font-size: 13px; color: #666;">— %s</p>
                </div>
                """.formatted(invite.getMessage(), invitedBy.getFullName());
        }

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                    .btn { display: inline-block; background: #667eea; color: white; padding: 14px 28px; text-decoration: none; border-radius: 5px; margin-top: 20px; font-size: 16px; font-weight: bold; }
                    .warning { background: #fef3c7; padding: 12px; border-radius: 8px; margin: 15px 0; font-size: 13px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>You're Invited!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <p><strong>%s</strong> has invited you to join <strong>%s</strong> on SafelyNested, a platform that connects families in need with safe, temporary housing.</p>

                        %s

                        <div class="info-box">
                            <p><strong>Organization:</strong> %s</p>
                            <p><strong>Invited by:</strong> %s</p>
                        </div>

                        <p>Click the button below to create your account and get started:</p>

                        <a href="%s" class="btn">Create Your Account</a>

                        <div class="warning">
                            <strong>Note:</strong> This invitation will expire in 7 days.
                        </div>

                        <p>Warm regards,<br><strong>The SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested - A Safe Place, Every Night<br>
                        Please do not reply directly to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                invite.getFullName(),
                invitedBy.getFullName(),
                charity.getCharityName(),
                personalMessage,
                charity.getCharityName(),
                invitedBy.getFullName(),
                registrationUrl
        );

        emailService.sendHtmlEmail(invite.getEmail(), subject, html);
    }
}
