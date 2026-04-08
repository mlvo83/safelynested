package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.CharityApplicationRepository;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.RegistrationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class CharityApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(CharityApplicationService.class);

    @Autowired
    private CharityApplicationRepository applicationRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private RegistrationTokenRepository registrationTokenRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ========================================
    // SUBMIT APPLICATION
    // ========================================

    @Transactional
    public CharityApplication submitApplication(CharityApplication application) {
        if (applicationRepository.existsPendingByEmail(application.getContactEmail())) {
            throw new RuntimeException("A pending application already exists for this email address. Please check your application status instead.");
        }

        application.setApplicationNumber(generateApplicationNumber());
        application.setStatus(CharityApplication.ApplicationStatus.PENDING);

        application = applicationRepository.save(application);
        logger.info("Charity application submitted: {} by {}", application.getApplicationNumber(), application.getContactEmail());

        try {
            sendConfirmationEmail(application);
        } catch (Exception e) {
            logger.error("Failed to send confirmation email for {}: {}", application.getApplicationNumber(), e.getMessage());
        }

        return application;
    }

    // ========================================
    // APPROVE / REJECT
    // ========================================

    @Transactional
    public CharityApplication approveApplication(Long id, User adminUser, String adminNotes) {
        CharityApplication application = getApplicationById(id);

        if (!application.isReviewable()) {
            throw new RuntimeException("Application is not in a reviewable status.");
        }

        // Create Charity from application data
        Charity charity = new Charity();
        charity.setCharityName(application.getCharityName());
        charity.setOrganizationType(application.getOrganizationType());
        charity.setEinTaxId(application.getEinTaxId());
        charity.setContactName(application.getContactName());
        charity.setContactEmail(application.getContactEmail());
        charity.setContactPhone(application.getContactPhone());
        charity.setAddress(application.getAddress());
        charity.setCity(application.getCity());
        charity.setState(application.getState());
        charity.setZipCode(application.getZipCode());
        charity.setCountry(application.getCountry());
        charity.setDescription(application.getDescription());
        charity.setMissionStatement(application.getMissionStatement());
        charity.setIsActive(true);
        charity.setIsVerified(true);
        charity.setVerifiedBy(adminUser);
        charity.setVerifiedAt(LocalDateTime.now());

        charity = charityRepository.save(charity);
        logger.info("Created Charity {} for application {}", charity.getId(), application.getApplicationNumber());

        // Create registration token for primary contact
        RegistrationToken token = new RegistrationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setEmail(application.getContactEmail());
        token.setTokenType(RegistrationToken.TokenType.PRIMARY_CONTACT);
        token.setCharity(charity);
        token.setCharityApplication(application);
        token.setRoleToAssign("ROLE_CHARITY_PARTNER");
        token.setExpiresAt(LocalDateTime.now().plusDays(7));

        registrationTokenRepository.save(token);
        logger.info("Created registration token for {}", application.getContactEmail());

        // Update application
        application.setStatus(CharityApplication.ApplicationStatus.APPROVED);
        application.setReviewedBy(adminUser);
        application.setReviewedAt(LocalDateTime.now());
        application.setAdminNotes(adminNotes);
        application.setResultCharity(charity);

        application = applicationRepository.save(application);
        logger.info("Approved charity application {}", application.getApplicationNumber());

        // Send approval email with registration link
        try {
            sendApprovalEmail(application, token);
        } catch (Exception e) {
            logger.error("Failed to send approval email for {}: {}", application.getApplicationNumber(), e.getMessage());
        }

        return application;
    }

    @Transactional
    public CharityApplication rejectApplication(Long id, User adminUser, String rejectionReason) {
        CharityApplication application = getApplicationById(id);

        if (!application.isReviewable()) {
            throw new RuntimeException("Application is not in a reviewable status.");
        }

        application.setStatus(CharityApplication.ApplicationStatus.REJECTED);
        application.setReviewedBy(adminUser);
        application.setReviewedAt(LocalDateTime.now());
        application.setRejectionReason(rejectionReason);

        application = applicationRepository.save(application);
        logger.info("Rejected charity application {}: {}", application.getApplicationNumber(), rejectionReason);

        try {
            sendRejectionEmail(application);
        } catch (Exception e) {
            logger.error("Failed to send rejection email for {}: {}", application.getApplicationNumber(), e.getMessage());
        }

        return application;
    }

    // ========================================
    // RETRIEVAL
    // ========================================

    public CharityApplication getApplicationById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Charity application not found: " + id));
    }

    public List<CharityApplication> getAllApplications() {
        return applicationRepository.findAllOrderByStatusAndDate();
    }

    public List<CharityApplication> getApplicationsByStatus(CharityApplication.ApplicationStatus status) {
        return applicationRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public long countPendingApplications() {
        return applicationRepository.countByStatus(CharityApplication.ApplicationStatus.PENDING);
    }

    public CharityApplication lookupStatus(String applicationNumber, String email) {
        return applicationRepository.findByApplicationNumberAndContactEmail(applicationNumber, email)
                .orElse(null);
    }

    // ========================================
    // HELPERS
    // ========================================

    private String generateApplicationNumber() {
        int year = Year.now().getValue();
        long count = applicationRepository.count() + 1;
        return String.format("CA-%d-%04d", year, count);
    }

    // ========================================
    // EMAIL BUILDERS
    // ========================================

    private void sendConfirmationEmail(CharityApplication application) {
        String subject = "Application Received: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildConfirmationEmailHtml(application);
        emailService.sendHtmlEmail(application.getContactEmail(), subject, html);
    }

    private void sendApprovalEmail(CharityApplication application, RegistrationToken token) {
        String subject = "Application Approved: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildApprovalEmailHtml(application, token);
        emailService.sendHtmlEmail(application.getContactEmail(), subject, html);
    }

    private void sendRejectionEmail(CharityApplication application) {
        String subject = "Application Update: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildRejectionEmailHtml(application);
        emailService.sendHtmlEmail(application.getContactEmail(), subject, html);
    }

    private String buildConfirmationEmailHtml(CharityApplication application) {
        String statusUrl = baseUrl + "/charity-application/status";

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .success-badge { background: #dbeafe; color: #1e40af; padding: 10px 20px; border-radius: 20px; display: inline-block; font-weight: bold; margin: 20px 0; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Application Received</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="success-badge">Application Submitted Successfully</div>

                        <p>Thank you for your interest in joining SafelyNested as a Charity Partner! We have received your application and our team will review it shortly.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Organization:</strong> %s</p>
                            <p><strong>Status:</strong> Pending Review</p>
                        </div>

                        <h3>What Happens Next?</h3>
                        <p>Our team will review your application within a few business days. Once approved, you'll receive an email with a link to create your account and access your charity dashboard.</p>

                        <p>You can check your application status at any time:</p>
                        <a href="%s" class="btn">Check Application Status</a>

                        <p style="margin-top: 30px;">Thank you for your dedication to helping families in need.</p>

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
                application.getContactName(),
                application.getApplicationNumber(),
                application.getCharityName(),
                statusUrl
        );
    }

    private String buildApprovalEmailHtml(CharityApplication application, RegistrationToken token) {
        String registrationUrl = baseUrl + "/charity-application/register/" + token.getToken();

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
                    .btn { display: inline-block; background: #10b981; color: white; padding: 14px 28px; text-decoration: none; border-radius: 5px; margin-top: 20px; font-size: 16px; font-weight: bold; }
                    .warning { background: #fef3c7; padding: 12px; border-radius: 8px; margin: 15px 0; font-size: 13px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Application Approved!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="success-badge">Your Application Has Been Approved</div>

                        <p>We're excited to welcome <strong>%s</strong> to the SafelyNested platform! Your organization has been verified and is ready to go.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Organization:</strong> %s</p>
                            <p><strong>Status:</strong> Approved</p>
                        </div>

                        <h3>Create Your Account</h3>
                        <p>Click the button below to set up your username and password. Once registered, you'll have full access to your charity dashboard where you can manage referrals, invite team members, and more.</p>

                        <a href="%s" class="btn">Create Your Account</a>

                        <div class="warning">
                            <strong>Note:</strong> This link will expire in 7 days. If it expires, please contact us for a new invitation.
                        </div>

                        <p>Thank you for joining our mission to help families find safe housing.</p>

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
                application.getContactName(),
                application.getCharityName(),
                application.getApplicationNumber(),
                application.getCharityName(),
                registrationUrl
        );
    }

    private String buildRejectionEmailHtml(CharityApplication application) {
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
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Application Update</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="status-badge">Application Not Approved</div>

                        <p>Thank you for your interest in joining SafelyNested as a Charity Partner. After careful review, we are unable to approve your application at this time.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Organization:</strong> %s</p>
                            <p><strong>Status:</strong> Not Approved</p>
                        </div>

                        <div class="reason-box">
                            <p><strong>Reason:</strong></p>
                            <p>%s</p>
                        </div>

                        <p>If you believe this decision was made in error or have additional information, please feel free to submit a new application or contact us directly.</p>

                        <p>We appreciate your dedication and hope to work with you in the future.</p>

                        <p>Best regards,<br><strong>The SafelyNested Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>SafelyNested - A Safe Place, Every Night<br>
                        Please do not reply directly to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                application.getContactName(),
                application.getApplicationNumber(),
                application.getCharityName(),
                application.getRejectionReason()
        );
    }
}
