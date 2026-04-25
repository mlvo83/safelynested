package com.learning.learning.repository;

import com.learning.learning.entity.PartnerLocationCharity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerLocationCharityRepository extends JpaRepository<PartnerLocationCharity, Long> {

    List<PartnerLocationCharity> findByPartnerLocationIdOrderByCreatedAtAsc(Long partnerLocationId);

    List<PartnerLocationCharity> findByCharityIdOrderByCreatedAtAsc(Long charityId);

    Optional<PartnerLocationCharity> findByPartnerLocationIdAndCharityId(Long partnerLocationId, Long charityId);

    boolean existsByPartnerLocationIdAndCharityId(Long partnerLocationId, Long charityId);

    long countByPartnerLocationId(Long partnerLocationId);
}
