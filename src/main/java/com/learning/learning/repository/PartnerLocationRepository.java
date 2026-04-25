package com.learning.learning.repository;

import com.learning.learning.entity.PartnerLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerLocationRepository extends JpaRepository<PartnerLocation, Long> {

    List<PartnerLocation> findByLocationPartnerId(Long locationPartnerId);

    List<PartnerLocation> findByLocationPartnerIdAndIsActiveTrue(Long locationPartnerId);

    List<PartnerLocation> findByZipCodeAndIsActiveTrue(String zipCode);

    /**
     * Active partner locations linked (via partner_location_charities)
     * to the given charity. Used by the charity booking form to surface
     * partner properties as bookable options.
     */
    @Query("SELECT pl FROM PartnerLocation pl " +
            "JOIN PartnerLocationCharity plc ON plc.partnerLocation = pl " +
            "WHERE plc.charity.id = :charityId AND pl.isActive = true " +
            "ORDER BY pl.name ASC")
    List<PartnerLocation> findActiveLinkedToCharity(@Param("charityId") Long charityId);

    /**
     * Maintenance queue — active partner properties that have not yet been
     * linked to ANY charity. These are invisible to every charity's booking
     * dropdown until admin links at least one charity.
     * Returned oldest-first so admins work the queue in FIFO order.
     */
    @Query("SELECT pl FROM PartnerLocation pl " +
            "WHERE pl.isActive = true " +
            "  AND NOT EXISTS (" +
            "        SELECT 1 FROM PartnerLocationCharity plc WHERE plc.partnerLocation = pl" +
            "      ) " +
            "ORDER BY pl.createdAt ASC")
    List<PartnerLocation> findActiveWithNoCharityLinks();

    @Query("SELECT COUNT(pl) FROM PartnerLocation pl " +
            "WHERE pl.isActive = true " +
            "  AND NOT EXISTS (" +
            "        SELECT 1 FROM PartnerLocationCharity plc WHERE plc.partnerLocation = pl" +
            "      )")
    long countActiveWithNoCharityLinks();
}
