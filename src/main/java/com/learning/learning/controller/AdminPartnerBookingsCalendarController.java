package com.learning.learning.controller;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.LocationPartner;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.LocationPartnerRepository;
import com.learning.learning.service.PartnerBookingsCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin-only calendar showing partner-property bookings (and optionally available windows)
 * across the whole platform. Powered by FullCalendar v6 (self-hosted).
 */
@Controller
@RequestMapping("/admin/partner-bookings-calendar")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPartnerBookingsCalendarController {

    @Autowired
    private PartnerBookingsCalendarService calendarService;

    @Autowired
    private LocationPartnerRepository locationPartnerRepository;

    @Autowired
    private CharityRepository charityRepository;

    @GetMapping
    public String page(Model model) {
        List<LocationPartner> partners = locationPartnerRepository.findAllByOrderByCreatedAtDesc();
        List<Charity> charities = charityRepository.findAllActiveOrderByName();

        // Build per-partner color legend entries the template can iterate over
        java.util.List<PartnerLegendEntry> partnerLegend = new java.util.ArrayList<>();
        for (LocationPartner p : partners) {
            partnerLegend.add(new PartnerLegendEntry(
                    p.getId(),
                    p.getDisplayName(),
                    calendarService.colorForPartner(p.getId())
            ));
        }

        model.addAttribute("partners", partners);
        model.addAttribute("charities", charities);
        model.addAttribute("partnerLegend", partnerLegend);
        return "admin/partner-bookings-calendar";
    }

    public record PartnerLegendEntry(Long id, String name, String color) {}

    /**
     * JSON events feed consumed by FullCalendar.
     * FullCalendar passes start and end (ISO strings, end exclusive) on each navigation.
     */
    @GetMapping(value = "/events.json", produces = "application/json")
    @ResponseBody
    public List<PartnerBookingsCalendarService.CalendarEvent> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String end,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long charityId) {

        // FullCalendar sends ISO date-times; we only need the date portion.
        LocalDate rangeStart = LocalDate.parse(start.substring(0, 10));
        // FullCalendar's end is exclusive; subtract 1 to make our query inclusive.
        LocalDate rangeEnd = LocalDate.parse(end.substring(0, 10)).minusDays(1);

        return calendarService.getEvents(rangeStart, rangeEnd, partnerId, charityId);
    }
}
