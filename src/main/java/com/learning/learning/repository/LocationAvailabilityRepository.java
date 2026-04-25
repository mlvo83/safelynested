package com.learning.learning.repository;

import com.learning.learning.entity.LocationAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LocationAvailabilityRepository extends JpaRepository<LocationAvailability, Long> {

    List<LocationAvailability> findByPartnerLocationIdOrderByStartDateAsc(Long partnerLocationId);

    List<LocationAvailability> findByPartnerLocationIdAndStatusOrderByStartDateAsc(
            Long partnerLocationId, LocationAvailability.AvailabilityStatus status);

    List<LocationAvailability> findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(
            LocalDate endDate, LocalDate startDate, LocationAvailability.AvailabilityStatus status);

    java.util.Optional<LocationAvailability> findByBookingId(Long bookingId);

    /**
     * All availability rows whose date range overlaps the given window.
     * Used by the admin partner-bookings calendar.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT la FROM LocationAvailability la
            WHERE la.startDate <= :rangeEnd
              AND la.endDate >= :rangeStart
            ORDER BY la.startDate ASC
            """)
    List<LocationAvailability> findOverlappingRange(
            @org.springframework.data.repository.query.Param("rangeStart") LocalDate rangeStart,
            @org.springframework.data.repository.query.Param("rangeEnd") LocalDate rangeEnd);
}
