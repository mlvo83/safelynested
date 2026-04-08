package com.learning.learning.repository;

import com.learning.learning.entity.CharityApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharityApplicationRepository extends JpaRepository<CharityApplication, Long> {

    Optional<CharityApplication> findByApplicationNumber(String applicationNumber);

    List<CharityApplication> findByStatusOrderByCreatedAtAsc(CharityApplication.ApplicationStatus status);

    @Query("SELECT a FROM CharityApplication a ORDER BY " +
            "CASE a.status WHEN 'PENDING' THEN 0 WHEN 'UNDER_REVIEW' THEN 1 WHEN 'APPROVED' THEN 2 WHEN 'REJECTED' THEN 3 END, " +
            "a.createdAt DESC")
    List<CharityApplication> findAllOrderByStatusAndDate();

    long countByStatus(CharityApplication.ApplicationStatus status);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM CharityApplication a " +
            "WHERE a.contactEmail = :email AND a.status = 'PENDING'")
    boolean existsPendingByEmail(@Param("email") String email);

    Optional<CharityApplication> findByApplicationNumberAndContactEmail(String applicationNumber, String contactEmail);
}
