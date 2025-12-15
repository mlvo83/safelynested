package com.learning.learning.service;

import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Document;
import com.learning.learning.entity.Referral;
import com.learning.learning.entity.User;
import com.learning.learning.repository.DocumentRepository;
import com.learning.learning.repository.ReferralRepository;
import com.learning.learning.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ReferralRepository referralRepository;

    @Autowired
    private UserRepository userRepository;

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

        // Create directory structure: uploads/{charityId}/{referralId or 'general'}/
        String subDir = charity.getId() + "/" + (referralId != null ? referralId : "general");
        Path uploadPath = Paths.get(uploadDir, subDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Save file
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create document record
        Document document = new Document();
        document.setReferral(referral);
        document.setCharity(charity);
        document.setUploadedBy(user);
        document.setDocumentType(documentType);
        document.setFileName(originalFileName);
        document.setFilePath(filePath.toString());
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

    // ========================================
    // FILE DOWNLOAD
    // ========================================

    /**
     * Download document (with access control)
     */
    public Resource downloadDocument(Long documentId, String username) throws MalformedURLException {
        Document document = getDocumentWithAccessCheck(documentId, username);

        Path filePath = Paths.get(document.getFilePath());
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Could not read file: " + document.getFileName());
        }
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

        // Delete physical file
        Path filePath = Paths.get(document.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // Delete database record
        documentRepository.delete(document);
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
