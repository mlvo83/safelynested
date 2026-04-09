package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donation;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.DonationRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    private static final BigDecimal TOTAL_FEE_RATE = new BigDecimal("0.10"); // 10% total (7% + 3%)
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private DonationService donationService;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private EmailService emailService;

    // ========================================
    // CREATE CHECKOUT SESSION
    // ========================================

    public String createCheckoutSession(Long charityId, BigDecimal amount, boolean coverFees,
                                         String donorEmail, String donorName) throws Exception {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        if (!Boolean.TRUE.equals(charity.getIsActive())) {
            throw new RuntimeException("This charity is not currently accepting donations.");
        }

        // Calculate the gross amount (if covering fees, increase so charity gets full intended amount)
        BigDecimal grossAmount;
        BigDecimal originalAmount = amount;

        if (coverFees) {
            // adjustedGross = intendedNet / (1 - 0.10) = intendedNet / 0.90
            grossAmount = amount.divide(ONE.subtract(TOTAL_FEE_RATE), 2, RoundingMode.CEILING);
        } else {
            grossAmount = amount;
        }

        // Convert to cents for Stripe (Stripe uses minor currency units)
        long amountInCents = grossAmount.multiply(new BigDecimal("100")).longValue();

        if (amountInCents < 50) {
            throw new RuntimeException("Minimum donation amount is $0.50.");
        }

        // Build Stripe Checkout Session
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.US_BANK_ACCOUNT)
                .setSuccessUrl(baseUrl + "/donate/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/donate/cancel")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Donation to " + charity.getCharityName())
                                                                .setDescription("Supporting families in need through SafelyNested")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("charity_id", charityId.toString())
                .putMetadata("original_amount", originalAmount.toPlainString())
                .putMetadata("cover_fees", String.valueOf(coverFees))
                .putMetadata("fee_structure_version", "v1.0");

        if (donorEmail != null && !donorEmail.isEmpty()) {
            paramsBuilder.setCustomerEmail(donorEmail);
            paramsBuilder.putMetadata("donor_email", donorEmail);
        }
        if (donorName != null && !donorName.isEmpty()) {
            paramsBuilder.putMetadata("donor_name", donorName);
        }

        Session session = Session.create(paramsBuilder.build());
        logger.info("Created Stripe Checkout Session {} for charity {} (amount: {})",
                session.getId(), charity.getCharityName(), grossAmount);

        return session.getUrl();
    }

    // ========================================
    // WEBHOOK HANDLING
    // ========================================

    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        logger.info("Received Stripe webhook event: {} ({})", event.getType(), event.getId());

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new RuntimeException("Failed to deserialize session from webhook"));

            handleCheckoutCompleted(session);
        }
    }

    private void handleCheckoutCompleted(Session session) {
        String sessionId = session.getId();

        // Idempotency check
        if (donationRepository.findByStripeSessionId(sessionId).isPresent()) {
            logger.info("Donation already exists for session {}, skipping (duplicate webhook)", sessionId);
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        Long charityId = Long.parseLong(metadata.get("charity_id"));
        BigDecimal originalAmount = new BigDecimal(metadata.get("original_amount"));
        boolean coverFees = Boolean.parseBoolean(metadata.get("cover_fees"));
        String donorName = metadata.getOrDefault("donor_name", null);
        String donorEmail = session.getCustomerEmail();
        if (donorEmail == null) {
            donorEmail = metadata.getOrDefault("donor_email", null);
        }

        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        // Get gross amount from Stripe (in cents)
        BigDecimal grossAmount = new BigDecimal(session.getAmountTotal())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Calculate fees
        DonationService.DonationBreakdown breakdown = donationService.calculateFees(grossAmount);

        // Estimate Stripe processing fee (2.9% + $0.30)
        BigDecimal stripeFee = grossAmount.multiply(new BigDecimal("0.029"))
                .add(new BigDecimal("0.30"))
                .setScale(2, RoundingMode.HALF_UP);

        // Calculate nights funded
        int nightsFunded = donationService.calculateNightsFunded(breakdown.netAmount(), charityId);
        BigDecimal avgRate = donationService.getAverageNightlyRate(charityId);

        // Build donation record
        Donation donation = new Donation();
        donation.setDonor(null); // Anonymous online donation
        donation.setCharity(charity);
        donation.setGrossAmount(grossAmount);
        donation.setPlatformFee(breakdown.platformFee());
        donation.setFacilitatorFee(breakdown.facilitatorFee());
        donation.setProcessingFee(stripeFee);
        donation.setNetAmount(breakdown.netAmount());
        donation.setNightsFunded(nightsFunded);
        donation.setAvgNightlyRateAtDonation(avgRate);
        donation.setStatus(Donation.DonationStatus.VERIFIED);
        donation.setVerificationStatus(Donation.VerificationStatus.VERIFIED);
        donation.setFeeStructureVersion("v1.0");
        donation.setDonatedAt(LocalDateTime.now());
        donation.setVerifiedAt(LocalDateTime.now());
        donation.setNotes("Online donation via Stripe Checkout");
        donation.setStripeSessionId(sessionId);
        donation.setStripePaymentIntentId(session.getPaymentIntent());
        donation.setDonorEmail(donorEmail);
        donation.setDonorName(donorName);
        donation.setPaymentSource("STRIPE");
        donation.setCoverFees(coverFees);
        donation.setOriginalAmount(originalAmount);

        donation = donationRepository.save(donation);
        logger.info("Created donation {} from Stripe session {} for charity {} (gross: {}, net: {})",
                donation.getId(), sessionId, charity.getCharityName(), grossAmount, breakdown.netAmount());

        // Send confirmation email
        if (donorEmail != null && !donorEmail.isEmpty()) {
            try {
                sendDonationConfirmationEmail(donation, charity);
            } catch (Exception e) {
                logger.error("Failed to send donation confirmation email to {}: {}", donorEmail, e.getMessage());
            }
        }
    }

    // ========================================
    // RETRIEVE SESSION (for success page)
    // ========================================

    public Session retrieveSession(String sessionId) throws Exception {
        return Session.retrieve(sessionId);
    }

    // ========================================
    // EMAIL
    // ========================================

    private void sendDonationConfirmationEmail(Donation donation, Charity charity) {
        String subject = "Thank You for Your Donation - SafelyNested";
        String donorDisplayName = donation.getDonorName() != null ? donation.getDonorName() : "Generous Donor";

        String nightsInfo = "";
        if (donation.getNightsFunded() != null && donation.getNightsFunded() > 0) {
            nightsInfo = """
                <div style="background: #d1fae5; padding: 15px; border-radius: 8px; margin: 15px 0; text-align: center;">
                    <p style="margin: 0; font-size: 24px; font-weight: bold; color: #065f46;">%d</p>
                    <p style="margin: 5px 0 0; color: #065f46;">nights of safe housing funded</p>
                </div>
                """.formatted(donation.getNightsFunded());
        }

        String coverFeesNote = "";
        if (Boolean.TRUE.equals(donation.getCoverFees())) {
            coverFeesNote = "<p style=\"color: #059669; font-size: 13px;\">You generously chose to cover the processing fees so 100%% of your intended donation goes to the charity.</p>";
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
                    .success-badge { background: #d1fae5; color: #065f46; padding: 10px 20px; border-radius: 20px; display: inline-block; font-weight: bold; margin: 20px 0; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #667eea; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Thank You!</h1>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>

                        <div class="success-badge">Donation Received</div>

                        <p>Thank you for your generous donation to <strong>%s</strong> through SafelyNested. Your contribution helps provide safe, temporary housing for families in need.</p>

                        <div class="info-box">
                            <p><strong>Amount:</strong> $%s</p>
                            <p><strong>Organization:</strong> %s</p>
                            <p><strong>Date:</strong> %s</p>
                        </div>

                        %s

                        %s

                        <p>Your generosity makes a real difference. Together, we can help families find safety and stability.</p>

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
                donorDisplayName,
                charity.getCharityName(),
                donation.getGrossAmount().toPlainString(),
                charity.getCharityName(),
                donation.getDonatedAt().toLocalDate().toString(),
                nightsInfo,
                coverFeesNote
        );

        emailService.sendHtmlEmail(donation.getDonorEmail(), subject, html);
    }
}
