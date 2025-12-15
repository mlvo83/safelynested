package com.learning.learning.repository;





import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Long> {

    // ========================================
    // MULTI-TENANT: CHARITY-SPECIFIC QUERIES
    // ========================================

    // Find referrals by charity
    List<Referral> findByCharity(Charity charity);

    List<Referral> findByCharityId(Long charityId);

    List<Referral> findByCharityIdOrderByCreatedAtDesc(Long charityId);

    // Find by charity and status
    List<Referral> findByCharityIdAndStatus(Long charityId, Referral.ReferralStatus status);

    List<Referral> findByCharityIdAndStatusOrderByCreatedAtDesc(Long charityId, Referral.ReferralStatus status);

    // Find by charity and referred by user
    List<Referral> findByCharityIdAndReferredByUser(Long charityId, User referredByUser);

    List<Referral> findByCharityIdAndReferredByUserOrderByCreatedAtDesc(Long charityId, User referredByUser);

    // Multi-tenant safe: Get referral by ID and charity
    @Query("SELECT r FROM Referral r WHERE r.id = :referralId AND r.charity.id = :charityId")
    Optional<Referral> findByIdAndCharityId(@Param("referralId") Long referralId, @Param("charityId") Long charityId);

    // Find by referral number
    Optional<Referral> findByReferralNumber(String referralNumber);

    Optional<Referral> findByReferralNumberAndCharityId(String referralNumber, Long charityId);

    // ========================================
    // TRADITIONAL QUERIES (For Facilitators)
    // ========================================

    // Find by referred by user (facilitator view)
    List<Referral> findByReferredByUser(User referredByUser);

    List<Referral> findByReferredByUserOrderByCreatedAtDesc(User referredByUser);

    // Find by status (all charities)
    List<Referral> findByStatus(Referral.ReferralStatus status);

    List<Referral> findByStatusOrderByCreatedAtDesc(Referral.ReferralStatus status);

    // Find all ordered by created date (for facilitators)
    List<Referral> findAllByOrderByCreatedAtDesc();

    // ========================================
    // SEARCH AND FILTER QUERIES
    // ========================================

    // Search referrals by charity
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND " +
            "(LOWER(r.participantName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.participantEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.participantPhone) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.referralNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Referral> searchReferralsByCharity(@Param("charityId") Long charityId, @Param("searchTerm") String searchTerm);

    // Search all referrals (facilitator)
    @Query("SELECT r FROM Referral r WHERE " +
            "LOWER(r.participantName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.participantEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.participantPhone) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(r.referralNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Referral> searchAllReferrals(@Param("searchTerm") String searchTerm);

    // Find by date range (charity-specific)
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.createdAt BETWEEN :startDate AND :endDate ORDER BY r.createdAt DESC")
    List<Referral> findByCharityIdAndDateRange(
            @Param("charityId") Long charityId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Find by urgency level
    List<Referral> findByCharityIdAndUrgencyLevel(Long charityId, Referral.UrgencyLevel urgencyLevel);

    List<Referral> findByCharityIdAndUrgencyLevelOrderByCreatedAtDesc(Long charityId, Referral.UrgencyLevel urgencyLevel);

    // Find urgent referrals
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.urgencyLevel = 'URGENT' AND r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Referral> findUrgentPendingByCharityId(@Param("charityId") Long charityId);

    // ========================================
    // COUNT AND STATISTICS QUERIES
    // ========================================

    // Count by charity
    Long countByCharityId(Long charityId);

    Long countByCharityIdAndStatus(Long charityId, Referral.ReferralStatus status);

    // Count by status (all charities - for facilitators)
    Long countByStatus(Referral.ReferralStatus status);

    // Status counts for charity
    @Query("SELECT COUNT(r) FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'PENDING'")
    Long countPendingByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'APPROVED'")
    Long countApprovedByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'REJECTED'")
    Long countRejectedByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'COMPLETED'")
    Long countCompletedByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'CANCELLED'")
    Long countCancelledByCharityId(@Param("charityId") Long charityId);

    // Group by status (charity-specific)
    @Query("SELECT r.status, COUNT(r) FROM Referral r WHERE r.charity.id = :charityId GROUP BY r.status")
    List<Object[]> countReferralsByStatusForCharity(@Param("charityId") Long charityId);

    // Group by urgency level
    @Query("SELECT r.urgencyLevel, COUNT(r) FROM Referral r WHERE r.charity.id = :charityId GROUP BY r.urgencyLevel")
    List<Object[]> countReferralsByUrgencyForCharity(@Param("charityId") Long charityId);

    // Recent activity (charity-specific)
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<Referral> findRecentByCharityId(@Param("charityId") Long charityId, @Param("since") LocalDateTime since);

    // ========================================
    // FACILITATOR GLOBAL QUERIES
    // ========================================

    // Count all referrals by status (facilitator)
    @Query("SELECT COUNT(r) FROM Referral r WHERE r.status = 'PENDING'")
    Long countAllPending();

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.status = 'APPROVED'")
    Long countAllApproved();

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.status = 'REJECTED'")
    Long countAllRejected();

    // Group by status (all charities)
    @Query("SELECT r.status, COUNT(r) FROM Referral r GROUP BY r.status")
    List<Object[]> countAllReferralsByStatus();

    // ========================================
    // VALIDATION AND EXISTENCE CHECKS
    // ========================================

    // Check if referral exists for participant (prevent duplicates)
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Referral r " +
            "WHERE r.charity.id = :charityId AND r.participantEmail = :email AND r.status NOT IN ('CANCELLED', 'REJECTED')")
    boolean existsActiveReferralForEmail(@Param("charityId") Long charityId, @Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Referral r " +
            "WHERE r.charity.id = :charityId AND r.participantPhone = :phone AND r.status NOT IN ('CANCELLED', 'REJECTED')")
    boolean existsActiveReferralForPhone(@Param("charityId") Long charityId, @Param("phone") String phone);

    // Find potential duplicate referrals by name
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND " +
            "LOWER(r.participantName) = LOWER(:participantName) AND r.status NOT IN ('CANCELLED', 'REJECTED')")
    List<Referral> findPotentialDuplicatesByName(
            @Param("charityId") Long charityId,
            @Param("participantName") String participantName
    );

    // Check if referral number exists
    boolean existsByReferralNumber(String referralNumber);

    // ========================================
    // DOCUMENT STATUS QUERIES
    // ========================================

    // Find referrals needing documents
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.documentsRequired = true AND r.documentsUploaded = false")
    List<Referral> findReferralsNeedingDocuments(@Param("charityId") Long charityId);

    // Find referrals with documents uploaded
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.documentsUploaded = true")
    List<Referral> findReferralsWithDocuments(@Param("charityId") Long charityId);

    // ========================================
    // APPROVAL QUERIES
    // ========================================

    // Find referrals approved by specific user
    List<Referral> findByApprovedByUser(User approvedByUser);

    List<Referral> findByApprovedByUserOrderByApprovedAtDesc(User approvedByUser);

    // Find recently approved
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.approvedAt >= :since ORDER BY r.approvedAt DESC")
    List<Referral> findRecentlyApprovedByCharityId(@Param("charityId") Long charityId, @Param("since") LocalDateTime since);

    // ========================================
    // DASHBOARD QUERIES
    // ========================================

    // Get recent referrals for dashboard (charity-specific)
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId ORDER BY r.createdAt DESC")
    List<Referral> findTop10ByCharityIdOrderByCreatedAtDesc(@Param("charityId") Long charityId);

    // Get pending action items (pending but not reviewed)
    @Query("SELECT r FROM Referral r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Referral> findPendingReviewReferrals();

    // Get pending referrals for a charity
    @Query("SELECT r FROM Referral r WHERE r.charity.id = :charityId AND r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Referral> findPendingByCharityId(@Param("charityId") Long charityId);
}

