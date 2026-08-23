package com.learning.learning.service;

import com.learning.learning.entity.LocationPartner;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.StayPartnerApplication;
import com.learning.learning.entity.User;
import com.learning.learning.repository.LocationPartnerRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Business logic scoped to a single Location Partner.
 * All lookups start from the logged-in username so a partner
 * can only see/modify their own data.
 */
@Service
public class LocationPartnerService {

    private static final Logger logger = LoggerFactory.getLogger(LocationPartnerService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationPartnerRepository locationPartnerRepository;

    @Autowired
    private PartnerLocationRepository partnerLocationRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    /** Credentials produced by an admin password reset (shown once to the admin). */
    public record ResetCredentials(String username, String email, String temporaryPassword) {}

    /**
     * Admin-initiated password reset for a location partner. Resolves the account via the
     * partner's own {@code user} link (no email matching), assigns a temporary password,
     * forces a change on next login, emails it, and returns the credentials.
     */
    @Transactional
    public ResetCredentials resetLocationPartnerPassword(Long partnerId) {
        LocationPartner partner = locationPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Location Partner not found: " + partnerId));
        User user = partner.getUser();
        if (user == null) {
            throw new RuntimeException("This location partner hasn't registered an account yet — "
                    + "there's no password to reset.");
        }
        String temporaryPassword = passwordResetService.resetToTemporaryPassword(user, partner.getDisplayName());
        return new ResetCredentials(user.getUsername(), user.getEmail(), temporaryPassword);
    }

    public LocationPartner getPartnerForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return locationPartnerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No Location Partner profile found for this account."));
    }

    /**
     * All properties for the partner, active first, then alphabetical by name.
     */
    public List<PartnerLocation> getLocationsForPartner(LocationPartner partner) {
        return partnerLocationRepository.findByLocationPartnerId(partner.getId()).stream()
                .sorted(Comparator
                        .comparing((PartnerLocation pl) -> !Boolean.TRUE.equals(pl.getIsActive()))
                        .thenComparing(pl -> pl.getName() == null ? "" : pl.getName().toLowerCase()))
                .toList();
    }

    public List<PartnerLocation> getActiveLocationsForPartner(LocationPartner partner) {
        return partnerLocationRepository.findByLocationPartnerIdAndIsActiveTrue(partner.getId());
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

    /**
     * Create a new property for the partner. Address fields are required.
     * Property starts active so partner can mark availability immediately;
     * it still won't appear in any charity's booking dropdown until admin
     * links charities to it (Phase 3 gate).
     */
    @Transactional
    public PartnerLocation createPropertyForPartner(LocationPartner partner,
                                                     String name,
                                                     StayPartnerApplication.PropertyType propertyType,
                                                     String address,
                                                     String city,
                                                     String state,
                                                     String zipCode,
                                                     String country,
                                                     Integer numberOfBedrooms,
                                                     Integer maxGuests,
                                                     BigDecimal nightlyRate,
                                                     String amenities,
                                                     String accessibilityFeatures,
                                                     Boolean petsAllowed,
                                                     String description) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Property name is required.");
        }
        if (address == null || address.isBlank()) {
            throw new RuntimeException("Street address is required.");
        }
        if (city == null || city.isBlank() || state == null || state.isBlank() || zipCode == null || zipCode.isBlank()) {
            throw new RuntimeException("City, state, and ZIP are required.");
        }

        PartnerLocation pl = new PartnerLocation();
        pl.setLocationPartner(partner);
        pl.setName(name.trim());
        pl.setPropertyType(propertyType);
        pl.setAddress(address.trim());
        pl.setCity(city.trim());
        pl.setState(state.trim());
        pl.setZipCode(zipCode.trim());
        pl.setCountry(country == null || country.isBlank() ? "USA" : country.trim());
        pl.setNumberOfBedrooms(numberOfBedrooms);
        pl.setMaxGuests(maxGuests);
        pl.setNightlyRate(nightlyRate);
        pl.setAmenities(amenities);
        pl.setAccessibilityFeatures(accessibilityFeatures);
        pl.setPetsAllowed(Boolean.TRUE.equals(petsAllowed));
        pl.setDescription(description);
        pl.setIsActive(true);

        pl = partnerLocationRepository.save(pl);
        logger.info("Created PartnerLocation id={} ('{}') for partner_id={}",
                pl.getId(), pl.getName(), partner.getId());
        return pl;
    }

    /**
     * Update non-address fields on a property the partner owns.
     * Address changes go through admin (different geographic context could
     * invalidate existing charity links).
     */
    @Transactional
    public PartnerLocation updatePropertyForPartner(LocationPartner partner,
                                                     Long locationId,
                                                     String name,
                                                     StayPartnerApplication.PropertyType propertyType,
                                                     Integer numberOfBedrooms,
                                                     Integer maxGuests,
                                                     BigDecimal nightlyRate,
                                                     String amenities,
                                                     String accessibilityFeatures,
                                                     Boolean petsAllowed,
                                                     String description) {
        PartnerLocation pl = getLocationForPartner(partner, locationId);
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Property name is required.");
        }
        pl.setName(name.trim());
        pl.setPropertyType(propertyType);
        pl.setNumberOfBedrooms(numberOfBedrooms);
        pl.setMaxGuests(maxGuests);
        pl.setNightlyRate(nightlyRate);
        pl.setAmenities(amenities);
        pl.setAccessibilityFeatures(accessibilityFeatures);
        pl.setPetsAllowed(Boolean.TRUE.equals(petsAllowed));
        pl.setDescription(description);
        pl = partnerLocationRepository.save(pl);
        logger.info("Updated PartnerLocation id={} for partner_id={}", pl.getId(), partner.getId());
        return pl;
    }

    @Transactional
    public PartnerLocation setActive(LocationPartner partner, Long locationId, boolean active) {
        PartnerLocation pl = getLocationForPartner(partner, locationId);
        pl.setIsActive(active);
        pl = partnerLocationRepository.save(pl);
        logger.info("Set PartnerLocation id={} is_active={} for partner_id={}",
                pl.getId(), active, partner.getId());
        return pl;
    }
}
