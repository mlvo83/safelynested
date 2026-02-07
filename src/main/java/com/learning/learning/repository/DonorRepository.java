package com.learning.learning.repository;

import com.learning.learning.entity.Donor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonorRepository extends JpaRepository<Donor, Long> {

    // Find by user
    Optional<Donor> findByUserId(Long userId);

    Optional<Donor> findByUserUsername(String username);

    // Find donors associated with a charity
    @Query("SELECT d FROM Donor d JOIN d.charities c WHERE c.id = :charityId")
    List<Donor> findByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT d FROM Donor d JOIN d.charities c WHERE c.id = :charityId ORDER BY d.createdAt DESC")
    List<Donor> findByCharityIdOrderByCreatedAtDesc(@Param("charityId") Long charityId);

    // Find verified/unverified donors
    List<Donor> findByIsVerified(Boolean isVerified);

    @Query("SELECT d FROM Donor d JOIN d.charities c WHERE c.id = :charityId AND d.isVerified = :isVerified")
    List<Donor> findByCharityIdAndIsVerified(
            @Param("charityId") Long charityId,
            @Param("isVerified") Boolean isVerified
    );

    // Check if user has donor profile
    boolean existsByUserId(Long userId);

    // Count donors
    @Query("SELECT COUNT(d) FROM Donor d JOIN d.charities c WHERE c.id = :charityId")
    Long countByCharityId(@Param("charityId") Long charityId);

    // Search donors by name or email
    @Query("SELECT d FROM Donor d WHERE " +
            "LOWER(d.user.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.user.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.user.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Donor> searchDonors(@Param("searchTerm") String searchTerm);

    @Query("SELECT d FROM Donor d JOIN d.charities c WHERE c.id = :charityId AND (" +
            "LOWER(d.user.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.user.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.user.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Donor> searchDonorsByCharity(
            @Param("charityId") Long charityId,
            @Param("searchTerm") String searchTerm
    );

    // Find all donors ordered by creation date
    List<Donor> findAllByOrderByCreatedAtDesc();

    // Find donor by ID with charities eagerly loaded
    @Query("SELECT d FROM Donor d LEFT JOIN FETCH d.charities WHERE d.id = :id")
    Optional<Donor> findByIdWithCharities(@Param("id") Long id);
}
