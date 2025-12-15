package com.learning.learning.repository;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.CharityLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharityLocationRepository extends JpaRepository<CharityLocation, Long> {

    // ========================================
    // CHARITY-SPECIFIC QUERIES (for Charity Partner)
    // ========================================

    // Multi-tenant: Find locations by charity
    List<CharityLocation> findByCharity(Charity charity);

    List<CharityLocation> findByCharityId(Long charityId);

    List<CharityLocation> findByCharityIdOrderByLocationNameAsc(Long charityId);

    // Find active/inactive locations for a charity
    List<CharityLocation> findByCharityIdAndIsActive(Long charityId, Boolean isActive);

    List<CharityLocation> findByCharityIdAndIsActiveTrue(Long charityId);

    List<CharityLocation> findByCharityIdAndIsActiveFalse(Long charityId);

    // Find by city and state for a charity
    List<CharityLocation> findByCharityIdAndCity(Long charityId, String city);

    List<CharityLocation> findByCharityIdAndState(Long charityId, String state);

    List<CharityLocation> findByCharityIdAndCityAndState(Long charityId, String city, String state);

    /*List<CharityLocation> findByCharityIdAndIsActiveTrueOrderByLocationNameAsc(Long charityId);*/
    // Search locations within a charity
    @Query("SELECT cl FROM CharityLocation cl WHERE cl.charity.id = :charityId AND " +
            "(LOWER(cl.locationName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.address) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.state) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<CharityLocation> searchLocations(
            @Param("charityId") Long charityId,
            @Param("searchTerm") String searchTerm
    );

    // Count locations for a charity
    Long countByCharityId(Long charityId);

    Long countByCharityIdAndIsActive(Long charityId, Boolean isActive);

    // Check if location name exists for charity
    boolean existsByCharityIdAndLocationName(Long charityId, String locationName);

    Optional<CharityLocation> findByCharityIdAndLocationName(Long charityId, String locationName);

    // Find locations by zip code for a charity
    List<CharityLocation> findByCharityIdAndZipCode(Long charityId, String zipCode);

    // ========================================
    // CROSS-CHARITY QUERIES (for Location Admin)
    // ========================================

    // Find all locations (no charity filter)
    List<CharityLocation> findByZipCode(String zipCode);

    // Find all active locations by state
    @Query("SELECT cl FROM CharityLocation cl WHERE cl.state = :state AND cl.isActive = true")
    List<CharityLocation> findActiveLocationsByState(@Param("state") String state);

    // Find all active locations across all charities (ordered by charity then name)
    @Query("SELECT cl FROM CharityLocation cl WHERE cl.isActive = true ORDER BY cl.charity.charityName, cl.locationName")
    List<CharityLocation> findAllActiveLocationsOrderByCharityAndName();

    // Find ALL locations ordered by charity and name (for Location Admin)
    @Query("SELECT cl FROM CharityLocation cl ORDER BY cl.charity.charityName, cl.locationName")
    List<CharityLocation> findAllOrderByCharityAndName();

    // Search across ALL locations (for Location Admin)
    @Query("SELECT cl FROM CharityLocation cl WHERE " +
            "LOWER(cl.locationName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.address) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.state) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(cl.zipCode) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY cl.charity.charityName, cl.locationName")
    List<CharityLocation> searchAllLocations(@Param("searchTerm") String searchTerm);

    // Count all active/inactive
    Long countByIsActive(Boolean isActive);

    // ========================================
    // STATISTICS
    // ========================================

    @Query("SELECT cl.state, COUNT(cl) FROM CharityLocation cl WHERE cl.charity.id = :charityId GROUP BY cl.state")
    List<Object[]> countLocationsByStateForCharity(@Param("charityId") Long charityId);

    @Query("SELECT cl.city, COUNT(cl) FROM CharityLocation cl WHERE cl.charity.id = :charityId GROUP BY cl.city")
    List<Object[]> countLocationsByCityForCharity(@Param("charityId") Long charityId);

    // ========================================
    // CAPACITY QUERIES
    // ========================================

    @Query("SELECT cl FROM CharityLocation cl WHERE cl.charity.id = :charityId AND cl.capacity IS NOT NULL AND cl.capacity > 0")
    List<CharityLocation> findLocationsWithCapacity(@Param("charityId") Long charityId);

    @Query("SELECT cl FROM CharityLocation cl WHERE cl.charity.id = :charityId AND cl.capacity >= :minCapacity")
    List<CharityLocation> findLocationsByMinCapacity(
            @Param("charityId") Long charityId,
            @Param("minCapacity") Integer minCapacity
    );

    // Find locations with capacity set (for booking)
    @Query("SELECT cl FROM CharityLocation cl WHERE cl.isActive = true AND cl.capacity IS NOT NULL AND cl.capacity > 0")
    List<CharityLocation> findActiveLocationsWithCapacity();

    // Find active locations in specific zip codes
    @Query("SELECT cl FROM CharityLocation cl WHERE cl.isActive = true AND cl.zipCode IN :zipCodes")
    List<CharityLocation> findActiveLocationsInZipCodes(@Param("zipCodes") List<String> zipCodes);
}