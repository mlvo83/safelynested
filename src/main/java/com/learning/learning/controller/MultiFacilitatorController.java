package com.learning.learning.controller;

import com.learning.learning.entity.Booking;
import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Referral;
import com.learning.learning.repository.BookingRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.service.MultiFacilitatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hub for multi-charity facilitators. After login they land here and
 * pick which charity to operate as. The hub shows one card per
 * authorized charity with badge counts so the user can see at a glance
 * which charity has urgent work waiting.
 *
 * Navigation into a charity uses the standard /charity-facilitator/{charityId}/...
 * URL pattern — the hub is purely a picker; no scoping state is held
 * server-side beyond the URL itself.
 */
@Controller
@RequestMapping("/multi-facilitator")
public class MultiFacilitatorController {

    @Autowired
    private MultiFacilitatorService multiFacilitatorService;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping({"", "/", "/hub"})
    public String hub(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";

        String username = principal.getName();
        List<Charity> authorizedCharities = multiFacilitatorService.getAuthorizedCharities(username);

        List<CharityCard> cards = new ArrayList<>(authorizedCharities.size());
        for (Charity charity : authorizedCharities) {
            CharityCard card = new CharityCard();
            card.id = charity.getId();
            card.name = charity.getCharityName();
            card.city = charity.getCity();
            card.state = charity.getState();
            card.pendingReferrals = referralRepository.countByCharityIdAndStatus(charity.getId(), Referral.ReferralStatus.PENDING);
            card.confirmedBookings = bookingRepository.countByCharityIdAndStatus(charity.getId(), Booking.BookingStatus.CONFIRMED);
            card.checkedInBookings = bookingRepository.countByCharityIdAndStatus(charity.getId(), Booking.BookingStatus.CHECKED_IN);
            cards.add(card);
        }

        model.addAttribute("username", username);
        model.addAttribute("cards", cards);
        return "multi-facilitator/hub";
    }

    /**
     * View model for one charity card on the hub. Public fields so
     * Thymeleaf's standard property accessor can read them without
     * generating boilerplate getters.
     */
    public static class CharityCard {
        public Long id;
        public String name;
        public String city;
        public String state;
        public long pendingReferrals;
        public long confirmedBookings;
        public long checkedInBookings;

        // Thymeleaf SpEL needs getter-style access for public-field reads
        // through the BeanWrapperImpl path; lombok-free getters here.
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public long getPendingReferrals() { return pendingReferrals; }
        public long getConfirmedBookings() { return confirmedBookings; }
        public long getCheckedInBookings() { return checkedInBookings; }

        public boolean getHasUrgentWork() {
            return pendingReferrals > 0 || confirmedBookings > 0;
        }

        public String getLocationLabel() {
            if (city != null && state != null) return city + ", " + state;
            if (city != null) return city;
            if (state != null) return state;
            return "";
        }
    }
}
