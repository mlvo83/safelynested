package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CharityService {

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private UserRepository userRepository;

    // ========================================
    // MULTI-TENANT: GET CHARITY FOR CURRENT USER
    // ========================================

    /**
     * Get charity for current user (multi-tenant)
     */
    public Charity getCharityForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (user.getCharity() == null) {
            throw new RuntimeException("User is not associated with a charity");
        }

        return user.getCharity();
    }

    /**
     * Get charity ID for current user
     */
    public Long getCharityIdForUser(String username) {
        return getCharityForUser(username).getId();
    }

    /**
     * Check if user belongs to a specific charity
     */
    public boolean userBelongsToCharity(String username, Long charityId) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCharity() == null) {
            return false;
        }
        return user.getCharity().getId().equals(charityId);
    }

    // ========================================
    // CHARITY CRUD OPERATIONS
    // ========================================

    /**
     * Get charity by ID (with access control)
     */
    public Charity getCharityById(Long charityId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        // Facilitators and admins can see all charities
        if (user.isFacilitator() || user.isAdmin()) {
            return charity;
        }

        // Charity partners can only see their own charity
        if (user.isCharityPartner() && user.belongsToCharity(charityId)) {
            return charity;
        }

        throw new RuntimeException("Access denied: User cannot access this charity");
    }

    /**
     * Get charity by ID (no access control - for internal use)
     */
    public Optional<Charity> findById(Long charityId) {
        return charityRepository.findById(charityId);
    }

    /**
     * Get all active charities
     */
    public List<Charity> getAllActiveCharities() {
        return charityRepository.findByIsActiveTrue();
    }

    /**
     * Get all active and verified charities
     */
    public List<Charity> getAllActiveVerifiedCharities() {
        return charityRepository.findByIsActiveTrueAndIsVerifiedTrue();
    }

    /**
     * Get all charities (for admin/facilitator)
     */
    public List<Charity> getAllCharities() {
        return charityRepository.findAll();
    }

    /**
     * Search charities
     */
    public List<Charity> searchCharities(String searchTerm) {
        return charityRepository.searchCharities(searchTerm);
    }

    /**
     * Create new charity
     */
    @Transactional
    public Charity createCharity(Charity charity) {
        // Check if charity name already exists
        if (charityRepository.existsByCharityName(charity.getCharityName())) {
            throw new RuntimeException("Charity name already exists: " + charity.getCharityName());
        }

        // Check if EIN already exists (if provided)
        if (charity.getEinTaxId() != null && !charity.getEinTaxId().isEmpty()) {
            if (charityRepository.existsByEinTaxId(charity.getEinTaxId())) {
                throw new RuntimeException("EIN/Tax ID already exists: " + charity.getEinTaxId());
            }
        }

        return charityRepository.save(charity);
    }

    /**
     * Update charity
     */
    @Transactional
    public Charity updateCharity(Long charityId, Charity updatedCharity, String username) {
        Charity existingCharity = getCharityById(charityId, username);

        // Update fields
        if (updatedCharity.getCharityName() != null) {
            // Check if new name conflicts with another charity
            Optional<Charity> existingByName = charityRepository.findByCharityName(updatedCharity.getCharityName());
            if (existingByName.isPresent() && !existingByName.get().getId().equals(charityId)) {
                throw new RuntimeException("Charity name already exists");
            }
            existingCharity.setCharityName(updatedCharity.getCharityName());
        }

        if (updatedCharity.getOrganizationType() != null) {
            existingCharity.setOrganizationType(updatedCharity.getOrganizationType());
        }
        if (updatedCharity.getContactName() != null) {
            existingCharity.setContactName(updatedCharity.getContactName());
        }
        if (updatedCharity.getContactEmail() != null) {
            existingCharity.setContactEmail(updatedCharity.getContactEmail());
        }
        if (updatedCharity.getContactPhone() != null) {
            existingCharity.setContactPhone(updatedCharity.getContactPhone());
        }
        if (updatedCharity.getAddress() != null) {
            existingCharity.setAddress(updatedCharity.getAddress());
        }
        if (updatedCharity.getCity() != null) {
            existingCharity.setCity(updatedCharity.getCity());
        }
        if (updatedCharity.getState() != null) {
            existingCharity.setState(updatedCharity.getState());
        }
        if (updatedCharity.getZipCode() != null) {
            existingCharity.setZipCode(updatedCharity.getZipCode());
        }
        if (updatedCharity.getDescription() != null) {
            existingCharity.setDescription(updatedCharity.getDescription());
        }
        if (updatedCharity.getMissionStatement() != null) {
            existingCharity.setMissionStatement(updatedCharity.getMissionStatement());
        }
        if (updatedCharity.getAllowedZipCodes() != null) {
            existingCharity.setAllowedZipCodes(updatedCharity.getAllowedZipCodes());
        }
        if (updatedCharity.getMaxReferralsPerMonth() != null) {
            existingCharity.setMaxReferralsPerMonth(updatedCharity.getMaxReferralsPerMonth());
        }

        return charityRepository.save(existingCharity);
    }

    /**
     * Deactivate charity (soft delete)
     */
    @Transactional
    public void deactivateCharity(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));
        charity.setIsActive(false);
        charityRepository.save(charity);
    }

    /**
     * Activate charity
     */
    @Transactional
    public void activateCharity(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));
        charity.setIsActive(true);
        charityRepository.save(charity);
    }

    // ========================================
    // VERIFICATION
    // ========================================

    /**
     * Verify charity (admin/facilitator only)
     */
    @Transactional
    public Charity verifyCharity(Long charityId, String verifierUsername) {
        User verifier = userRepository.findByUsername(verifierUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!verifier.isFacilitator() && !verifier.isAdmin()) {
            throw new RuntimeException("Only facilitators and admins can verify charities");
        }

        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        charity.setIsVerified(true);
        charity.setVerifiedAt(LocalDateTime.now());
        charity.setVerifiedBy(verifier);

        return charityRepository.save(charity);
    }

    /**
     * Unverify charity
     */
    @Transactional
    public Charity unverifyCharity(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        charity.setIsVerified(false);
        charity.setVerifiedAt(null);
        charity.setVerifiedBy(null);

        return charityRepository.save(charity);
    }

    /**
     * Get unverified charities
     */
    public List<Charity> getUnverifiedCharities() {
        return charityRepository.findUnverifiedCharities();
    }

    // ========================================
    // STATISTICS AND DASHBOARD
    // ========================================

    /**
     * Get charity dashboard statistics
     */
    public Map<String, Object> getCharityDashboardStats(Long charityId) {
        Map<String, Object> stats = new HashMap<>();

        // Referral counts by status
        stats.put("totalReferrals", referralRepository.countByCharityId(charityId));
        stats.put("pendingReferrals", referralRepository.countPendingByCharityId(charityId));
        stats.put("approvedReferrals", referralRepository.countApprovedByCharityId(charityId));
        stats.put("rejectedReferrals", referralRepository.countRejectedByCharityId(charityId));
        stats.put("completedReferrals", referralRepository.countCompletedByCharityId(charityId));

        // Recent referrals
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<Referral> recentReferrals = referralRepository.findRecentByCharityId(charityId, oneWeekAgo);
        stats.put("recentReferralsCount", recentReferrals.size());

        return stats;
    }

    /**
     * Get admin dashboard statistics (all charities)
     */
    public Map<String, Object> getAdminDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalCharities", charityRepository.count());
        stats.put("activeCharities", charityRepository.countActiveCharities());
        stats.put("verifiedCharities", charityRepository.countVerifiedCharities());
        stats.put("pendingVerification", charityRepository.findUnverifiedCharities().size());

        // Referral stats across all charities
        stats.put("totalPendingReferrals", referralRepository.countAllPending());
        stats.put("totalApprovedReferrals", referralRepository.countAllApproved());

        return stats;
    }

    // ========================================
    // REFERRAL LIMIT CHECKS
    // ========================================

    /**
     * Check if charity can create more referrals this month
     */
    public boolean canCreateReferral(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        if (charity.getMaxReferralsPerMonth() == null) {
            return true; // No limit set
        }

        // Count referrals created this month
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);

        List<Referral> thisMonthReferrals = referralRepository.findByCharityIdAndDateRange(
                charityId, startOfMonth, endOfMonth);

        return thisMonthReferrals.size() < charity.getMaxReferralsPerMonth();
    }

    /**
     * Get remaining referrals for this month
     */
    public int getRemainingReferralsThisMonth(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        if (charity.getMaxReferralsPerMonth() == null) {
            return Integer.MAX_VALUE; // No limit
        }

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);

        List<Referral> thisMonthReferrals = referralRepository.findByCharityIdAndDateRange(
                charityId, startOfMonth, endOfMonth);

        return Math.max(0, charity.getMaxReferralsPerMonth() - thisMonthReferrals.size());
    }

    // ========================================
    // ZIP CODE VALIDATION
    // ========================================

    /**
     * Check if zip code is allowed for charity
     */
    public boolean isZipCodeAllowed(Long charityId, String zipCode) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        if (charity.getAllowedZipCodes() == null || charity.getAllowedZipCodes().isEmpty()) {
            return true; // No restrictions
        }

        String[] allowedZips = charity.getAllowedZipCodes().split(",");
        for (String allowedZip : allowedZips) {
            if (allowedZip.trim().equals(zipCode.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get list of allowed zip codes for charity
     */
    public List<String> getAllowedZipCodes(Long charityId) {
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found"));

        if (charity.getAllowedZipCodes() == null || charity.getAllowedZipCodes().isEmpty()) {
            return List.of(); // Empty list means all zip codes allowed
        }

        return List.of(charity.getAllowedZipCodes().split(","))
                .stream()
                .map(String::trim)
                .toList();
    }
}
