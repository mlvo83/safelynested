package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Situations - the privacy abstraction layer.
 *
 * CRITICAL PRIVACY NOTE:
 * This service handles the bridge between internal referrals and donor-visible
 * "situations". When returning data to donors, NEVER include referral IDs,
 * beneficiary information, or any PII.
 */
@Service
public class SituationService {

    private static final Logger logger = LoggerFactory.getLogger(SituationService.class);

    @Autowired
    private SituationRepository situationRepository;

    @Autowired
    private SituationFundingRepository situationFundingRepository;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReferralRepository referralRepository;

    // ========================================
    // SITUATION CREATION (Admin/Facilitator)
    // ========================================

    /**
     * Create a new situation from a referral.
     * This is used by admins/facilitators to create the privacy-safe view.
     */
    @Transactional
    public Situation createSituationFromReferral(Long referralId, String description,
                                                  Situation.SituationCategory category,
                                                  String creatorUsername) {
        logger.info("Creating situation from referral: {}", referralId);

        Referral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new RuntimeException("Referral not found: " + referralId));

        // Check if situation already exists for this referral
        if (situationRepository.findByReferralId(referralId).isPresent()) {
            throw new RuntimeException("Situation already exists for referral: " + referralId);
        }

        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + creatorUsername));

        Situation situation = new Situation();
        situation.setCharity(referral.getCharity());
        situation.setReferral(referral);
        situation.setDescription(sanitizeDescription(description));
        situation.setCategory(category);
        situation.setStartDate(LocalDate.now());
        situation.setIsActive(true);
        situation.setCreatedBy(creator);

        situation = situationRepository.save(situation);
        logger.info("Created situation {} for referral {}", situation.getId(), referralId);

        return situation;
    }

    /**
     * Create a standalone situation (not linked to a referral).
     * Useful for generic fund allocation.
     */
    @Transactional
    public Situation createSituation(Long charityId, String description,
                                     Situation.SituationCategory category,
                                     String creatorUsername) {
        logger.info("Creating standalone situation for charity: {}", charityId);

        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + creatorUsername));

        Situation situation = new Situation();
        situation.setCharity(charity);
        situation.setDescription(sanitizeDescription(description));
        situation.setCategory(category);
        situation.setStartDate(LocalDate.now());
        situation.setIsActive(true);
        situation.setCreatedBy(creator);

        situation = situationRepository.save(situation);
        logger.info("Created standalone situation {}", situation.getId());

        return situation;
    }

    // ========================================
    // SITUATION RETRIEVAL
    // ========================================

    /**
     * Get situation by ID
     */
    public Situation getSituationById(Long situationId) {
        return situationRepository.findById(situationId)
                .orElseThrow(() -> new RuntimeException("Situation not found: " + situationId));
    }

    /**
     * Get all active situations for a charity
     */
    public List<Situation> getActiveSituationsForCharity(Long charityId) {
        return situationRepository.findByCharityIdAndIsActiveTrue(charityId);
    }

    /**
     * Get all situations for a charity
     */
    public List<Situation> getSituationsForCharity(Long charityId) {
        return situationRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
    }

    /**
     * Search situations for a charity
     */
    public List<Situation> searchSituations(Long charityId, String searchTerm) {
        return situationRepository.searchByCharityId(charityId, searchTerm);
    }

    /**
     * Get situations by category for a charity
     */
    public List<Situation> getSituationsByCategory(Long charityId, Situation.SituationCategory category) {
        return situationRepository.findByCharityIdAndCategory(charityId, category);
    }

    /**
     * Get situations with available funding
     */
    public List<Situation> getSituationsWithAvailableFunding(Long charityId) {
        return situationRepository.findSituationsWithAvailableFunding(charityId);
    }

    // ========================================
    // DONOR-SAFE SITUATION VIEWS
    // ========================================

    /**
     * Get situations funded by a donor.
     * Returns privacy-safe DTOs without referral information.
     */
    public List<DonorSituationView> getSituationsForDonor(Long donorId) {
        List<SituationFunding> fundings = situationFundingRepository.findByDonorIdWithSituation(donorId);

        return fundings.stream()
                .map(this::toDonorSituationView)
                .collect(Collectors.toList());
    }

    /**
     * Get situations funded by a specific donation.
     * Returns privacy-safe DTOs without referral information.
     */
    public List<DonorSituationView> getSituationsForDonation(Long donationId) {
        List<SituationFunding> fundings = situationFundingRepository.findByDonationIdWithSituation(donationId);

        return fundings.stream()
                .map(this::toDonorSituationView)
                .collect(Collectors.toList());
    }

    /**
     * Get unique situations funded by a donor
     */
    public List<DonorSituationSummary> getUniqueSituationsFundedByDonor(Long donorId) {
        List<SituationFunding> fundings = situationFundingRepository.findByDonorIdWithSituation(donorId);

        // Group by situation and aggregate
        return fundings.stream()
                .collect(Collectors.groupingBy(sf -> sf.getSituation().getId()))
                .values().stream()
                .map(this::aggregateSituationFundings)
                .collect(Collectors.toList());
    }

    // ========================================
    // FUNDING ALLOCATION
    // ========================================

    /**
     * Allocate a donation to a situation.
     * This is how funds get linked to specific cases.
     */
    @Transactional
    public SituationFunding allocateDonationToSituation(Long donationId, Long situationId,
                                                         int nightsToAllocate, BigDecimal amountToAllocate,
                                                         String allocatorUsername) {
        logger.info("Allocating donation {} to situation {}: {} nights, ${}",
                donationId, situationId, nightsToAllocate, amountToAllocate);

        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new RuntimeException("Donation not found: " + donationId));

        Situation situation = getSituationById(situationId);

        User allocator = userRepository.findByUsername(allocatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + allocatorUsername));

        // Verify donation and situation are from the same charity
        if (!donation.getCharity().getId().equals(situation.getCharity().getId())) {
            throw new RuntimeException("Donation and situation must be from the same charity");
        }

        // Verify donation has enough nights available
        int nightsAlreadyAllocated = situationFundingRepository.sumNightsAllocatedByDonationId(donationId);
        int nightsAvailable = donation.getNightsFunded() - nightsAlreadyAllocated;

        if (nightsToAllocate > nightsAvailable) {
            throw new RuntimeException("Not enough nights available. Requested: " + nightsToAllocate +
                    ", Available: " + nightsAvailable);
        }

        // Create funding record
        SituationFunding funding = new SituationFunding();
        funding.setDonation(donation);
        funding.setSituation(situation);
        funding.setNightsAllocated(nightsToAllocate);
        funding.setNightsUsed(0);
        funding.setAmountAllocated(amountToAllocate);
        funding.setAllocatedAt(LocalDateTime.now());
        funding.setAllocatedBy(allocator);

        funding = situationFundingRepository.save(funding);

        // Update donation status
        updateDonationStatus(donation);

        logger.info("Created situation funding {}: {} nights allocated", funding.getId(), nightsToAllocate);

        return funding;
    }

    /**
     * Record usage of allocated nights
     */
    @Transactional
    public SituationFunding recordNightsUsed(Long fundingId, int nightsUsed, String explanation) {
        SituationFunding funding = situationFundingRepository.findById(fundingId)
                .orElseThrow(() -> new RuntimeException("Funding not found: " + fundingId));

        int totalUsed = (funding.getNightsUsed() != null ? funding.getNightsUsed() : 0) + nightsUsed;
        int allocated = funding.getNightsAllocated() != null ? funding.getNightsAllocated() : 0;

        if (totalUsed > allocated) {
            throw new RuntimeException("Cannot use more nights than allocated. Allocated: " + allocated +
                    ", Would become: " + totalUsed);
        }

        funding.setNightsUsed(totalUsed);
        if (explanation != null && !explanation.isEmpty()) {
            String existingExplanation = funding.getUsageExplanation();
            if (existingExplanation != null && !existingExplanation.isEmpty()) {
                funding.setUsageExplanation(existingExplanation + "\n" + explanation);
            } else {
                funding.setUsageExplanation(explanation);
            }
        }

        funding = situationFundingRepository.save(funding);

        // Update donation status based on usage
        updateDonationStatus(funding.getDonation());

        logger.info("Updated funding {}: {} nights used of {} allocated",
                fundingId, totalUsed, allocated);

        return funding;
    }

    // ========================================
    // SITUATION UPDATES
    // ========================================

    /**
     * Update situation description (must remain privacy-safe)
     */
    @Transactional
    public Situation updateSituation(Long situationId, String description,
                                     Situation.SituationCategory category) {
        Situation situation = getSituationById(situationId);

        if (description != null) {
            situation.setDescription(sanitizeDescription(description));
        }
        if (category != null) {
            situation.setCategory(category);
        }

        return situationRepository.save(situation);
    }

    /**
     * Close a situation
     */
    @Transactional
    public Situation closeSituation(Long situationId) {
        Situation situation = getSituationById(situationId);
        situation.setIsActive(false);
        situation.setEndDate(LocalDate.now());
        return situationRepository.save(situation);
    }

    /**
     * Reactivate a situation
     */
    @Transactional
    public Situation reactivateSituation(Long situationId) {
        Situation situation = getSituationById(situationId);
        situation.setIsActive(true);
        situation.setEndDate(null);
        return situationRepository.save(situation);
    }

    // ========================================
    // STATISTICS
    // ========================================

    /**
     * Get situation statistics for a charity
     */
    public SituationStats getStatsForCharity(Long charityId) {
        long totalSituations = situationRepository.countByCharityId(charityId);
        long activeSituations = situationRepository.countByCharityIdAndIsActiveTrue(charityId);

        List<Object[]> categoryStats = situationRepository.countByCategoryForCharity(charityId);

        return new SituationStats(totalSituations, activeSituations, categoryStats);
    }

    /**
     * Count situations for charity
     */
    public long countSituationsForCharity(Long charityId) {
        return situationRepository.countByCharityId(charityId);
    }

    /**
     * Count active situations for charity
     */
    public long countActiveSituationsForCharity(Long charityId) {
        return situationRepository.countByCharityIdAndIsActiveTrue(charityId);
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Sanitize description to ensure no PII is included
     */
    private String sanitizeDescription(String description) {
        if (description == null) {
            return "";
        }
        // Remove potential PII patterns (basic sanitization)
        // This is a simple implementation - in production, use more robust PII detection
        String sanitized = description;

        // Remove potential phone numbers (various formats)
        sanitized = sanitized.replaceAll("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "[REDACTED]");

        // Remove potential SSN patterns
        sanitized = sanitized.replaceAll("\\b\\d{3}[-]?\\d{2}[-]?\\d{4}\\b", "[REDACTED]");

        // Remove potential email addresses
        sanitized = sanitized.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[REDACTED]");

        return sanitized.trim();
    }

    /**
     * Update donation status based on funding allocation and usage
     */
    private void updateDonationStatus(Donation donation) {
        int totalAllocated = situationFundingRepository.sumNightsAllocatedByDonationId(donation.getId());
        int totalUsed = situationFundingRepository.sumNightsUsedByDonationId(donation.getId());
        int nightsFunded = donation.getNightsFunded() != null ? donation.getNightsFunded() : 0;

        if (totalUsed >= nightsFunded) {
            donation.setStatus(Donation.DonationStatus.FULLY_USED);
        } else if (totalUsed > 0) {
            donation.setStatus(Donation.DonationStatus.PARTIALLY_USED);
        } else if (totalAllocated > 0) {
            donation.setStatus(Donation.DonationStatus.ALLOCATED);
        }
        // VERIFIED status is set by the verification process, not here

        donationRepository.save(donation);
    }

    /**
     * Convert SituationFunding to donor-safe view
     */
    private DonorSituationView toDonorSituationView(SituationFunding funding) {
        Situation situation = funding.getSituation();
        return new DonorSituationView(
                situation.getId(),
                situation.getDescription(),
                situation.getCategoryDisplayName(),
                funding.getNightsAllocated(),
                funding.getNightsUsed(),
                funding.getNightsRemaining(),
                funding.getAmountAllocated(),
                funding.getAllocatedAt(),
                situation.getIsActive()
        );
    }

    /**
     * Aggregate multiple fundings for the same situation
     */
    private DonorSituationSummary aggregateSituationFundings(List<SituationFunding> fundings) {
        if (fundings.isEmpty()) {
            return null;
        }

        Situation situation = fundings.get(0).getSituation();

        int totalAllocated = fundings.stream()
                .mapToInt(f -> f.getNightsAllocated() != null ? f.getNightsAllocated() : 0)
                .sum();

        int totalUsed = fundings.stream()
                .mapToInt(f -> f.getNightsUsed() != null ? f.getNightsUsed() : 0)
                .sum();

        BigDecimal totalAmount = fundings.stream()
                .map(f -> f.getAmountAllocated() != null ? f.getAmountAllocated() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DonorSituationSummary(
                situation.getId(),
                situation.getDescription(),
                situation.getCategoryDisplayName(),
                totalAllocated,
                totalUsed,
                totalAllocated - totalUsed,
                totalAmount,
                situation.getIsActive()
        );
    }

    // ========================================
    // DTOs
    // ========================================

    /**
     * Privacy-safe view of a situation for donors
     */
    public record DonorSituationView(
            Long situationId,
            String description,
            String category,
            Integer nightsAllocated,
            Integer nightsUsed,
            Integer nightsRemaining,
            BigDecimal amountAllocated,
            LocalDateTime allocatedAt,
            Boolean isActive
    ) {}

    /**
     * Aggregated summary of a donor's contribution to a situation
     */
    public record DonorSituationSummary(
            Long situationId,
            String description,
            String category,
            int totalNightsAllocated,
            int totalNightsUsed,
            int nightsRemaining,
            BigDecimal totalAmountAllocated,
            Boolean isActive
    ) {}

    /**
     * Situation statistics for a charity
     */
    public record SituationStats(
            long totalSituations,
            long activeSituations,
            List<Object[]> categoryCounts
    ) {}
}
