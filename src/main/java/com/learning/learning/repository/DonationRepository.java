package com.learning.learning.repository;

import com.learning.learning.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long> {

    // Find by donor
    List<Donation> findByDonorId(Long donorId);

    List<Donation> findByDonorIdOrderByDonatedAtDesc(Long donorId);

    // Find by charity
    List<Donation> findByCharityId(Long charityId);

    List<Donation> findByCharityIdOrderByDonatedAtDesc(Long charityId);

    // Find by donor and charity
    List<Donation> findByDonorIdAndCharityId(Long donorId, Long charityId);

    List<Donation> findByDonorIdAndCharityIdOrderByDonatedAtDesc(Long donorId, Long charityId);

    // Find by status
    List<Donation> findByStatus(Donation.DonationStatus status);

    List<Donation> findByCharityIdAndStatus(Long charityId, Donation.DonationStatus status);

    List<Donation> findByDonorIdAndStatus(Long donorId, Donation.DonationStatus status);

    // Find by verification status
    List<Donation> findByVerificationStatus(Donation.VerificationStatus status);

    List<Donation> findByCharityIdAndVerificationStatus(Long charityId, Donation.VerificationStatus status);

    // Find by date range
    @Query("SELECT d FROM Donation d WHERE d.donatedAt BETWEEN :startDate AND :endDate ORDER BY d.donatedAt DESC")
    List<Donation> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT d FROM Donation d WHERE d.charity.id = :charityId AND d.donatedAt BETWEEN :startDate AND :endDate ORDER BY d.donatedAt DESC")
    List<Donation> findByCharityIdAndDateRange(
            @Param("charityId") Long charityId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Aggregations for donor
    @Query("SELECT SUM(d.grossAmount) FROM Donation d WHERE d.donor.id = :donorId")
    BigDecimal sumGrossAmountByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT SUM(d.netAmount) FROM Donation d WHERE d.donor.id = :donorId")
    BigDecimal sumNetAmountByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT SUM(d.nightsFunded) FROM Donation d WHERE d.donor.id = :donorId")
    Integer sumNightsFundedByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT SUM(d.platformFee) FROM Donation d WHERE d.donor.id = :donorId")
    BigDecimal sumPlatformFeeByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT SUM(d.facilitatorFee) FROM Donation d WHERE d.donor.id = :donorId")
    BigDecimal sumFacilitatorFeeByDonorId(@Param("donorId") Long donorId);

    // Aggregations for charity
    @Query("SELECT SUM(d.grossAmount) FROM Donation d WHERE d.charity.id = :charityId")
    BigDecimal sumGrossAmountByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT SUM(d.netAmount) FROM Donation d WHERE d.charity.id = :charityId")
    BigDecimal sumNetAmountByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT SUM(d.nightsFunded) FROM Donation d WHERE d.charity.id = :charityId")
    Integer sumNightsFundedByCharityId(@Param("charityId") Long charityId);

    // Count donations
    Long countByDonorId(Long donorId);

    Long countByCharityId(Long charityId);

    Long countByDonorIdAndCharityId(Long donorId, Long charityId);

    Long countByStatus(Donation.DonationStatus status);

    Long countByCharityIdAndStatus(Long charityId, Donation.DonationStatus status);

    // Find recent donations
    @Query("SELECT d FROM Donation d WHERE d.donor.id = :donorId ORDER BY d.donatedAt DESC")
    List<Donation> findRecentByDonorId(@Param("donorId") Long donorId);

    @Query("SELECT d FROM Donation d WHERE d.charity.id = :charityId ORDER BY d.donatedAt DESC")
    List<Donation> findRecentByCharityId(@Param("charityId") Long charityId);

    // Find donations pending verification
    @Query("SELECT d FROM Donation d WHERE d.verificationStatus = 'PENDING' ORDER BY d.donatedAt ASC")
    List<Donation> findPendingVerification();

    @Query("SELECT d FROM Donation d WHERE d.charity.id = :charityId AND d.verificationStatus = 'PENDING' ORDER BY d.donatedAt ASC")
    List<Donation> findPendingVerificationByCharityId(@Param("charityId") Long charityId);
}
