package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Donor;
import com.learning.learning.entity.Role;
import com.learning.learning.entity.User;
import com.learning.learning.repository.CharityRepository;
import com.learning.learning.repository.DonorRepository;
import com.learning.learning.repository.RoleRepository;
import com.learning.learning.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DonorService {

    private static final Logger logger = LoggerFactory.getLogger(DonorService.class);

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CharityRepository charityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ========================================
    // DONOR RETRIEVAL
    // ========================================

    /**
     * Get donor by username (for logged-in donor)
     */
    public Donor getDonorByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return donorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + username));
    }

    /**
     * Get donor by ID
     */
    public Donor getDonorById(Long donorId) {
        return donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found with ID: " + donorId));
    }

    /**
     * Find donor by ID (returns Optional)
     */
    public Optional<Donor> findById(Long donorId) {
        return donorRepository.findById(donorId);
    }

    /**
     * Get all donors
     */
    public List<Donor> getAllDonors() {
        return donorRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get donors for a specific charity (for charity partner read-only view)
     */
    public List<Donor> getDonorsForCharity(Long charityId) {
        return donorRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
    }

    /**
     * Search donors
     */
    public List<Donor> searchDonors(String searchTerm) {
        return donorRepository.searchDonors(searchTerm);
    }

    /**
     * Search donors within a charity
     */
    public List<Donor> searchDonorsForCharity(Long charityId, String searchTerm) {
        return donorRepository.searchDonorsByCharity(charityId, searchTerm);
    }

    // ========================================
    // DONOR CREATION (Admin only)
    // ========================================

    /**
     * Create a new donor with user account
     * This creates both a User and a Donor profile
     */
    @Transactional
    public Donor createDonor(String username, String password, String email,
                             String firstName, String lastName, String phone) {
        logger.info("Creating new donor: {}", username);

        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }

        // Check if email already exists
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }

        // Get DONOR role
        Role donorRole = roleRepository.findByName("ROLE_DONOR")
                .orElseThrow(() -> new RuntimeException("ROLE_DONOR not found. Please run the migration."));

        // Create User
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setEnabled(true);
        user.setIsActive(true);
        user.addRole(donorRole);

        user = userRepository.save(user);
        logger.info("Created user for donor: {}", user.getId());

        // Create Donor profile
        Donor donor = new Donor();
        donor.setUser(user);
        donor.setDonorType(Donor.DonorType.INDIVIDUAL);
        donor.setIsVerified(false);
        donor.setCreatedAt(LocalDateTime.now());

        donor = donorRepository.save(donor);
        logger.info("Created individual donor profile: {}", donor.getId());

        return donor;
    }

    /**
     * Create a new business donor with user account
     */
    @Transactional
    public Donor createBusinessDonor(String username, String password, String email, String phone,
                                      String businessName, String contactName, String taxId) {
        logger.info("Creating new business donor: {} - {}", username, businessName);

        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }

        // Check if email already exists
        if (email != null && !email.isEmpty() && userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }

        // Get DONOR role
        Role donorRole = roleRepository.findByName("ROLE_DONOR")
                .orElseThrow(() -> new RuntimeException("ROLE_DONOR not found. Please run the migration."));

        // Create User (contact info for business)
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhone(phone);
        user.setFirstName(contactName); // Store contact name as first name for now
        user.setEnabled(true);
        user.setIsActive(true);
        user.addRole(donorRole);

        user = userRepository.save(user);
        logger.info("Created user for business donor: {}", user.getId());

        // Create Donor profile with business info
        Donor donor = new Donor();
        donor.setUser(user);
        donor.setDonorType(Donor.DonorType.BUSINESS);
        donor.setBusinessName(businessName);
        donor.setContactName(contactName);
        donor.setTaxId(taxId);
        donor.setIsVerified(false);
        donor.setCreatedAt(LocalDateTime.now());

        donor = donorRepository.save(donor);
        logger.info("Created business donor profile: {} - {}", donor.getId(), businessName);

        return donor;
    }

    /**
     * Create donor profile for existing user
     */
    @Transactional
    public Donor createDonorProfile(User user) {
        // Check if donor profile already exists
        if (donorRepository.existsByUserId(user.getId())) {
            throw new RuntimeException("Donor profile already exists for user: " + user.getUsername());
        }

        // Add DONOR role if not present
        Role donorRole = roleRepository.findByName("ROLE_DONOR")
                .orElseThrow(() -> new RuntimeException("ROLE_DONOR not found"));

        if (!user.hasRole("ROLE_DONOR")) {
            user.addRole(donorRole);
            userRepository.save(user);
        }

        // Create Donor profile
        Donor donor = new Donor();
        donor.setUser(user);
        donor.setCreatedAt(LocalDateTime.now());

        return donorRepository.save(donor);
    }

    // ========================================
    // DONOR UPDATE
    // ========================================

    /**
     * Update individual donor information
     */
    @Transactional
    public Donor updateDonor(Long donorId, String firstName, String lastName,
                             String email, String phone, String verificationNotes) {
        Donor donor = getDonorById(donorId);
        User user = donor.getUser();

        // Set donor type to individual
        donor.setDonorType(Donor.DonorType.INDIVIDUAL);
        // Clear business fields
        donor.setBusinessName(null);
        donor.setContactName(null);
        donor.setTaxId(null);

        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (email != null && !email.isEmpty()) {
            // Check if email conflicts with another user
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }

        userRepository.save(user);

        if (verificationNotes != null) {
            donor.setVerificationNotes(verificationNotes);
        }

        return donorRepository.save(donor);
    }

    /**
     * Update business donor information
     */
    @Transactional
    public Donor updateBusinessDonor(Long donorId, String email, String phone,
                                      String businessName, String contactName,
                                      String taxId, String verificationNotes) {
        Donor donor = getDonorById(donorId);
        User user = donor.getUser();

        // Update user contact info
        if (email != null) {
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (contactName != null) {
            user.setFirstName(contactName);
        }
        userRepository.save(user);

        // Update donor business info
        donor.setDonorType(Donor.DonorType.BUSINESS);
        if (businessName != null) {
            donor.setBusinessName(businessName);
        }
        if (contactName != null) {
            donor.setContactName(contactName);
        }
        if (taxId != null) {
            donor.setTaxId(taxId);
        }
        if (verificationNotes != null) {
            donor.setVerificationNotes(verificationNotes);
        }

        return donorRepository.save(donor);
    }

    /**
     * Update all charity assignments at once (replaces existing)
     */
    @Transactional
    public Donor updateCharityAssignments(Long donorId, List<Long> charityIds) {
        Donor donor = getDonorById(donorId);

        // Clear existing associations
        donor.getCharities().clear();

        // Add new associations
        if (charityIds != null && !charityIds.isEmpty()) {
            for (Long charityId : charityIds) {
                Charity charity = charityRepository.findById(charityId)
                        .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));
                donor.addCharity(charity);
            }
        }

        return donorRepository.save(donor);
    }

    /**
     * Verify donor
     */
    @Transactional
    public Donor verifyDonor(Long donorId, String notes) {
        Donor donor = getDonorById(donorId);
        donor.setIsVerified(true);
        donor.setVerificationNotes(notes);
        return donorRepository.save(donor);
    }

    /**
     * Unverify donor
     */
    @Transactional
    public Donor unverifyDonor(Long donorId) {
        Donor donor = getDonorById(donorId);
        donor.setIsVerified(false);
        return donorRepository.save(donor);
    }

    // ========================================
    // CHARITY ASSOCIATION
    // ========================================

    /**
     * Assign a charity to a donor
     */
    @Transactional
    public Donor assignCharity(Long donorId, Long charityId) {
        Donor donor = getDonorById(donorId);
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        if (donor.isAssociatedWithCharity(charityId)) {
            logger.info("Donor {} already associated with charity {}", donorId, charityId);
            return donor;
        }

        donor.addCharity(charity);
        donor = donorRepository.save(donor);

        logger.info("Assigned charity {} to donor {}", charityId, donorId);
        return donor;
    }

    /**
     * Remove charity association from donor
     */
    @Transactional
    public Donor removeCharity(Long donorId, Long charityId) {
        Donor donor = getDonorById(donorId);
        Charity charity = charityRepository.findById(charityId)
                .orElseThrow(() -> new RuntimeException("Charity not found: " + charityId));

        donor.removeCharity(charity);
        donor = donorRepository.save(donor);

        logger.info("Removed charity {} from donor {}", charityId, donorId);
        return donor;
    }

    /**
     * Check if donor is associated with a charity
     */
    public boolean isDonorAssociatedWithCharity(Long donorId, Long charityId) {
        Donor donor = getDonorById(donorId);
        return donor.isAssociatedWithCharity(charityId);
    }

    // ========================================
    // DONOR DELETION
    // ========================================

    /**
     * Deactivate donor (soft delete)
     */
    @Transactional
    public void deactivateDonor(Long donorId) {
        Donor donor = getDonorById(donorId);
        User user = donor.getUser();
        user.setIsActive(false);
        user.setEnabled(false);
        userRepository.save(user);
        logger.info("Deactivated donor: {}", donorId);
    }

    /**
     * Activate donor
     */
    @Transactional
    public void activateDonor(Long donorId) {
        Donor donor = getDonorById(donorId);
        User user = donor.getUser();
        user.setIsActive(true);
        user.setEnabled(true);
        userRepository.save(user);
        logger.info("Activated donor: {}", donorId);
    }

    // ========================================
    // STATISTICS
    // ========================================

    /**
     * Count all donors
     */
    public long countAllDonors() {
        return donorRepository.count();
    }

    /**
     * Count donors for a charity
     */
    public long countDonorsForCharity(Long charityId) {
        return donorRepository.countByCharityId(charityId);
    }

    /**
     * Count verified donors
     */
    public long countVerifiedDonors() {
        return donorRepository.findByIsVerified(true).size();
    }
}
