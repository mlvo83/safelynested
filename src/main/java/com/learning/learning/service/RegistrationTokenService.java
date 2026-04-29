package com.learning.learning.service;

import com.learning.learning.dto.UserDto;
import com.learning.learning.entity.*;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.LocationPartnerRepository;
import com.learning.learning.repository.RegistrationTokenRepository;
import com.learning.learning.repository.TeamInviteRepository;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
public class RegistrationTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationTokenService.class);

    @Autowired
    private RegistrationTokenRepository tokenRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamInviteRepository teamInviteRepository;

    @Autowired
    private LocationPartnerRepository locationPartnerRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public RegistrationToken createPrimaryContactToken(Charity charity, CharityApplication application, String email) {
        RegistrationToken token = new RegistrationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setEmail(email);
        token.setTokenType(RegistrationToken.TokenType.PRIMARY_CONTACT);
        token.setCharity(charity);
        token.setCharityApplication(application);
        token.setRoleToAssign("ROLE_CHARITY_PARTNER");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return tokenRepository.save(token);
    }

    public RegistrationToken createTeamMemberToken(Charity charity, TeamInvite invite, String email) {
        RegistrationToken token = new RegistrationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setEmail(email);
        token.setTokenType(RegistrationToken.TokenType.TEAM_MEMBER);
        token.setCharity(charity);
        token.setTeamInviteId(invite.getId());
        token.setRoleToAssign(invite.getRoleToAssign());
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return tokenRepository.save(token);
    }

    public RegistrationToken createLocationPartnerToken(LocationPartner partner, StayPartnerApplication application, String email) {
        RegistrationToken token = new RegistrationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setEmail(email);
        token.setTokenType(RegistrationToken.TokenType.LOCATION_PARTNER);
        token.setLocationPartner(partner);
        token.setStayPartnerApplication(application);
        token.setRoleToAssign("ROLE_LOCATION_PARTNER");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return tokenRepository.save(token);
    }

    public RegistrationToken validateToken(String tokenString) {
        RegistrationToken token = tokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new RuntimeException("Invalid registration link."));

        if (Boolean.TRUE.equals(token.getIsUsed())) {
            throw new RuntimeException("This registration link has already been used.");
        }

        if (token.isExpired()) {
            throw new RuntimeException("This registration link has expired. Please contact your administrator for a new invitation.");
        }

        return token;
    }

    @Transactional
    public User registerUserFromToken(String tokenString, String username, String password,
                                       String firstName, String lastName, String phone) {
        RegistrationToken token = validateToken(tokenString);

        // Check if email is already registered
        if (userRepository.existsByEmail(token.getEmail())) {
            throw new RuntimeException("An account with the email " + token.getEmail() + " already exists. Please log in instead, or contact your administrator if you need assistance.");
        }

        // Build UserDto
        UserDto userDto = new UserDto();
        userDto.setUsername(username);
        userDto.setPassword(password);
        userDto.setEmail(token.getEmail());
        userDto.setFirstName(firstName);
        userDto.setLastName(lastName);
        userDto.setPhone(phone);
        userDto.setRoles(Collections.singletonList(token.getRoleToAssign()));
        // Charity-scoped tokens set charityId; LOCATION_PARTNER tokens have no charity
        if (token.getCharity() != null) {
            userDto.setCharityId(token.getCharity().getId());
        }

        // Create user
        User user = userService.createUser(userDto);
        if (token.getCharity() != null) {
            logger.info("Created user '{}' from registration token for charity {}", username, token.getCharity().getCharityName());
        } else if (token.getLocationPartner() != null) {
            logger.info("Created user '{}' from LOCATION_PARTNER registration token (partner id={})",
                    username, token.getLocationPartner().getId());
        } else {
            logger.info("Created user '{}' from registration token", username);
        }

        // Handle type-specific wiring
        if (token.getTokenType() == RegistrationToken.TokenType.PRIMARY_CONTACT && token.getCharity() != null) {
            Charity charity = token.getCharity();
            charity.setPrimaryContact(user);
            String domain = token.getEmail().substring(token.getEmail().indexOf("@") + 1).toLowerCase();
            charity.setAllowedEmailDomain(domain);
            charityRepository.save(charity);
            logger.info("Set primary contact for charity '{}' to user '{}', domain: {}",
                    charity.getCharityName(), username, domain);
        }

        if (token.getTokenType() == RegistrationToken.TokenType.TEAM_MEMBER && token.getTeamInviteId() != null) {
            teamInviteRepository.findById(token.getTeamInviteId()).ifPresent(invite -> {
                invite.setStatus(TeamInvite.InviteStatus.ACCEPTED);
                invite.setAcceptedAt(LocalDateTime.now());
                invite.setResultUser(user);
                teamInviteRepository.save(invite);
                logger.info("Team invite {} accepted by user '{}'", invite.getId(), username);
            });
        }

        if (token.getTokenType() == RegistrationToken.TokenType.LOCATION_PARTNER && token.getLocationPartner() != null) {
            LocationPartner partner = token.getLocationPartner();
            partner.setUser(user);
            locationPartnerRepository.save(partner);
            logger.info("Linked user '{}' to LocationPartner id={}", username, partner.getId());
        }

        // Mark token as used
        token.setIsUsed(true);
        token.setUsedAt(LocalDateTime.now());
        token.setUsedByUser(user);
        tokenRepository.save(token);

        // Send welcome email (branching on token type)
        try {
            if (token.getTokenType() == RegistrationToken.TokenType.LOCATION_PARTNER) {
                sendLocationPartnerWelcomeEmail(user, token.getLocationPartner());
            } else if (token.getCharity() != null) {
                sendWelcomeEmail(user, token.getCharity());
            }
        } catch (Exception e) {
            logger.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }

        return user;
    }

    private void sendWelcomeEmail(User user, Charity charity) {
        String loginUrl = baseUrl + "/login";
        String subject = "Welcome to SafelyNested - " + charity.getCharityName();

        String html = """
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
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to SafelyNested!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="success-badge">Account Created Successfully</div>

                        <p>Your account has been set up for <strong>%s</strong>. You can now log in and start using the SafelyNested platform.</p>

                        <div class="info-box">
                            <p><strong>Username:</strong> %s</p>
                            <p><strong>Organization:</strong> %s</p>
                        </div>

                        <a href="%s" class="btn">Log In Now</a>

                        <p style="margin-top: 30px;">Warm regards,<br><strong>The SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested - A Safe Place, Every Night<br>
                        Please do not reply directly to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                user.getFullName(),
                charity.getCharityName(),
                user.getUsername(),
                charity.getCharityName(),
                loginUrl
        );

        emailService.sendHtmlEmail(user.getEmail(), subject, html);
    }

    private void sendLocationPartnerWelcomeEmail(User user, LocationPartner partner) {
        String loginUrl = baseUrl + "/login";
        String partnerName = partner != null ? partner.getDisplayName() : user.getFullName();
        String subject = "Welcome to SafelyNested - Location Partner";

        String html = """
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
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to SafelyNested!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="success-badge">Account Created Successfully</div>

                        <p>Your Location Partner account is ready. You can now log in to your dashboard, review your property, and mark the dates and times it's available to help families in need.</p>

                        <div class="info-box">
                            <p><strong>Username:</strong> %s</p>
                            <p><strong>Partner:</strong> %s</p>
                        </div>

                        <a href="%s" class="btn">Log In Now</a>

                        <p style="margin-top: 30px;">Thank you for opening your doors to families in need.</p>

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
                user.getFullName(),
                user.getUsername(),
                partnerName,
                loginUrl
        );

        emailService.sendHtmlEmail(user.getEmail(), subject, html);
    }
}
