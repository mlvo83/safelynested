package com.learning.learning.repository;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.MultiFacilitatorCharity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MultiFacilitatorCharityRepository extends JpaRepository<MultiFacilitatorCharity, Long> {

    boolean existsByUserIdAndCharityId(Long userId, Long charityId);

    Optional<MultiFacilitatorCharity> findByUserIdAndCharityId(Long userId, Long charityId);

    List<MultiFacilitatorCharity> findByUserId(Long userId);

    List<MultiFacilitatorCharity> findByCharityId(Long charityId);

    /**
     * Charities a given user is authorized to facilitate for via the
     * multi-facilitator assignment table. Does NOT include the charity
     * referenced by user.charity_id (that's the single-charity facilitator
     * path); callers that need both should combine them.
     */
    @Query("SELECT mfc.charity FROM MultiFacilitatorCharity mfc " +
           "WHERE mfc.user.id = :userId " +
           "ORDER BY mfc.charity.charityName")
    List<Charity> findAuthorizedCharitiesByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndCharityId(Long userId, Long charityId);
}
