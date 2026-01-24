package com.learning.learning.repository;

import com.learning.learning.entity.SituationFunding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SituationFundingRepository extends JpaRepository<SituationFunding, Long> {

    // Find by donation
    List<SituationFunding> findByDonationId(Long donationId);

    List<SituationFunding> findByDonationIdOrderByAllocatedAtDesc(Long donationId);

    // Find by situation
    List<SituationFunding> findBySituationId(Long situationId);

    List<SituationFunding> findBySituationIdOrderByAllocatedAtDesc(Long situationId);

    // Find by donor (through donation) - for donor dashboard
    @Query("SELECT sf FROM SituationFunding sf WHERE sf.donation.donor.id = :donorId ORDER BY sf.allocatedAt DESC")
    List<SituationFunding> findByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT sf FROM SituationFunding sf " +
            "JOIN FETCH sf.situation s " +
            "WHERE sf.donation.donor.id = :donorId " +
            "ORDER BY sf.allocatedAt DESC")
    List<SituationFunding> findByDonorIdWithSituation(@Param("donorId") Long donorId);

    // Find by charity (through situation)
    @Query("SELECT sf FROM SituationFunding sf WHERE sf.situation.charity.id = :charityId ORDER BY sf.allocatedAt DESC")
    List<SituationFunding> findByCharityId(@Param("charityId") Long charityId);

    // Aggregations for donor
    @Query("SELECT COALESCE(SUM(sf.nightsUsed), 0) FROM SituationFunding sf WHERE sf.donation.donor.id = :donorId")
    Integer sumNightsUsedByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT COALESCE(SUM(sf.nightsAllocated), 0) FROM SituationFunding sf WHERE sf.donation.donor.id = :donorId")
    Integer sumNightsAllocatedByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT COALESCE(SUM(sf.amountAllocated), 0) FROM SituationFunding sf WHERE sf.donation.donor.id = :donorId")
    BigDecimal sumAmountAllocatedByDonorId(@Param("donorId") Long donorId);

    // Aggregations for situation
    @Query("SELECT COALESCE(SUM(sf.nightsUsed), 0) FROM SituationFunding sf WHERE sf.situation.id = :situationId")
    Integer sumNightsUsedBySituationId(@Param("situationId") Long situationId);

    @Query("SELECT COALESCE(SUM(sf.nightsAllocated), 0) FROM SituationFunding sf WHERE sf.situation.id = :situationId")
    Integer sumNightsAllocatedBySituationId(@Param("situationId") Long situationId);

    // Aggregations for donation
    @Query("SELECT COALESCE(SUM(sf.nightsUsed), 0) FROM SituationFunding sf WHERE sf.donation.id = :donationId")
    Integer sumNightsUsedByDonationId(@Param("donationId") Long donationId);

    @Query("SELECT COALESCE(SUM(sf.nightsAllocated), 0) FROM SituationFunding sf WHERE sf.donation.id = :donationId")
    Integer sumNightsAllocatedByDonationId(@Param("donationId") Long donationId);

    // Count fundings
    Long countByDonationId(Long donationId);

    Long countBySituationId(Long situationId);

    @Query("SELECT COUNT(sf) FROM SituationFunding sf WHERE sf.donation.donor.id = :donorId")
    Long countByDonorId(@Param("donorId") Long donorId);

    // Find fundings with nights remaining
    @Query("SELECT sf FROM SituationFunding sf WHERE sf.nightsAllocated > sf.nightsUsed")
    List<SituationFunding> findWithNightsRemaining();

    @Query("SELECT sf FROM SituationFunding sf WHERE sf.donation.id = :donationId AND sf.nightsAllocated > sf.nightsUsed")
    List<SituationFunding> findWithNightsRemainingByDonationId(@Param("donationId") Long donationId);
}
