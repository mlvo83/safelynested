package com.learning.learning.service;

import com.learning.learning.entity.User;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared "admin resets a user's password to a temporary one" flow: assigns a fresh temp
 * password, forces a change on next login (via {@code User.mustChangePassword}), emails it,
 * and returns the temp password so the admin can also relay it.
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public String resetToTemporaryPassword(User user, String displayName) {
        String temporaryPassword = generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        try {
            sendResetEmail(user, displayName, temporaryPassword);
        } catch (Exception e) {
            logger.error("Failed to send password-reset email to {}: {}", user.getEmail(), e.getMessage());
        }
        logger.info("Reset password for user '{}'", user.getUsername());
        return temporaryPassword;
    }

    private String generateTemporaryPassword() {
        return "TempPass" + (System.currentTimeMillis() % 100000) + "!";
    }

    private void sendResetEmail(User user, String displayName, String temporaryPassword) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return; // no address on file — the admin relays the temp password shown on screen
        }
        String subject = "Your SafelyNested Password Was Reset";
        String html = buildResetEmailHtml(displayName, user.getUsername(), temporaryPassword);
        emailService.sendHtmlEmail(user.getEmail(), subject, html);
    }

    private String buildResetEmailHtml(String greetingName, String username, String temporaryPassword) {
        String loginUrl = baseUrl + "/login";
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #1B2A4A; color: white; padding: 24px 30px; border-radius: 10px 10px 0 0; }
                    .header h2 { margin: 0; font-size: 20px; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .creds { background: white; padding: 20px; border-radius: 8px; margin: 16px 0; border-left: 4px solid #2A9D8F; }
                    .creds p { margin: 6px 0; }
                    .label { color: #5B6677; font-size: 13px; }
                    .btn { display: inline-block; background: #2A9D8F; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 16px; font-weight: bold; }
                    .footer { text-align: center; margin-top: 24px; color: #888; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header"><h2>Your Password Was Reset</h2></div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>An administrator has reset the password for your SafelyNested account.
                           Use the temporary password below to sign in.</p>
                        <div class="creds">
                            <p><span class="label">Username:</span> <strong>%s</strong></p>
                            <p><span class="label">Temporary password:</span> <strong>%s</strong></p>
                        </div>
                        <a href="%s" class="btn">Sign In</a>
                        <p style="margin-top: 24px;">You'll be asked to choose a new password when you sign in.
                           If you didn't expect this, contact your SafelyNested administrator.</p>
                        <p>Warm regards,<br><strong>The SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested - A Safe Place, Every Night<br>Please do not reply directly to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(nz(greetingName), username, temporaryPassword, loginUrl);
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "there" : s;
    }
}
