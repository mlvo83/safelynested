package com.learning.learning.repository;

import com.learning.learning.entity.NightlyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NightlyRateRepository extends JpaRepository<NightlyRate, Long> {

    // Find by location
    List<NightlyRate> findByLocationId(Long locationId);

    List<NightlyRate> findByLocationIdOrderByEffectiveDateDesc(Long locationId);

    // Find current active rate for a location
    @Query("SELECT nr FROM NightlyRate nr WHERE nr.location.id = :locationId " +
            "AND nr.effectiveDate <= :date " +
            "AND (nr.endDate IS NULL OR nr.endDate >= :date) " +
            "ORDER BY nr.effectiveDate DESC")
    List<NightlyRate> findActiveRatesForLocationOnDate(
            @Param("locationId") Long locationId,
            @Param("date") LocalDate date
    );

    default Optional<NightlyRate> findCurrentRateForLocation(Long locationId) {
        List<NightlyRate> rates = findActiveRatesForLocationOnDate(locationId, LocalDate.now());
        return rates.isEmpty() ? Optional.empty() : Optional.of(rates.get(0));
    }

    // Find all active rates for a charity's locations
    @Query("SELECT nr FROM NightlyRate nr WHERE nr.location.charity.id = :charityId " +
            "AND nr.effectiveDate <= :date " +
            "AND (nr.endDate IS NULL OR nr.endDate >= :date)")
    List<NightlyRate> findActiveRatesForCharityOnDate(
            @Param("charityId") Long charityId,
            @Param("date") LocalDate date
    );

    default List<NightlyRate> findCurrentRatesForCharity(Long charityId) {
        return findActiveRatesForCharityOnDate(charityId, LocalDate.now());
    }

    // Calculate average rate for a charity
    @Query("SELECT AVG(nr.rate) FROM NightlyRate nr WHERE nr.location.charity.id = :charityId " +
            "AND nr.effectiveDate <= :date " +
            "AND (nr.endDate IS NULL OR nr.endDate >= :date)")
    BigDecimal getAverageRateForCharityOnDate(
            @Param("charityId") Long charityId,
            @Param("date") LocalDate date
    );

    default BigDecimal getAverageActiveRateForCharity(Long charityId) {
        return getAverageRateForCharityOnDate(charityId, LocalDate.now());
    }

    // Find rates by charity
    @Query("SELECT nr FROM NightlyRate nr WHERE nr.location.charity.id = :charityId ORDER BY nr.effectiveDate DESC")
    List<NightlyRate> findByCharityId(@Param("charityId") Long charityId);

    // Count rates
    Long countByLocationId(Long locationId);

    @Query("SELECT COUNT(nr) FROM NightlyRate nr WHERE nr.location.charity.id = :charityId")
    Long countByCharityId(@Param("charityId") Long charityId);

    // Check if location has any rates
    boolean existsByLocationId(Long locationId);

    // Find rates ending soon (for admin alerts)
    @Query("SELECT nr FROM NightlyRate nr WHERE nr.endDate IS NOT NULL " +
            "AND nr.endDate BETWEEN :startDate AND :endDate " +
            "ORDER BY nr.endDate ASC")
    List<NightlyRate> findRatesEndingSoon(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
