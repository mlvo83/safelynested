package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.PartnerLocation;
import com.learning.learning.entity.PartnerLocationCharity;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.PartnerLocationCharityRepository;
import com.learning.learning.repository.PartnerLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-only service for managing the many-to-many link
 * between PartnerLocation and Charity.
 */
@Service
public class PartnerLocationAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerLocationAssignmentService.class);

    @Autowired
    private PartnerLocationCharityRepository linkRepository;

    @Autowired
    private PartnerLocationRepository partnerLocationRepository;

    @Autowired
    private CharityRepository charityRepository;

    public List<PartnerLocationCharity> getLinksForLocation(Long partnerLocationId) {
        return linkRepository.findByPartnerLocationIdOrderByCreatedAtAsc(partnerLocationId);
    }

    public long countLinksForLocation(Long partnerLocationId) {
        return linkRepository.countByPartnerLocationId(partnerLocationId);
    }

    /**
     * Returns charities NOT yet linked to the given partner location,
     * for use in the "Add charity" dropdown.
     */
    public List<Charity> getEligibleCharities(Long partnerLocationId) {
        Set<Long> linkedIds = linkRepository.findByPartnerLocationIdOrderByCreatedAtAsc(partnerLocationId)
                .stream()
                .map(l -> l.getCharity().getId())
                .collect(Collectors.toCollection(HashSet::new));
        return charityRepository.findAllActiveOrderByName().stream()
                .filter(c -> !linkedIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public PartnerLocationCharity linkCharity(Long partnerLocationId, Long charityId, User adminUser) {
        PartnerLocation location = partnerLocationRepository.findById(partnerLocationId)
                .orElseThrow(() -> new RuntimeException("Partner location not found: " + partnerLocationId));
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        if (linkRepository.existsByPartnerLocationIdAndCharityId(partnerLocationId, charityId)) {
            throw new RuntimeException("That charity is already linked to this location.");
        }

        PartnerLocationCharity link = new PartnerLocationCharity();
        link.setPartnerLocation(location);
        link.setCharity(charity);
        link.setCreatedBy(adminUser);
        link = linkRepository.save(link);

        logger.info("Linked partner_location_id={} to charity_id={} by user '{}'",
                partnerLocationId, charityId, adminUser != null ? adminUser.getUsername() : "(unknown)");
        return link;
    }

    @Transactional
    public void unlinkCharity(Long partnerLocationId, Long charityId) {
        PartnerLocationCharity link = linkRepository.findByPartnerLocationIdAndCharityId(partnerLocationId, charityId)
                .orElseThrow(() -> new RuntimeException("That link does not exist."));
        linkRepository.delete(link);
        logger.info("Unlinked partner_location_id={} from charity_id={}", partnerLocationId, charityId);
    }
}
