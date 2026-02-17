package com.learning.learning.repository;

import com.learning.learning.entity.DonorSetupRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonorSetupRequestRepository extends JpaRepository<DonorSetupRequest, Long> {

    Optional<DonorSetupRequest> findByRequestNumber(String requestNumber);

    // Charity-scoped queries
    List<DonorSetupRequest> findByCharityIdOrderByCreatedAtDesc(Long charityId);

    List<DonorSetupRequest> findByCharityIdAndStatusOrderByCreatedAtDesc(
            Long charityId, DonorSetupRequest.RequestStatus status);

    // Admin queries (pending first, then by date)
    List<DonorSetupRequest> findByStatusOrderByCreatedAtAsc(DonorSetupRequest.RequestStatus status);

    @Query("SELECT r FROM DonorSetupRequest r ORDER BY " +
            "CASE r.status WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 WHEN 'REJECTED' THEN 2 END, " +
            "r.createdAt DESC")
    List<DonorSetupRequest> findAllOrderByStatusAndDate();

    // Counts
    long countByStatus(DonorSetupRequest.RequestStatus status);

    long countByCharityIdAndStatus(Long charityId, DonorSetupRequest.RequestStatus status);

    // Duplicate checks
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM DonorSetupRequest r " +
            "WHERE r.existingDonor.id = :donorId AND r.charity.id = :charityId AND r.status = 'PENDING'")
    boolean existsPendingForDonorAndCharity(@Param("donorId") Long donorId, @Param("charityId") Long charityId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM DonorSetupRequest r " +
            "WHERE r.email = :email AND r.charity.id = :charityId AND r.status = 'PENDING'")
    boolean existsPendingForEmailAndCharity(@Param("email") String email, @Param("charityId") Long charityId);
}
