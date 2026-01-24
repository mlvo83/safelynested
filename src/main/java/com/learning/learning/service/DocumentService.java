package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Document;
import com.learning.learning.entity.Donor;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.ReferralInvite;
import com.learning.learning.entity.User;
import com.learning.learning.repository.DocumentRepository;
import com.learning.learning.repository.DonorRepository;
import com.learning.learning.repository.ReferralInviteRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.repository.UserRepository;
import com.learning.learning.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    @Autowired
    private StorageService storageService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private ReferralInviteRepository referralInviteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private CharityService charityService;

    // Allowed file types
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // ========================================
    // FILE UPLOAD
    // ========================================

    /**
     * Upload document for a referral
     */
    @Transactional
    public Document uploadDocument(
            MultipartFile file,
            Long referralId,
            Document.DocumentType documentType,
            String description,
            String username
    ) throws IOException {
        // Validate file
        validateFile(file);

        // Get user and charity
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Charity charity = charityService.getCharityForUser(username);

        // Get referral (with multi-tenant check)
        Referral referral = null;
        if (referralId != null) {
            referral = referralRepository.findByIdAndCharityId(referralId, charity.getId())
                    .orElseThrow(() -> new RuntimeException("Referral not found or access denied"));
        }

        // Generate unique file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFileName = generateUniqueFileName(originalFileName);

        // Create storage key: {charityId}/{referralId or 'general'}/{uniqueFileName}
        String storageKey = charity.getId() + "/" + (referralId != null ? referralId : "general") + "/" + uniqueFileName;

        // Store file using storage service (local or S3)
        storageService.store(file, storageKey);

        // Create document record
        Document document = new Document();
        document.setReferral(referral);
        document.setCharity(charity);
        document.setUploadedBy(user);
        document.setDocumentType(documentType);
        document.setFileName(originalFileName);
        document.setFilePath(storageKey); // Store the storage key, not full path
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDescription(description);
        document.setIsVerified(false);

        Document savedDocument = documentRepository.save(document);

        // Update referral document status if applicable
        if (referral != null && referral.getDocumentsRequired()) {
            referral.setDocumentsUploaded(true);
            referralRepository.save(referral);
        }

        return savedDocument;
    }

    /**
     * Upload document without referral (general charity document)
     */
    @Transactional
    public Document uploadCharityDocument(
            MultipartFile file,
            Document.DocumentType documentType,
            String description,
            String username
    ) throws IOException {
        return uploadDocument(file, null, documentType, description, username);
    }

    /**
     * Upload document for an invite (charity partner)
     */
    @Transactional
    public Document uploadDocumentForInvite(
            MultipartFile file,
            Long inviteId,
            Document.DocumentType documentType,
            String description,
            String username
    ) throws IOException {
        // Validate file
        validateFile(file);

        // Get user and charity
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Charity charity = charityService.getCharityForUser(username);

        // Get invite (with multi-tenant check)
        ReferralInvite invite = referralInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        // Verify invite belongs to user's charity
        if (invite.getCharity() == null || !invite.getCharity().getId().equals(charity.getId())) {
            throw new RuntimeException("Access denied: Invite does not belong to your charity");
        }

        // Generate unique file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFileName = generateUniqueFileName(originalFileName);

        // Create storage key: {charityId}/invites/{inviteId}/{uniqueFileName}
        String storageKey = charity.getId() + "/invites/" + inviteId + "/" + uniqueFileName;

        // Store file
        storageService.store(file, storageKey);

        // Create document record
        Document document = new Document();
        document.setInvite(invite);
        document.setReferral(invite.getReferral()); // Link to referral if exists
        document.setCharity(charity);
        document.setUploadedBy(user);
        document.setDocumentType(documentType);
        document.setFileName(originalFileName);
        document.setFilePath(storageKey);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDescription(description);
        document.setIsVerified(false);
        document.setUploadedByParticipant(false);

        return documentRepository.save(document);
    }

    /**
     * Upload document by participant via public invite link (no authentication required)
     */
    @Transactional
    public Document uploadDocumentByParticipant(
            MultipartFile file,
            String inviteToken,
            Document.DocumentType documentType,
            String description,
            String participantName
    ) throws IOException {
        // Validate file
        validateFile(file);

        // Get invite by token
        ReferralInvite invite = referralInviteRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new RuntimeException("Invalid invite token"));

        // Check invite is valid
        if (invite.isExpired()) {
            throw new RuntimeException("Invite has expired");
        }

        Charity charity = invite.getCharity();
        if (charity == null) {
            throw new RuntimeException("Invite has no associated charity");
        }

        // Generate unique file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFileName = generateUniqueFileName(originalFileName);

        // Create storage key: {charityId}/invites/{inviteId}/participant/{uniqueFileName}
        String storageKey = charity.getId() + "/invites/" + invite.getId() + "/participant/" + uniqueFileName;

        // Store file
        storageService.store(file, storageKey);

        // Create document record
        Document document = new Document();
        document.setInvite(invite);
        document.setReferral(invite.getReferral()); // Link to referral if exists
        document.setCharity(charity);
        document.setUploadedBy(null); // No user - uploaded by participant
        document.setDocumentType(documentType);
        document.setFileName(originalFileName);
        document.setFilePath(storageKey);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDescription(description);
        document.setIsVerified(false);
        document.setUploadedByParticipant(true);
        document.setParticipantName(participantName != null ? participantName : invite.getRecipientName());

        return documentRepository.save(document);
    }

    /**
     * Link all documents from an invite to a referral
     * Called when a referral is created from an invite
     */
    @Transactional
    public void linkInviteDocumentsToReferral(Long inviteId, Long referralId) {
        List<Document> inviteDocuments = documentRepository.findByInviteId(inviteId);
        Referral referral = referralRepository.findById(referralId)
                .orElseThrow(() -> new RuntimeException("Referral not found"));

        for (Document doc : inviteDocuments) {
            if (doc.getReferral() == null) {
                doc.setReferral(referral);
                documentRepository.save(doc);
            }
        }
    }

    // ========================================
    // DONOR DOCUMENT UPLOAD
    // ========================================

    /**
     * Upload document for a donor (by admin or the donor themselves)
     */
    @Transactional
    public Document uploadDonorDocument(
            MultipartFile file,
            Long donorId,
            Long charityId,
            Document.DocumentType documentType,
            String description,
            String username
    ) throws IOException {
        // Validate file
        validateFile(file);

        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get donor
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found"));

        // Get charity (must be one the donor is associated with)
        Charity charity = donor.getCharities().stream()
                .filter(c -> c.getId().equals(charityId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Donor is not associated with charity: " + charityId));

        // Verify access: must be admin, facilitator, or the donor themselves
        boolean isAdmin = user.isAdmin();
        boolean isFacilitator = user.isFacilitator();
        boolean isDonorUser = donor.getUser().getId().equals(user.getId());

        if (!isAdmin && !isFacilitator && !isDonorUser) {
            throw new RuntimeException("Access denied: Cannot upload documents for this donor");
        }

        // Generate unique file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String uniqueFileName = generateUniqueFileName(originalFileName);

        // Create storage key: {charityId}/donors/{donorId}/{uniqueFileName}
        String storageKey = charityId + "/donors/" + donorId + "/" + uniqueFileName;

        // Store file
        storageService.store(file, storageKey);

        // Create document record
        Document document = new Document();
        document.setDonor(donor);
        document.setCharity(charity);
        document.setUploadedBy(user);
        document.setDocumentType(documentType);
        document.setFileName(originalFileName);
        document.setFilePath(storageKey);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDescription(description);
        document.setIsVerified(false);

        return documentRepository.save(document);
    }

    /**
     * Upload document for own donor account (for donors to upload their own docs)
     */
    @Transactional
    public Document uploadOwnDonorDocument(
            MultipartFile file,
            Long charityId,
            Document.DocumentType documentType,
            String description,
            String username
    ) throws IOException {
        // Get donor by username
        Donor donor = donorRepository.findByUserUsername(username)
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + username));

        return uploadDonorDocument(file, donor.getId(), charityId, documentType, description, username);
    }

    // ========================================
    // DONOR DOCUMENT QUERIES
    // ========================================

    /**
     * Get all documents for a donor (donor's own view)
     */
    public List<Document> getDocumentsForDonor(String username) {
        Donor donor = donorRepository.findByUserUsername(username)
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + username));
        return documentRepository.findByDonorIdOrderByUploadedAtDesc(donor.getId());
    }

    /**
     * Get documents for a donor at a specific charity (donor's own view)
     */
    public List<Document> getDocumentsForDonorAtCharity(String username, Long charityId) {
        Donor donor = donorRepository.findByUserUsername(username)
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + username));

        // Verify donor is associated with this charity
        if (!donor.isAssociatedWithCharity(charityId)) {
            throw new RuntimeException("Donor is not associated with charity: " + charityId);
        }

        return documentRepository.findByDonorIdAndCharityId(donor.getId(), charityId);
    }

    /**
     * Get documents for a specific donor (admin/charity view)
     */
    public List<Document> getDocumentsForDonorById(Long donorId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Admins and facilitators can see any donor's documents
        if (user.isAdmin() || user.isFacilitator()) {
            return documentRepository.findByDonorIdOrderByUploadedAtDesc(donorId);
        }

        // Charity partners can only see donors associated with their charity
        if (user.isCharityPartner()) {
            Charity charity = charityService.getCharityForUser(username);
            Donor donor = donorRepository.findById(donorId)
                    .orElseThrow(() -> new RuntimeException("Donor not found"));

            if (!donor.isAssociatedWithCharity(charity.getId())) {
                throw new RuntimeException("Access denied: Donor not associated with your charity");
            }

            return documentRepository.findByDonorIdAndCharityId(donorId, charity.getId());
        }

        throw new RuntimeException("Access denied");
    }

    /**
     * Get document count for a donor
     */
    public Long getDocumentCountForDonor(Long donorId) {
        return documentRepository.countByDonorId(donorId);
    }

    // ========================================
    // FILE DOWNLOAD
    // ========================================

    /**
     * Download document (with access control)
     */
    public Resource downloadDocument(Long documentId, String username) throws IOException {
        Document document = getDocumentWithAccessCheck(documentId, username);

        InputStream inputStream = storageService.retrieve(document.getFilePath());
        return new InputStreamResource(inputStream);
    }

    /**
     * Get a download URL for a document (useful for S3 pre-signed URLs)
     */
    public String getDownloadUrl(Long documentId, String username, int expirationMinutes) {
        Document document = getDocumentWithAccessCheck(documentId, username);
        return storageService.getDownloadUrl(document.getFilePath(), expirationMinutes);
    }

    /**
     * Get document by ID with access control
     */
    public Document getDocumentWithAccessCheck(Long documentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Facilitators and admins can access all documents
        if (user.isFacilitator() || user.isAdmin()) {
            return document;
        }

        // Charity partners can only access their own charity's documents
        if (user.isCharityPartner() && user.belongsToCharity(document.getCharity().getId())) {
            return document;
        }

        // Donors can only access their own documents
        if (user.isDonor()) {
            Donor donor = donorRepository.findByUserId(user.getId()).orElse(null);
            if (donor != null && document.getDonor() != null &&
                    document.getDonor().getId().equals(donor.getId())) {
                return document;
            }
        }

        throw new RuntimeException("Access denied: User cannot access this document");
    }

    // ========================================
    // DOCUMENT QUERIES (MULTI-TENANT)
    // ========================================

    /**
     * Get all documents for current user's charity
     */
    public List<Document> getDocumentsForCharity(String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.findByCharityIdOrderByUploadedAtDesc(charityId);
    }

    /**
     * Get documents for a specific referral (with access control)
     */
    public List<Document> getDocumentsForReferral(Long referralId, String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.findByReferralIdAndCharityId(referralId, charityId);
    }

    /**
     * Get documents for a specific invite (with access control)
     */
    public List<Document> getDocumentsForInvite(Long inviteId, String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.findByInviteIdAndCharityId(inviteId, charityId);
    }

    /**
     * Get documents for an invite by token (for public access)
     */
    public List<Document> getDocumentsForInviteToken(String inviteToken) {
        ReferralInvite invite = referralInviteRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new RuntimeException("Invalid invite token"));
        return documentRepository.findByInviteIdOrderByUploadedAtDesc(invite.getId());
    }

    /**
     * Get document count for invite
     */
    public Long getDocumentCountForInvite(Long inviteId) {
        return documentRepository.countByInviteId(inviteId);
    }

    /**
     * Get documents by type for current user's charity
     */
    public List<Document> getDocumentsByType(Document.DocumentType documentType, String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.findByCharityIdAndDocumentType(charityId, documentType);
    }

    /**
     * Get unverified documents for current user's charity
     */
    public List<Document> getUnverifiedDocuments(String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.findByCharityIdAndIsVerified(charityId, false);
    }

    /**
     * Search documents for current user's charity
     */
    public List<Document> searchDocuments(String searchTerm, String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.searchDocuments(charityId, searchTerm);
    }

    /**
     * Get all documents (for facilitator/admin)
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAllOrderByUploadedAtDesc();
    }

    /**
     * Get all unverified documents (for facilitator/admin)
     */
    public List<Document> getAllUnverifiedDocuments() {
        return documentRepository.findUnverifiedDocuments();
    }

    // ========================================
    // DOCUMENT VERIFICATION
    // ========================================

    /**
     * Verify document (facilitator/admin only)
     */
    @Transactional
    public Document verifyDocument(Long documentId, String verifierUsername) {
        User verifier = userRepository.findByUsername(verifierUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!verifier.isFacilitator() && !verifier.isAdmin()) {
            throw new RuntimeException("Only facilitators and admins can verify documents");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        document.setIsVerified(true);
        document.setVerifiedAt(LocalDateTime.now());
        document.setVerifiedBy(verifier);

        return documentRepository.save(document);
    }

    /**
     * Unverify document
     */
    @Transactional
    public Document unverifyDocument(Long documentId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isFacilitator() && !user.isAdmin()) {
            throw new RuntimeException("Only facilitators and admins can unverify documents");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        document.setIsVerified(false);
        document.setVerifiedAt(null);
        document.setVerifiedBy(null);

        return documentRepository.save(document);
    }

    // ========================================
    // DOCUMENT UPDATE AND DELETE
    // ========================================

    /**
     * Update document metadata
     */
    @Transactional
    public Document updateDocument(Long documentId, String description, Document.DocumentType documentType, String username) {
        Document document = getDocumentWithAccessCheck(documentId, username);

        if (description != null) {
            document.setDescription(description);
        }
        if (documentType != null) {
            document.setDocumentType(documentType);
        }

        return documentRepository.save(document);
    }

    /**
     * Delete document (with access control)
     */
    @Transactional
    public void deleteDocument(Long documentId, String username) throws IOException {
        Document document = getDocumentWithAccessCheck(documentId, username);

        // Delete file from storage (local or S3)
        storageService.delete(document.getFilePath());

        // Delete database record
        documentRepository.delete(document);
    }

    /**
     * Get the current storage type (local or s3)
     */
    public String getStorageType() {
        return storageService.getStorageType();
    }

    // ========================================
    // STATISTICS
    // ========================================

    /**
     * Get document count for charity
     */
    public Long getDocumentCount(String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.countByCharityId(charityId);
    }

    /**
     * Get document count for referral
     */
    public Long getDocumentCountForReferral(Long referralId) {
        return documentRepository.countByReferralId(referralId);
    }

    /**
     * Get verified document count for charity
     */
    public Long getVerifiedDocumentCount(String username) {
        Long charityId = charityService.getCharityIdForUser(username);
        return documentRepository.countByCharityIdAndIsVerified(charityId, true);
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Cannot upload empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds maximum allowed size of 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new RuntimeException("File type not allowed. Allowed types: PDF, JPEG, PNG, GIF, DOC, DOCX");
        }

        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.contains("..")) {
            throw new RuntimeException("Invalid file name");
        }
    }

    /**
     * Generate unique file name
     */
    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Get file extension
     */
    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Check if file is an image
     */
    public boolean isImage(Document document) {
        String mimeType = document.getMimeType();
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Check if file is a PDF
     */
    public boolean isPdf(Document document) {
        return "application/pdf".equals(document.getMimeType());
    }
}
