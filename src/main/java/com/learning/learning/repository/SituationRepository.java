package com.learning.learning.repository;

import com.learning.learning.entity.Situation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SituationRepository extends JpaRepository<Situation, Long> {

    // Find by charity
    List<Situation> findByCharityId(Long charityId);

    List<Situation> findByCharityIdOrderByCreatedAtDesc(Long charityId);

    List<Situation> findByCharityIdAndIsActiveTrue(Long charityId);

    // Find by category
    List<Situation> findByCategory(Situation.SituationCategory category);

    List<Situation> findByCharityIdAndCategory(Long charityId, Situation.SituationCategory category);

    // Find by referral (internal use only - never expose to donors)
    Optional<Situation> findByReferralId(Long referralId);

    // Find active situations
    List<Situation> findByIsActiveTrue();

    List<Situation> findByIsActiveTrueOrderByCreatedAtDesc();

    // Count situations
    Long countByCharityId(Long charityId);

    Long countByCharityIdAndIsActiveTrue(Long charityId);

    Long countByCharityIdAndCategory(Long charityId, Situation.SituationCategory category);

    // Search situations by description
    @Query("SELECT s FROM Situation s WHERE s.charity.id = :charityId AND " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Situation> searchByCharityId(
            @Param("charityId") Long charityId,
            @Param("searchTerm") String searchTerm
    );

    // Find situations with available funding (nights remaining > 0)
    @Query("SELECT DISTINCT s FROM Situation s JOIN s.fundings f " +
            "WHERE s.charity.id = :charityId AND s.isActive = true " +
            "GROUP BY s HAVING SUM(f.nightsAllocated - f.nightsUsed) > 0")
    List<Situation> findSituationsWithAvailableFunding(@Param("charityId") Long charityId);

    // Statistics by category
    @Query("SELECT s.category, COUNT(s) FROM Situation s WHERE s.charity.id = :charityId GROUP BY s.category")
    List<Object[]> countByCategoryForCharity(@Param("charityId") Long charityId);
}
