package com.learning.learning.repository;





import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Location;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByConfirmationCode(String confirmationCode);

    Optional<Booking> findByConfirmationNumber(String confirmationNumber);

    List<Booking> findByReferral(Referral referral);

    List<Booking> findByLocation(Location location);

    List<Booking> findByAssignedBy(User user);

    List<Booking> findByBookedByUser(User user);

    List<Booking> findByBookingStatus(Booking.BookingStatus status);

    List<Booking> findByPaymentStatus(Booking.PaymentStatus status);

    List<Booking> findByCheckInDateBetween(LocalDate startDate, LocalDate endDate);

    List<Booking> findByCheckOutDateBetween(LocalDate startDate, LocalDate endDate);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByAssignedByOrderByCreatedAtDesc(User user);

    Long countByBookingStatus(Booking.BookingStatus status);

    Long countByPaymentStatus(Booking.PaymentStatus status);

    @Query("SELECT b FROM Booking b WHERE b.checkInDate <= :date AND b.checkOutDate >= :date")
    List<Booking> findActiveBookingsOnDate(LocalDate date);

    @Query("SELECT b FROM Booking b WHERE b.location.id = :locationId AND b.checkInDate <= :checkOutDate AND b.checkOutDate >= :checkInDate AND b.bookingStatus NOT IN ('CANCELLED', 'NO_SHOW')")
    List<Booking> findOverlappingBookings(Long locationId, LocalDate checkInDate, LocalDate checkOutDate);
}