package com.learning.learning.repository;

import com.learning.learning.entity.LocationPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationPartnerRepository extends JpaRepository<LocationPartner, Long> {

    Optional<LocationPartner> findByUserId(Long userId);

    Optional<LocationPartner> findByContactEmail(String contactEmail);

    java.util.List<LocationPartner> findAllByOrderByCreatedAtDesc();
}
