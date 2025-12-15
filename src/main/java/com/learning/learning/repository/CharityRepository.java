package com.learning.learning.repository;




import com.learning.learning.entity.Charity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharityRepository extends JpaRepository<Charity, Long> {

    // Basic finder methods
    Optional<Charity> findByCharityName(String charityName);

    Optional<Charity> findByEinTaxId(String einTaxId);

    // Active and verified charities
    List<Charity> findByIsActiveTrue();

    List<Charity> findByIsVerifiedTrue();

    List<Charity> findByIsActiveTrueAndIsVerifiedTrue();

    // Location-based queries
    List<Charity> findByCity(String city);

    List<Charity> findByState(String state);

    List<Charity> findByCityAndState(String city, String state);

    // Existence checks
    boolean existsByCharityName(String charityName);

    boolean existsByEinTaxId(String einTaxId);

    // Custom queries with sorting
    @Query("SELECT c FROM Charity c WHERE c.isActive = true ORDER BY c.charityName ASC")
    List<Charity> findAllActiveOrderByName();

    @Query("SELECT c FROM Charity c WHERE c.isActive = true AND c.isVerified = true ORDER BY c.charityName ASC")
    List<Charity> findAllActiveVerifiedOrderByName();

    // Search functionality
    @Query("SELECT c FROM Charity c WHERE " +
            "LOWER(c.charityName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.state) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Charity> searchCharities(@Param("searchTerm") String searchTerm);

    @Query("SELECT c FROM Charity c WHERE c.isActive = true AND (" +
            "LOWER(c.charityName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.state) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Charity> searchActiveCharities(@Param("searchTerm") String searchTerm);

    // Statistics and counts
    @Query("SELECT COUNT(c) FROM Charity c WHERE c.isActive = true")
    Long countActiveCharities();

    @Query("SELECT COUNT(c) FROM Charity c WHERE c.isVerified = true")
    Long countVerifiedCharities();

    @Query("SELECT COUNT(c) FROM Charity c WHERE c.isActive = true AND c.isVerified = true")
    Long countActiveVerifiedCharities();

    // Find charities needing verification
    @Query("SELECT c FROM Charity c WHERE c.isActive = true AND c.isVerified = false ORDER BY c.createdAt ASC")
    List<Charity> findUnverifiedCharities();
}
