package com.learning.learning.service;

import com.learning.learning.entity.Booking;
import com.learning.learning.entity.LocationAvailability;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.repository.LocationAvailabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class LocationAvailabilityService {

    private static final Logger logger = LoggerFactory.getLogger(LocationAvailabilityService.class);

    @Autowired
    private LocationAvailabilityRepository availabilityRepository;

    public List<LocationAvailability> getAvailabilityForLocation(Long partnerLocationId) {
        return availabilityRepository.findByPartnerLocationIdOrderByStartDateAsc(partnerLocationId);
    }

    @Transactional
    public LocationAvailability addWindow(PartnerLocation location, LocalDate startDate, LocalDate endDate,
                                           LocalTime startTime, LocalTime endTime, String notes) {
        if (startDate == null || endDate == null) {
            throw new RuntimeException("Start date and end date are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new RuntimeException("End date must be on or after start date.");
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new RuntimeException("End time must be on or after start time.");
        }

        // Reject overlaps with existing AVAILABLE windows on the same location
        List<LocationAvailability> existing = availabilityRepository.findByPartnerLocationIdOrderByStartDateAsc(location.getId());
        for (LocationAvailability other : existing) {
            if (other.getStatus() != LocationAvailability.AvailabilityStatus.AVAILABLE) {
                continue;
            }
            boolean overlaps = !endDate.isBefore(other.getStartDate()) && !startDate.isAfter(other.getEndDate());
            if (overlaps) {
                throw new RuntimeException("This window overlaps an existing availability ("
                        + other.getStartDate() + " – " + other.getEndDate() + ").");
            }
        }

        LocationAvailability window = new LocationAvailability();
        window.setPartnerLocation(location);
        window.setStartDate(startDate);
        window.setEndDate(endDate);
        window.setStartTime(startTime);
        window.setEndTime(endTime);
        window.setStatus(LocationAvailability.AvailabilityStatus.AVAILABLE);
        window.setNotes(notes);

        window = availabilityRepository.save(window);
        logger.info("Added availability window id={} for partner_location_id={} ({} to {})",
                window.getId(), location.getId(), startDate, endDate);
        return window;
    }

    @Transactional
    public void deleteWindow(PartnerLocation location, Long windowId) {
        LocationAvailability window = availabilityRepository.findById(windowId)
                .orElseThrow(() -> new RuntimeException("Availability window not found: " + windowId));
        if (window.getPartnerLocation() == null
                || !window.getPartnerLocation().getId().equals(location.getId())) {
            throw new RuntimeException("You do not have access to that availability window.");
        }
        if (window.getStatus() == LocationAvailability.AvailabilityStatus.BOOKED) {
            throw new RuntimeException("Cannot delete a booked window. Cancel the booking first.");
        }
        availabilityRepository.delete(window);
        logger.info("Deleted availability window id={} for partner_location_id={}", windowId, location.getId());
    }

    /**
     * Reserve part of an AVAILABLE window for a booking.
     * The booking's [checkInDate, checkOutDate) range must be fully contained
     * within a single AVAILABLE window. The window is split:
     *
     *   prefix AVAILABLE | BOOKED slice | suffix AVAILABLE
     *
     * Either prefix or suffix may be empty. The original window's row is
     * reused as the BOOKED slice (to preserve audit/created_at) and gets
     * a FK to the booking.
     */
    @Transactional
    public LocationAvailability bookWindow(PartnerLocation location, LocalDate checkInDate, LocalDate checkOutDate, Booking booking) {
        if (checkInDate == null || checkOutDate == null) {
            throw new RuntimeException("Check-in and check-out dates are required.");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new RuntimeException("Check-out must be after check-in.");
        }
        // The last night occupied is checkOutDate - 1
        LocalDate lastNight = checkOutDate.minusDays(1);

        // Find an AVAILABLE window that fully contains [checkInDate, lastNight]
        List<LocationAvailability> windows = availabilityRepository.findByPartnerLocationIdOrderByStartDateAsc(location.getId());
        LocationAvailability containing = null;
        for (LocationAvailability w : windows) {
            if (w.getStatus() != LocationAvailability.AvailabilityStatus.AVAILABLE) {
                continue;
            }
            if (!w.getStartDate().isAfter(checkInDate) && !w.getEndDate().isBefore(lastNight)) {
                containing = w;
                break;
            }
        }
        if (containing == null) {
            throw new RuntimeException("The selected dates are not within an available window for this property.");
        }

        // Capture the original bounds before mutating
        LocalDate origStart = containing.getStartDate();
        LocalDate origEnd = containing.getEndDate();
        LocalTime origStartTime = containing.getStartTime();
        LocalTime origEndTime = containing.getEndTime();
        String origNotes = containing.getNotes();

        // Build prefix slice (still AVAILABLE) if booking starts after window start
        if (origStart.isBefore(checkInDate)) {
            LocationAvailability prefix = new LocationAvailability();
            prefix.setPartnerLocation(location);
            prefix.setStartDate(origStart);
            prefix.setEndDate(checkInDate.minusDays(1));
            prefix.setStartTime(origStartTime);
            prefix.setEndTime(origEndTime);
            prefix.setStatus(LocationAvailability.AvailabilityStatus.AVAILABLE);
            prefix.setNotes(origNotes);
            availabilityRepository.save(prefix);
        }

        // Build suffix slice (still AVAILABLE) if booking ends before window end
        if (origEnd.isAfter(lastNight)) {
            LocationAvailability suffix = new LocationAvailability();
            suffix.setPartnerLocation(location);
            suffix.setStartDate(checkOutDate);
            suffix.setEndDate(origEnd);
            suffix.setStartTime(origStartTime);
            suffix.setEndTime(origEndTime);
            suffix.setStatus(LocationAvailability.AvailabilityStatus.AVAILABLE);
            suffix.setNotes(origNotes);
            availabilityRepository.save(suffix);
        }

        // Repurpose the original row as the BOOKED slice
        containing.setStartDate(checkInDate);
        containing.setEndDate(lastNight);
        containing.setStatus(LocationAvailability.AvailabilityStatus.BOOKED);
        containing.setBooking(booking);
        LocationAvailability booked = availabilityRepository.save(containing);
        logger.info("Booked partner_location_id={} window {} → {} for booking_id={}",
                location.getId(), checkInDate, lastNight, booking != null ? booking.getId() : null);
        return booked;
    }

    /**
     * Restore a BOOKED window back to AVAILABLE when its booking is cancelled.
     * Adjacent AVAILABLE windows are NOT merged automatically — admin/partner
     * can re-add a single window manually if they want a contiguous one.
     */
    @Transactional
    public void releaseWindowForBooking(Booking booking) {
        if (booking == null) return;
        Optional<LocationAvailability> opt = availabilityRepository.findByBookingId(booking.getId());
        if (opt.isEmpty()) {
            return; // not partner-funded, or no window to release
        }
        LocationAvailability window = opt.get();
        window.setStatus(LocationAvailability.AvailabilityStatus.AVAILABLE);
        window.setBooking(null);
        availabilityRepository.save(window);
        logger.info("Released availability window id={} (was booked by booking_id={})",
                window.getId(), booking.getId());
    }
}
