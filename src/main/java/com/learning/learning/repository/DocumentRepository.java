package com.learning.learning.repository;


import com.learning.learning.entity.Charity;
import com.learning.learning.entity.Document;
import com.learning.learning.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Multi-tenant: Find documents by charity
    List<Document> findByCharity(Charity charity);

    List<Document> findByCharityId(Long charityId);

    List<Document> findByCharityIdOrderByUploadedAtDesc(Long charityId);

    // Find documents by referral
    List<Document> findByReferral(Referral referral);

    List<Document> findByReferralId(Long referralId);

    List<Document> findByReferralIdOrderByUploadedAtDesc(Long referralId);

    // Find documents by type
    List<Document> findByDocumentType(Document.DocumentType documentType);

    List<Document> findByCharityIdAndDocumentType(Long charityId, Document.DocumentType documentType);

    // Find verified/unverified documents
    List<Document> findByIsVerified(Boolean isVerified);

    List<Document> findByCharityIdAndIsVerified(Long charityId, Boolean isVerified);

    List<Document> findByReferralIdAndIsVerified(Long referralId, Boolean isVerified);

    // Multi-tenant safe: Find documents by referral and charity
    @Query("SELECT d FROM Document d WHERE d.referral.id = :referralId AND d.charity.id = :charityId")
    List<Document> findByReferralIdAndCharityId(
            @Param("referralId") Long referralId,
            @Param("charityId") Long charityId
    );

    @Query("SELECT d FROM Document d WHERE d.referral.id = :referralId AND d.charity.id = :charityId ORDER BY d.uploadedAt DESC")
    List<Document> findByReferralIdAndCharityIdOrderByUploadedAtDesc(
            @Param("referralId") Long referralId,
            @Param("charityId") Long charityId
    );

    // Find by file name (for duplicate checking)
    Optional<Document> findByFileNameAndCharityId(String fileName, Long charityId);

    Optional<Document> findByFilePath(String filePath);

    // Count documents
    Long countByCharityId(Long charityId);

    Long countByReferralId(Long referralId);

    Long countByCharityIdAndIsVerified(Long charityId, Boolean isVerified);

    Long countByReferralIdAndIsVerified(Long referralId, Boolean isVerified);

    // Count by document type
    Long countByCharityIdAndDocumentType(Long charityId, Document.DocumentType documentType);

    Long countByReferralIdAndDocumentType(Long referralId, Document.DocumentType documentType);

    // Check if referral has all required documents
    @Query("SELECT COUNT(d) FROM Document d WHERE d.referral.id = :referralId")
    Long countDocumentsByReferralId(@Param("referralId") Long referralId);

    @Query("SELECT COUNT(DISTINCT d.documentType) FROM Document d WHERE d.referral.id = :referralId")
    Long countDistinctDocumentTypesByReferralId(@Param("referralId") Long referralId);

    // Find recent documents for a charity
    @Query("SELECT d FROM Document d WHERE d.charity.id = :charityId ORDER BY d.uploadedAt DESC")
    List<Document> findRecentByCharityId(@Param("charityId") Long charityId);

    @Query("SELECT d FROM Document d WHERE d.charity.id = :charityId AND d.uploadedAt >= :since ORDER BY d.uploadedAt DESC")
    List<Document> findByCharityIdAndUploadedAtAfter(
            @Param("charityId") Long charityId,
            @Param("since") LocalDateTime since
    );

    // Search documents
    @Query("SELECT d FROM Document d WHERE d.charity.id = :charityId AND " +
            "(LOWER(d.fileName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Document> searchDocuments(
            @Param("charityId") Long charityId,
            @Param("searchTerm") String searchTerm
    );

    // Find documents pending verification
    @Query("SELECT d FROM Document d WHERE d.isVerified = false ORDER BY d.uploadedAt ASC")
    List<Document> findUnverifiedDocuments();

    @Query("SELECT d FROM Document d WHERE d.charity.id = :charityId AND d.isVerified = false ORDER BY d.uploadedAt ASC")
    List<Document> findUnverifiedDocumentsByCharityId(@Param("charityId") Long charityId);

    // Find all documents for facilitator review
    @Query("SELECT d FROM Document d ORDER BY d.uploadedAt DESC")
    List<Document> findAllOrderByUploadedAtDesc();

    // Statistics
    @Query("SELECT d.documentType, COUNT(d) FROM Document d WHERE d.charity.id = :charityId GROUP BY d.documentType")
    List<Object[]> countDocumentsByTypeForCharity(@Param("charityId") Long charityId);
}