package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.MultiFacilitatorCharity;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.MultiFacilitatorCharityRepository;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Authorization and assignment helpers for the multi-charity
 * facilitator role. Use this service from controllers to check
 * whether a user may facilitate for a given charity, or to fetch
 * the list of charities a user is authorized for.
 */
@Service
public class MultiFacilitatorService {

    private static final Logger logger = LoggerFactory.getLogger(MultiFacilitatorService.class);

    @Autowired
    private MultiFacilitatorCharityRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharityRepository charityRepository;

    /**
     * Returns true if the given user is authorized to facilitate for
     * the given charity. Authorization comes from one of two paths:
     *
     *   1. Single-charity facilitator: user has ROLE_CHARITY_FACILITATOR
     *      AND user.charity_id == charityId.
     *   2. Multi-charity facilitator: user has ROLE_MULTI_FACILITATOR
     *      AND a row exists in multi_facilitator_charities for
     *      (user_id, charity_id).
     *
     * Admins are NOT auto-granted facilitator powers here — admin-side
     * tools live under /admin/**.
     */
    public boolean canFacilitateForCharity(String username, Long charityId) {
        if (username == null || charityId == null) return false;

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return false;

        // Path 1: single-charity facilitator
        boolean isSingleCharity = (user.hasRole("ROLE_CHARITY_FACILITATOR") || user.hasRole("CHARITY_FACILITATOR"))
                && user.getCharity() != null
                && charityId.equals(user.getCharity().getId());
        if (isSingleCharity) return true;

        // Path 2: multi-charity facilitator
        boolean isMulti = user.hasRole("ROLE_MULTI_FACILITATOR") || user.hasRole("MULTI_FACILITATOR");
        if (isMulti && assignmentRepository.existsByUserIdAndCharityId(user.getId(), charityId)) {
            return true;
        }

        return false;
    }

    /**
     * All charities the user is authorized to facilitate for, combined
     * across both authorization paths. Order is not guaranteed across
     * the two sources.
     */
    public List<Charity> getAuthorizedCharities(String username) {
        List<Charity> result = new ArrayList<>();
        if (username == null) return result;

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return result;

        // Single-charity facilitator path
        if ((user.hasRole("ROLE_CHARITY_FACILITATOR") || user.hasRole("CHARITY_FACILITATOR"))
                && user.getCharity() != null) {
            result.add(user.getCharity());
        }

        // Multi-charity facilitator path
        if (user.hasRole("ROLE_MULTI_FACILITATOR") || user.hasRole("MULTI_FACILITATOR")) {
            result.addAll(assignmentRepository.findAuthorizedCharitiesByUserId(user.getId()));
        }

        return result;
    }

    /**
     * Add a charity authorization for a multi-facilitator. Idempotent —
     * if the assignment already exists, this is a no-op. Records the
     * admin who created the assignment for audit.
     */
    @Transactional
    public MultiFacilitatorCharity assignCharity(Long userId, Long charityId, User assignedBy) {
        return assignmentRepository.findByUserIdAndCharityId(userId, charityId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
                    Charity charity = charityRepository.findById(charityId)
                            .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

                    MultiFacilitatorCharity assignment = new MultiFacilitatorCharity();
                    assignment.setUser(user);
                    assignment.setCharity(charity);
                    assignment.setCreatedByUser(assignedBy);

                    MultiFacilitatorCharity saved = assignmentRepository.save(assignment);
                    logger.info("Assigned multi-facilitator '{}' to charity '{}' by '{}'",
                            user.getUsername(),
                            charity.getCharityName(),
                            assignedBy != null ? assignedBy.getUsername() : "system");
                    return saved;
                });
    }

    /**
     * Remove a charity authorization. Idempotent — no error if the
     * assignment doesn't exist.
     */
    @Transactional
    public void removeAssignment(Long userId, Long charityId) {
        assignmentRepository.deleteByUserIdAndCharityId(userId, charityId);
        logger.info("Removed multi-facilitator assignment for user_id={} charity_id={}", userId, charityId);
    }

    /**
     * All current assignment rows for a user. Used by the admin form
     * to pre-populate the multi-charity selector.
     */
    public List<MultiFacilitatorCharity> getAssignmentsForUser(Long userId) {
        return assignmentRepository.findByUserId(userId);
    }

    /**
     * Reconcile the user's charity authorizations against the desired
     * set: add any new ones, remove any that are no longer desired.
     * Pass an empty or null list to remove all assignments.
     */
    @Transactional
    public void syncAssignments(Long userId, java.util.Collection<Long> desiredCharityIds, User assignedBy) {
        java.util.Set<Long> desired = new java.util.HashSet<>();
        if (desiredCharityIds != null) desired.addAll(desiredCharityIds);

        java.util.Set<Long> current = new java.util.HashSet<>();
        for (MultiFacilitatorCharity row : assignmentRepository.findByUserId(userId)) {
            current.add(row.getCharity().getId());
        }

        // Add anything in desired that isn't already there
        for (Long charityId : desired) {
            if (!current.contains(charityId)) {
                assignCharity(userId, charityId, assignedBy);
            }
        }

        // Remove anything currently there that isn't in desired
        for (Long charityId : current) {
            if (!desired.contains(charityId)) {
                removeAssignment(userId, charityId);
            }
        }
    }
}
