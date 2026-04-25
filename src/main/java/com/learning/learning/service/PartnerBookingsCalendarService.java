package com.learning.learning.service;

import com.learning.learning.entity.Booking;
import com.learning.learning.entity.LocationAvailability;
import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.repository.LocationAvailabilityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON event list for the admin partner-bookings calendar.
 * Pulls overlapping LocationAvailability rows in a date range and
 * decorates them with partner / charity / booking metadata. Coloring
 * is deterministic per Location Partner so the same partner always
 * gets the same swatch across loads.
 */
@Service
public class PartnerBookingsCalendarService {

    // Curated palette — high-contrast, accessible on white, perceptually-spaced
    // hues. Cycles by index. 20 entries pushes the "two partners share a color"
    // boundary out far enough for realistic charity-platform scale.
    private static final String[] PARTNER_PALETTE = {
            "#667eea", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6",
            "#06b6d4", "#ec4899", "#84cc16", "#f97316", "#6366f1",
            "#14b8a6", "#a855f7", "#2563eb", "#eab308", "#e11d48",
            "#059669", "#0284c7", "#c026d3", "#a3e635", "#b91c1c"
    };

    @Autowired
    private LocationAvailabilityRepository availabilityRepository;

    public List<CalendarEvent> getEvents(LocalDate rangeStart, LocalDate rangeEnd,
                                          Long partnerFilter, Long charityFilter) {
        List<LocationAvailability> rows = availabilityRepository.findOverlappingRange(rangeStart, rangeEnd);
        List<CalendarEvent> events = new ArrayList<>();

        for (LocationAvailability row : rows) {
            // Only show BOOKED slices on the calendar — AVAILABLE windows are kept
            // private to the partner's own dashboard and aren't useful here.
            if (row.getStatus() != LocationAvailability.AvailabilityStatus.BOOKED) {
                continue;
            }

            PartnerLocation pl = row.getPartnerLocation();
            if (pl == null) continue;
            LocationPartner partner = pl.getLocationPartner();
            if (partner == null) continue;

            // Apply partner filter
            if (partnerFilter != null && !partner.getId().equals(partnerFilter)) {
                continue;
            }

            Booking booking = row.getBooking();
            Long bookingCharityId = (booking != null && booking.getReferral() != null && booking.getReferral().getCharity() != null)
                    ? booking.getReferral().getCharity().getId()
                    : null;

            // Apply charity filter
            if (charityFilter != null) {
                if (bookingCharityId == null || !bookingCharityId.equals(charityFilter)) continue;
            }

            CalendarEvent ev = new CalendarEvent();
            ev.id = "booked-" + row.getId();
            ev.start = row.getStartDate().toString();
            // FullCalendar treats end as exclusive for all-day events; our endDate is the last night, so add 1.
            ev.end = row.getEndDate().plusDays(1).toString();
            ev.allDay = true;

            String partnerColor = colorForPartner(partner.getId());
            String charityName = (booking != null && booking.getReferral() != null && booking.getReferral().getCharity() != null)
                    ? booking.getReferral().getCharity().getCharityName()
                    : "Unknown charity";
            ev.title = pl.getName() + " — " + charityName;
            ev.backgroundColor = partnerColor;
            ev.borderColor = partnerColor;
            ev.textColor = "#ffffff";
            ev.display = "block";

            // Extended props are passed through to the click modal
            Map<String, Object> ext = new HashMap<>();
            ext.put("status", row.getStatus().name());
            ext.put("partnerId", partner.getId());
            ext.put("partnerName", partner.getDisplayName());
            ext.put("partnerEmail", partner.getContactEmail());
            ext.put("partnerLocationId", pl.getId());
            ext.put("partnerLocationName", pl.getName());
            ext.put("city", pl.getCity());
            ext.put("state", pl.getState());
            ext.put("zipCode", pl.getZipCode());
            ext.put("startDate", row.getStartDate().toString());
            ext.put("endDate", row.getEndDate().toString());
            ext.put("notes", row.getNotes());
            if (booking != null) {
                ext.put("bookingId", booking.getId());
                ext.put("confirmationCode", booking.getConfirmationCode());
                ext.put("checkInDate", booking.getCheckInDate() != null ? booking.getCheckInDate().toString() : null);
                ext.put("checkOutDate", booking.getCheckOutDate() != null ? booking.getCheckOutDate().toString() : null);
                ext.put("nights", booking.getNights());
                ext.put("participantName", booking.getParticipantName());
                ext.put("bookingStatus", booking.getBookingStatus() != null ? booking.getBookingStatus().name() : null);
                if (booking.getReferral() != null && booking.getReferral().getCharity() != null) {
                    ext.put("charityId", booking.getReferral().getCharity().getId());
                    ext.put("charityName", booking.getReferral().getCharity().getCharityName());
                }
            }
            ev.extendedProps = ext;
            events.add(ev);
        }
        return events;
    }

    /**
     * Deterministic color for a partner — same partner always gets the same swatch.
     * Public so the controller can build the per-partner legend.
     */
    public String colorForPartner(Long partnerId) {
        long idx = Math.floorMod(partnerId, PARTNER_PALETTE.length);
        return PARTNER_PALETTE[(int) idx];
    }

    /**
     * FullCalendar event payload (kept simple as a public class so Jackson serializes fields directly).
     */
    public static class CalendarEvent {
        public String id;
        public String title;
        public String start;
        public String end;
        public boolean allDay;
        public String backgroundColor;
        public String borderColor;
        public String textColor;
        public String display;
        public Map<String, Object> extendedProps;
    }
}
