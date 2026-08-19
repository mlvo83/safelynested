package com.learning.learning.service;

import com.learning.learning.entity.*;
import com.learning.learning.repository.DonorSetupRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
public class DonorSetupRequestService {

    private static final Logger logger = LoggerFactory.getLogger(DonorSetupRequestService.class);

    @Autowired
    private DonorSetupRequestRepository requestRepository;

    @Autowired
    private DonorService donorService;

    // ========================================
    // CREATE REQUESTS
    // ========================================

    @Transactional
    public DonorSetupRequest createLinkRequest(Donor donor, Charity charity, User user, String notes) {
        // Check if donor is already linked to this charity
        if (donor.isAssociatedWithCharity(charity.getId())) {
            throw new RuntimeException("This donor is already linked to your charity.");
        }

        // Check for duplicate pending request
        if (requestRepository.existsPendingForDonorAndCharity(donor.getId(), charity.getId())) {
            throw new RuntimeException("A pending request already exists for this donor and your charity.");
        }

        DonorSetupRequest request = new DonorSetupRequest();
        request.setRequestNumber(generateRequestNumber());
        request.setRequestType(DonorSetupRequest.RequestType.LINK_EXISTING);
        request.setStatus(DonorSetupRequest.RequestStatus.PENDING);
        request.setCharity(charity);
        request.setRequestedBy(user);
        request.setExistingDonor(donor);
        request.setNotes(notes);

        request = requestRepository.save(request);
        logger.info("Created link request {} for donor {} to charity {}", request.getRequestNumber(), donor.getId(), charity.getId());
        return request;
    }

    @Transactional
    public DonorSetupRequest createNewDonorRequest(
            Charity charity, User user,
            Donor.DonorType donorType, String firstName, String lastName,
            String email, String phone,
            String streetAddress, String city, String state, String zipCode,
            String companyName, String contactName, String taxId,
            DonorSetupRequest.AnonymityPreference anonymityPreference,
            DonorSetupRequest.PreferredContactMethod preferredContactMethod,
            String notes) {

        // Email is required for new donors — it's the donor's contact/login identity and
        // prevents blank-email collisions on the users.email unique constraint at approval.
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required to create a new donor.");
        }

        // Check for duplicate pending request by email
        if (requestRepository.existsPendingForEmailAndCharity(email.trim(), charity.getId())) {
            throw new RuntimeException("A pending request already exists for this email address.");
        }

        DonorSetupRequest request = new DonorSetupRequest();
        request.setRequestNumber(generateRequestNumber());
        request.setRequestType(DonorSetupRequest.RequestType.CREATE_NEW);
        request.setStatus(DonorSetupRequest.RequestStatus.PENDING);
        request.setCharity(charity);
        request.setRequestedBy(user);

        // Donor info
        request.setDonorType(donorType);
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setEmail(email);
        request.setPhone(phone);

        // Address
        request.setStreetAddress(streetAddress);
        request.setCity(city);
        request.setState(state);
        request.setZipCode(zipCode);

        // Business
        request.setCompanyName(companyName);
        request.setContactName(contactName);
        request.setTaxId(taxId);

        // Preferences
        request.setAnonymityPreference(anonymityPreference);
        request.setPreferredContactMethod(preferredContactMethod);

        request.setNotes(notes);

        request = requestRepository.save(request);
        logger.info("Created new donor request {} for charity {}", request.getRequestNumber(), charity.getId());
        return request;
    }

    // ========================================
    // APPROVE / REJECT
    // ========================================

    @Transactional
    public DonorSetupRequest approveRequest(Long requestId, User adminUser, String adminNotes,
                                            String emailOverride, String usernameOverride,
                                            String passwordOverride) {
        DonorSetupRequest request = getRequestById(requestId);

        if (!request.isPending()) {
            throw new RuntimeException("Request is not in pending status.");
        }

        Donor resultDonor;

        if (request.isLinkExisting()) {
            // Link existing donor to charity
            resultDonor = donorService.assignCharity(request.getExistingDonor().getId(), request.getCharity().getId());
            logger.info("Linked existing donor {} to charity {}", resultDonor.getId(), request.getCharity().getId());
        } else {
            // Create new donor. The admin can assign the login identity (email / username /
            // temporary password) at approval time; fall back to the request's email and
            // generated credentials when a value isn't supplied.
            String email = firstNonBlank(emailOverride, request.getEmail());
            if (isBlank(email)) {
                throw new RuntimeException("An email address is required to create the donor account.");
            }
            request.setEmail(email);

            String username = firstNonBlank(usernameOverride, null);
            String password = firstNonBlank(passwordOverride, null);

            if (request.getDonorType() == Donor.DonorType.BUSINESS) {
                if (username == null) username = generateUsername(request.getCompanyName());
                if (password == null) password = generateTemporaryPassword();

                resultDonor = donorService.createBusinessDonor(
                        username, password, email, request.getPhone(),
                        request.getCompanyName(), request.getContactName(), request.getTaxId());
            } else {
                if (username == null) username = generateUsername(request.getFirstName(), request.getLastName());
                if (password == null) password = generateTemporaryPassword();

                resultDonor = donorService.createDonor(
                        username, password, email,
                        request.getFirstName(), request.getLastName(), request.getPhone());
            }

            // Assign to charity
            donorService.assignCharity(resultDonor.getId(), request.getCharity().getId());
            logger.info("Created new donor {} and assigned to charity {}", resultDonor.getId(), request.getCharity().getId());
        }

        request.setStatus(DonorSetupRequest.RequestStatus.APPROVED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminNotes(adminNotes);
        request.setResultDonor(resultDonor);

        return requestRepository.save(request);
    }

    @Transactional
    public DonorSetupRequest rejectRequest(Long requestId, User adminUser, String rejectionReason) {
        DonorSetupRequest request = getRequestById(requestId);

        if (!request.isPending()) {
            throw new RuntimeException("Request is not in pending status.");
        }

        request.setStatus(DonorSetupRequest.RequestStatus.REJECTED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);

        logger.info("Rejected request {}: {}", request.getRequestNumber(), rejectionReason);
        return requestRepository.save(request);
    }

    // ========================================
    // RETRIEVAL
    // ========================================

    public DonorSetupRequest getRequestById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Donor setup request not found: " + id));
    }

    public List<DonorSetupRequest> getRequestsForCharity(Long charityId) {
        return requestRepository.findByCharityIdOrderByCreatedAtDesc(charityId);
    }

    public List<DonorSetupRequest> getRequestsForCharity(Long charityId, DonorSetupRequest.RequestStatus status) {
        return requestRepository.findByCharityIdAndStatusOrderByCreatedAtDesc(charityId, status);
    }

    public List<DonorSetupRequest> getAllRequests() {
        return requestRepository.findAllOrderByStatusAndDate();
    }

    public List<DonorSetupRequest> getRequestsByStatus(DonorSetupRequest.RequestStatus status) {
        return requestRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    public long countPendingRequests() {
        return requestRepository.countByStatus(DonorSetupRequest.RequestStatus.PENDING);
    }

    public long countPendingRequestsForCharity(Long charityId) {
        return requestRepository.countByCharityIdAndStatus(charityId, DonorSetupRequest.RequestStatus.PENDING);
    }

    // ========================================
    // HELPERS
    // ========================================

    private String generateRequestNumber() {
        int year = Year.now().getValue();
        long count = requestRepository.count() + 1;
        return String.format("DSR-%d-%04d", year, count);
    }

    /** Suggests a username for a CREATE_NEW request (business → company name, else person name). */
    public String suggestUsername(DonorSetupRequest request) {
        if (request.getDonorType() == Donor.DonorType.BUSINESS) {
            return generateUsername(request.getCompanyName());
        }
        return generateUsername(request.getFirstName(), request.getLastName());
    }

    private String generateUsername(String companyName) {
        String base = companyName == null ? "" : companyName.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.length() > 15) base = base.substring(0, 15);
        return "donor_" + base + "_" + System.currentTimeMillis() % 10000;
    }

    private String generateUsername(String firstName, String lastName) {
        String first = firstName != null ? firstName.toLowerCase().replaceAll("[^a-z]", "") : "";
        String last = lastName != null ? lastName.toLowerCase().replaceAll("[^a-z]", "") : "";
        String base = first + (last.isEmpty() ? "" : "." + last);
        if (base.length() > 15) base = base.substring(0, 15);
        return "donor_" + base + "_" + System.currentTimeMillis() % 10000;
    }

    public String generateTemporaryPassword() {
        return "TempPass" + System.currentTimeMillis() % 100000 + "!";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** First of the two values that isn't blank (trimmed), or null if both are blank. */
    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a.trim();
        if (!isBlank(b)) return b.trim();
        return null;
    }
}
