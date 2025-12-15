package com.learning.learning.repository;

import com.learning.learning.entity.ReferralInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralInviteRepository extends JpaRepository<ReferralInvite, Long> {

    // ========================================================================
    // CRITICAL FIX: Changed from implicit INNER JOIN to explicit LEFT JOIN
    // This ensures invites without referrals (referral_id = NULL) are included
    // ========================================================================

    /**
     * Find all invites for a charity (both direct and via referrals)
     * FIXED: Uses LEFT JOIN to include invites without referrals
     */
    @Query("SELECT i FROM ReferralInvite i LEFT JOIN i.referral r " +
            "WHERE i.charity.id = :charityId " +
            "OR (r IS NOT NULL AND r.charity.id = :charityId) " +
            "ORDER BY i.createdAt DESC")
    List<ReferralInvite> findAllByCharityId(@Param("charityId") Long charityId);

    /**
     * Find completed invites with selected locations
     */
    @Query("SELECT i FROM ReferralInvite i LEFT JOIN i.referral r " +
            "WHERE (i.charity.id = :charityId OR (r IS NOT NULL AND r.charity.id = :charityId)) " +
            "AND i.status = 'COMPLETED' AND i.selectedLocation IS NOT NULL " +
            "ORDER BY i.completedAt DESC")
    List<ReferralInvite> findCompletedInvitesWithLocationByCharityId(@Param("charityId") Long charityId);

    /**
     * Find invites without referrals for a charity
     */
    @Query("SELECT i FROM ReferralInvite i " +
            "WHERE i.charity.id = :charityId AND i.referral IS NULL " +
            "ORDER BY i.createdAt DESC")
    List<ReferralInvite> findInvitesWithoutReferralByCharityId(@Param("charityId") Long charityId);

    /**
     * Count all invites for a charity
     */
    @Query("SELECT COUNT(i) FROM ReferralInvite i LEFT JOIN i.referral r " +
            "WHERE i.charity.id = :charityId " +
            "OR (r IS NOT NULL AND r.charity.id = :charityId)")
    long countByCharityId(@Param("charityId") Long charityId);

    /**
     * Find invites by charity and status
     */
    @Query("SELECT i FROM ReferralInvite i LEFT JOIN i.referral r " +
            "WHERE (i.charity.id = :charityId OR (r IS NOT NULL AND r.charity.id = :charityId)) " +
            "AND i.status = :status " +
            "ORDER BY i.createdAt DESC")
    List<ReferralInvite> findByCharityIdAndStatus(
            @Param("charityId") Long charityId,
            @Param("status") ReferralInvite.InviteStatus status
    );

    // Simple query methods (don't need @Query)
    Optional<ReferralInvite> findByInviteToken(String inviteToken);

    List<ReferralInvite> findByReferralId(Long referralId);

    List<ReferralInvite> findByReferralIdOrderByCreatedAtDesc(Long referralId);

    List<ReferralInvite> findByStatus(ReferralInvite.InviteStatus status);

    List<ReferralInvite> findByReferralIdAndStatus(Long referralId, ReferralInvite.InviteStatus status);

    Optional<ReferralInvite> findByRecipientEmail(String email);

    Optional<ReferralInvite> findByRecipientPhone(String phone);
}