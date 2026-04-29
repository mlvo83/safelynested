package com.learning.learning.repository;

import com.learning.learning.entity.StayPartnerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StayPartnerApplicationRepository extends JpaRepository<StayPartnerApplication, Long> {

    Optional<StayPartnerApplication> findByApplicationNumber(String applicationNumber);

    // Status queries (pending first, then by date)
    List<StayPartnerApplication> findByStatusOrderByCreatedAtAsc(StayPartnerApplication.ApplicationStatus status);

    @Query("SELECT a FROM StayPartnerApplication a ORDER BY " +
            "CASE a.status WHEN 'PENDING' THEN 0 WHEN 'UNDER_REVIEW' THEN 1 WHEN 'APPROVED' THEN 2 WHEN 'REJECTED' THEN 3 END, " +
            "a.createdAt DESC")
    List<StayPartnerApplication> findAllOrderByStatusAndDate();

    // Counts
    long countByStatus(StayPartnerApplication.ApplicationStatus status);

    // Duplicate check — one pending application per email (case-insensitive)
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM StayPartnerApplication a " +
            "WHERE LOWER(a.email) = LOWER(:email) AND a.status = 'PENDING'")
    boolean existsPendingByEmail(@Param("email") String email);

    // Status lookup by application number and email (case-insensitive on email)
    @Query("SELECT a FROM StayPartnerApplication a " +
            "WHERE a.applicationNumber = :applicationNumber " +
            "  AND LOWER(a.email) = LOWER(:email)")
    Optional<StayPartnerApplication> findByApplicationNumberAndEmail(
            @Param("applicationNumber") String applicationNumber,
            @Param("email") String email);
}
