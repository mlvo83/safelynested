package com.learning.learning.service;

import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.User;
import com.learning.learning.repository.LocationPartnerRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic scoped to a single Location Partner.
 * All lookups start from the logged-in username so a partner
 * can only see/modify their own data.
 */
@Service
public class LocationPartnerService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationPartnerRepository locationPartnerRepository;

    @Autowired
    private PartnerLocationRepository partnerLocationRepository;

    public LocationPartner getPartnerForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return locationPartnerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No Location Partner profile found for this account."));
    }

    public List<PartnerLocation> getLocationsForPartner(LocationPartner partner) {
        return partnerLocationRepository.findByLocationPartnerId(partner.getId());
    }

    /**
     * Resolve a PartnerLocation but only if it belongs to the given partner.
     * Throws otherwise — prevents cross-partner data access.
     */
    public PartnerLocation getLocationForPartner(LocationPartner partner, Long locationId) {
        PartnerLocation location = partnerLocationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Location not found: " + locationId));
        if (location.getLocationPartner() == null
                || !location.getLocationPartner().getId().equals(partner.getId())) {
            throw new RuntimeException("You do not have access to that location.");
        }
        return location;
    }
}
