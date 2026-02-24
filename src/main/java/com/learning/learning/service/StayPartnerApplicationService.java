package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.CharityLocationRepository;
import com.learning.learning.repository.StayPartnerApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
public class StayPartnerApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(StayPartnerApplicationService.class);

    @Autowired
    private StayPartnerApplicationRepository applicationRepository;

    @Autowired
    private CharityLocationRepository charityLocationRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ========================================
    // SUBMIT APPLICATION
    // ========================================

    @Transactional
    public StayPartnerApplication submitApplication(StayPartnerApplication application) {
        // Duplicate check by email
        if (applicationRepository.existsPendingByEmail(application.getEmail())) {
            throw new RuntimeException("A pending application already exists for this email address. Please check your application status instead.");
        }

        application.setApplicationNumber(generateApplicationNumber());
        application.setStatus(StayPartnerApplication.ApplicationStatus.PENDING);

        application = applicationRepository.save(application);
        logger.info("Stay partner application submitted: {} by {}", application.getApplicationNumber(), application.getEmail());

        // Send confirmation email
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
    public StayPartnerApplication approveApplicationWithCharity(Long id, User adminUser, String adminNotes, Charity charity) {
        StayPartnerApplication application = getApplicationById(id);

        if (!application.isPending() && application.getStatus() != StayPartnerApplication.ApplicationStatus.UNDER_REVIEW) {
            throw new RuntimeException("Application is not in a reviewable status.");
        }

        // Create CharityLocation (inactive by default)
        CharityLocation location = new CharityLocation();
        location.setCharity(charity);
        location.setLocationName(application.getPropertyName());
        location.setAddress(application.getStreetAddress());
        location.setCity(application.getCity());
        location.setState(application.getState());
        location.setZipCode(application.getZipCode());
        location.setCountry(application.getCountry());
        location.setPhone(application.getPrimaryPhone());
        location.setEmail(application.getPrimaryEmail());
        location.setCapacity(application.getMaxGuests());
        location.setDescription(application.getDescription());
        location.setIsActive(false); // Starts inactive

        location = charityLocationRepository.save(location);
        logger.info("Created CharityLocation {} for application {}", location.getId(), application.getApplicationNumber());

        // Update application
        application.setStatus(StayPartnerApplication.ApplicationStatus.APPROVED);
        application.setReviewedBy(adminUser);
        application.setReviewedAt(LocalDateTime.now());
        application.setAdminNotes(adminNotes);
        application.setResultLocation(location);

        application = applicationRepository.save(application);
        logger.info("Approved application {}", application.getApplicationNumber());

        // Send approval email
        try {
            sendApprovalEmail(application);
        } catch (Exception e) {
            logger.error("Failed to send approval email for {}: {}", application.getApplicationNumber(), e.getMessage());
        }

        return application;
    }

    @Transactional
    public StayPartnerApplication rejectApplication(Long id, User adminUser, String rejectionReason) {
        StayPartnerApplication application = getApplicationById(id);

        if (!application.isPending() && application.getStatus() != StayPartnerApplication.ApplicationStatus.UNDER_REVIEW) {
            throw new RuntimeException("Application is not in a reviewable status.");
        }

        application.setStatus(StayPartnerApplication.ApplicationStatus.REJECTED);
        application.setReviewedBy(adminUser);
        application.setReviewedAt(LocalDateTime.now());
        application.setRejectionReason(rejectionReason);

        application = applicationRepository.save(application);
        logger.info("Rejected application {}: {}", application.getApplicationNumber(), rejectionReason);

        // Send rejection email
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

    public StayPartnerApplication getApplicationById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stay partner application not found: " + id));
    }

    public List<StayPartnerApplication> getAllApplications() {
        return applicationRepository.findAllOrderByStatusAndDate();
    }

    public List<StayPartnerApplication> getApplicationsByStatus(StayPartnerApplication.ApplicationStatus status) {
        return applicationRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public long countPendingApplications() {
        return applicationRepository.countByStatus(StayPartnerApplication.ApplicationStatus.PENDING);
    }

    public StayPartnerApplication lookupStatus(String applicationNumber, String email) {
        return applicationRepository.findByApplicationNumberAndEmail(applicationNumber, email)
                .orElse(null);
    }

    // ========================================
    // HELPERS
    // ========================================

    private String generateApplicationNumber() {
        int year = Year.now().getValue();
        long count = applicationRepository.count() + 1;
        return String.format("SPA-%d-%04d", year, count);
    }

    // ========================================
    // EMAIL BUILDERS
    // ========================================

    private void sendConfirmationEmail(StayPartnerApplication application) {
        String subject = "Application Received: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildConfirmationEmailHtml(application);
        emailService.sendHtmlEmail(application.getPrimaryEmail(), subject, html);
    }

    private void sendApprovalEmail(StayPartnerApplication application) {
        String subject = "Application Approved: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildApprovalEmailHtml(application);
        emailService.sendHtmlEmail(application.getPrimaryEmail(), subject, html);
    }

    private void sendRejectionEmail(StayPartnerApplication application) {
        String subject = "Application Update: " + application.getApplicationNumber() + " - SafelyNested";
        String html = buildRejectionEmailHtml(application);
        emailService.sendHtmlEmail(application.getPrimaryEmail(), subject, html);
    }

    private String buildConfirmationEmailHtml(StayPartnerApplication application) {
        String statusUrl = baseUrl + "/stay-partner/status";

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

                        <p>Thank you for your interest in becoming a SafelyNested Stay Partner! We have received your application and our team will review it shortly.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Property:</strong> %s</p>
                            <p><strong>Location:</strong> %s, %s %s</p>
                            <p><strong>Status:</strong> Pending Review</p>
                        </div>

                        <h3>What Happens Next?</h3>
                        <p>Our team will review your application within a few business days. You'll receive an email notification when a decision has been made.</p>

                        <p>You can check your application status at any time:</p>
                        <a href="%s" class="btn">Check Application Status</a>

                        <p style="margin-top: 30px;">Thank you for your willingness to help families in need.</p>

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
                application.getApplicantDisplayName(),
                application.getApplicationNumber(),
                application.getPropertyName(),
                application.getCity(),
                application.getState(),
                application.getZipCode(),
                statusUrl
        );
    }

    private String buildApprovalEmailHtml(StayPartnerApplication application) {
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

                        <p>We're excited to welcome you as a SafelyNested Stay Partner! Your property has been added to our network.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Property:</strong> %s</p>
                            <p><strong>Status:</strong> Approved</p>
                        </div>

                        <h3>What Happens Next?</h3>
                        <p>Your property listing has been created and will be activated once our team completes the onboarding process. A coordinator will reach out to you to finalize details and get you set up.</p>

                        <p>Thank you for opening your doors to families in need. Together, we can make a difference.</p>

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
                application.getApplicantDisplayName(),
                application.getApplicationNumber(),
                application.getPropertyName()
        );
    }

    private String buildRejectionEmailHtml(StayPartnerApplication application) {
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

                        <p>Thank you for your interest in becoming a SafelyNested Stay Partner. After careful review, we are unable to approve your application at this time.</p>

                        <div class="info-box">
                            <p><strong>Application Number:</strong> %s</p>
                            <p><strong>Property:</strong> %s</p>
                            <p><strong>Status:</strong> Not Approved</p>
                        </div>

                        <div class="reason-box">
                            <p><strong>Reason:</strong></p>
                            <p>%s</p>
                        </div>

                        <p>If you believe this decision was made in error or have additional information, please feel free to submit a new application or contact us directly.</p>

                        <p>We appreciate your willingness to help and hope to work with you in the future.</p>

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
                application.getApplicantDisplayName(),
                application.getApplicationNumber(),
                application.getPropertyName(),
                application.getRejectionReason()
        );
    }
}
