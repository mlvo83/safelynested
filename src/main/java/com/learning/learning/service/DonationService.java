package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DonationService {

    private static final Logger logger = LoggerFactory.getLogger(DonationService.class);

    // Fee percentages (10% total)
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.07");      // 7%
    private static final BigDecimal FACILITATOR_FEE_RATE = new BigDecimal("0.03");   // 3%
    private static final String CURRENT_FEE_STRUCTURE_VERSION = "v1.0";

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private NightlyRateRepository nightlyRateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerService ledgerService;

    // ========================================
    // FEE CALCULATION
    // ========================================

    /**
     * Calculate fee breakdown for a donation amount
     */
    public DonationBreakdown calculateFees(BigDecimal grossAmount) {
        BigDecimal platformFee = grossAmount.multiply(PLATFORM_FEE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal facilitatorFee = grossAmount.multiply(FACILITATOR_FEE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFees = platformFee.add(facilitatorFee);
        BigDecimal netAmount = grossAmount.subtract(totalFees);

        return new DonationBreakdown(
                grossAmount,
                platformFee,
                facilitatorFee,
                BigDecimal.ZERO, // Processing fee - added separately if needed
                totalFees,
                netAmount
        );
    }

    /**
     * Calculate how many nights can be funded with a net amount
     */
    public int calculateNightsFunded(BigDecimal netAmount, Long charityId) {
        BigDecimal avgRate = nightlyRateRepository.getAverageActiveRateForCharity(charityId);

        if (avgRate == null || avgRate.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("No nightly rates found for charity {}, cannot calculate nights funded", charityId);
            return 0;
        }

        return netAmount.divide(avgRate, 0, RoundingMode.FLOOR).intValue();
    }

    /**
     * Get current average nightly rate for a charity
     */
    public BigDecimal getAverageNightlyRate(Long charityId) {
        return nightlyRateRepository.getAverageActiveRateForCharity(charityId);
    }

    // ========================================
    // DONATION RECORDING
    // ========================================

    /**
     * Record a new donation (manual entry by admin)
     */
    @Transactional
    public Donation recordDonation(Long donorId, Long charityId, BigDecimal grossAmount,
                                   String notes, String recorderUsername) {
        logger.info("Recording donation: donor={}, charity={}, amount={}", donorId, charityId, grossAmount);

        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found: " + donorId));

        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        User recorder = userRepository.findByUsername(recorderUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + recorderUsername));

        // Calculate fees
        DonationBreakdown breakdown = calculateFees(grossAmount);

        // Calculate nights funded
        BigDecimal avgRate = nightlyRateRepository.getAverageActiveRateForCharity(charityId);
        int nightsFunded = calculateNightsFunded(breakdown.netAmount(), charityId);

        // Create donation record
        Donation donation = new Donation();
        donation.setDonor(donor);
        donation.setCharity(charity);
        donation.setGrossAmount(grossAmount);
        donation.setPlatformFee(breakdown.platformFee());
        donation.setFacilitatorFee(breakdown.facilitatorFee());
        donation.setProcessingFee(BigDecimal.ZERO);
        donation.setNetAmount(breakdown.netAmount());
        donation.setNightsFunded(nightsFunded);
        donation.setAvgNightlyRateAtDonation(avgRate);
        donation.setStatus(Donation.DonationStatus.PENDING);
        donation.setVerificationStatus(Donation.VerificationStatus.PENDING);
        donation.setFeeStructureVersion(CURRENT_FEE_STRUCTURE_VERSION);
        donation.setDonatedAt(LocalDateTime.now());
        donation.setRecordedBy(recorder);
        donation.setNotes(notes);

        donation = donationRepository.save(donation);

        // Ensure donor is associated with charity
        if (!donor.isAssociatedWithCharity(charityId)) {
            donor.addCharity(charity);
            donorRepository.save(donor);
        }

        logger.info("Recorded donation {}: gross={}, net={}, nights={}",
                donation.getId(), grossAmount, breakdown.netAmount(), nightsFunded);

        return donation;
    }

    // ========================================
    // DONATION RETRIEVAL
    // ========================================

    /**
     * Get donation by ID
     */
    public Donation getDonationById(Long donationId) {
        return donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found: " + donationId));
    }

    /**
     * Get donations for a donor
     */
    public List<Donation> getDonationsForDonor(Long donorId) {
        return donationRepository.findByDonorIdOrderByDonatedAtDesc(donorId);
    }

    /**
     * Get donations for a donor at a specific charity
     */
    public List<Donation> getDonationsForDonorAtCharity(Long donorId, Long charityId) {
        return donationRepository.findByDonorIdAndCharityIdOrderByDonatedAtDesc(donorId, charityId);
    }

    /**
     * Get donations for a charity
     */
    public List<Donation> getDonationsForCharity(Long charityId) {
        return donationRepository.findByCharityIdOrderByDonatedAtDesc(charityId);
    }

    /**
     * Get all donations (admin)
     */
    public List<Donation> getAllDonations() {
        return donationRepository.findAll();
    }

    /**
     * Get donations pending verification
     */
    public List<Donation> getPendingVerificationDonations() {
        return donationRepository.findPendingVerification();
    }

    // ========================================
    // DONATION VERIFICATION
    // ========================================

    /**
     * Verify a donation and record to ledger
     */
    @Transactional
    public Donation verifyDonation(Long donationId, String verifierUsername) {
        Donation donation = getDonationById(donationId);
        User verifier = userRepository.findByUsername(verifierUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        donation.setVerificationStatus(Donation.VerificationStatus.VERIFIED);
        donation.setStatus(Donation.DonationStatus.VERIFIED);
        donation.setVerifiedAt(LocalDateTime.now());
        donation.setVerifiedBy(verifier);

        donation = donationRepository.save(donation);

        // Record to ledger - this creates the accounting entries
        try {
            var ledgerTransaction = ledgerService.recordDonation(donation, verifier);
            donation.setLedgerTransaction(ledgerTransaction);
            donation = donationRepository.save(donation);
            logger.info("Recorded donation {} to ledger as transaction {}",
                    donation.getId(), ledgerTransaction.getTransactionCode());
        } catch (Exception e) {
            logger.error("Failed to record donation {} to ledger: {}", donation.getId(), e.getMessage());
            // Don't fail the verification if ledger recording fails - log and continue
        }

        return donation;
    }

    /**
     * Reject a donation
     */
    @Transactional
    public Donation rejectDonation(Long donationId, String reason, String verifierUsername) {
        Donation donation = getDonationById(donationId);
        User verifier = userRepository.findByUsername(verifierUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        donation.setVerificationStatus(Donation.VerificationStatus.REJECTED);
        donation.setStatus(Donation.DonationStatus.CANCELLED);
        donation.setVerifiedAt(LocalDateTime.now());
        donation.setVerifiedBy(verifier);
        donation.setNotes(donation.getNotes() + "\nRejection reason: " + reason);

        return donationRepository.save(donation);
    }

    // ========================================
    // DONATION STATUS UPDATES
    // ========================================

    /**
     * Mark donation as allocated (funds assigned to situations)
     */
    @Transactional
    public Donation markAsAllocated(Long donationId) {
        Donation donation = getDonationById(donationId);
        donation.setStatus(Donation.DonationStatus.ALLOCATED);
        return donationRepository.save(donation);
    }

    /**
     * Mark donation as partially used
     */
    @Transactional
    public Donation markAsPartiallyUsed(Long donationId) {
        Donation donation = getDonationById(donationId);
        donation.setStatus(Donation.DonationStatus.PARTIALLY_USED);
        return donationRepository.save(donation);
    }

    /**
     * Mark donation as fully used
     */
    @Transactional
    public Donation markAsFullyUsed(Long donationId) {
        Donation donation = getDonationById(donationId);
        donation.setStatus(Donation.DonationStatus.FULLY_USED);
        return donationRepository.save(donation);
    }

    // ========================================
    // AGGREGATIONS
    // ========================================

    /**
     * Get total donated by donor
     */
    public BigDecimal getTotalDonatedByDonor(Long donorId) {
        BigDecimal total = donationRepository.sumGrossAmountByDonorId(donorId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get total net amount for donor
     */
    public BigDecimal getTotalNetAmountForDonor(Long donorId) {
        BigDecimal total = donationRepository.sumNetAmountByDonorId(donorId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get total nights funded by donor
     */
    public int getTotalNightsFundedByDonor(Long donorId) {
        Integer total = donationRepository.sumNightsFundedByDonorId(donorId);
        return total != null ? total : 0;
    }

    /**
     * Get total fees paid by donor
     */
    public BigDecimal getTotalFeesByDonor(Long donorId) {
        BigDecimal platformFees = donationRepository.sumPlatformFeeByDonorId(donorId);
        BigDecimal facilitatorFees = donationRepository.sumFacilitatorFeeByDonorId(donorId);

        BigDecimal total = BigDecimal.ZERO;
        if (platformFees != null) total = total.add(platformFees);
        if (facilitatorFees != null) total = total.add(facilitatorFees);

        return total;
    }

    /**
     * Count donations for donor
     */
    public long countDonationsForDonor(Long donorId) {
        return donationRepository.countByDonorId(donorId);
    }

    // ========================================
    // DTO for fee breakdown
    // ========================================

    public record DonationBreakdown(
            BigDecimal grossAmount,
            BigDecimal platformFee,
            BigDecimal facilitatorFee,
            BigDecimal processingFee,
            BigDecimal totalFees,
            BigDecimal netAmount
    ) {}
}
