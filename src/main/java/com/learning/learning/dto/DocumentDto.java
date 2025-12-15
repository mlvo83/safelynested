package com.learning.learning.dto;



import com.learning.learning.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {

    private Long id;

    // Referral info
    private Long referralId;
    private String referralNumber;

    // Charity info
    private Long charityId;
    private String charityName;

    // Uploader info
    private Long uploadedById;
    private String uploadedByUsername;
    private String uploadedByFullName;

    // File information
    private Document.DocumentType documentType;
    private String documentTypeDisplay;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileSizeDisplay;
    private String mimeType;

    // Metadata
    private String description;
    private Boolean isVerified;
    private LocalDateTime verifiedAt;
    private String verifiedByUsername;

    // Timestamp
    private LocalDateTime uploadedAt;
    private String uploadedAtDisplay;

    // Computed fields
    private boolean isImage;
    private boolean isPdf;
    private String fileExtension;
    private String downloadUrl;

    // ========================================
    // FACTORY METHODS
    // ========================================

    /**
     * Create DTO from Document entity
     */
    public static DocumentDto fromEntity(Document document) {
        if (document == null) {
            return null;
        }

        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());

        // Referral info
        if (document.getReferral() != null) {
            dto.setReferralId(document.getReferral().getId());
            dto.setReferralNumber(document.getReferral().getReferralNumber());
        }

        // Charity info
        if (document.getCharity() != null) {
            dto.setCharityId(document.getCharity().getId());
            dto.setCharityName(document.getCharity().getCharityName());
        }

        // Uploader info
        if (document.getUploadedBy() != null) {
            dto.setUploadedById(document.getUploadedBy().getId());
            dto.setUploadedByUsername(document.getUploadedBy().getUsername());
            dto.setUploadedByFullName(document.getUploadedBy().getFullName());
        }

        // File info
        dto.setDocumentType(document.getDocumentType());
        if (document.getDocumentType() != null) {
            dto.setDocumentTypeDisplay(document.getDocumentType().getDisplayName());
        }
        dto.setFileName(document.getFileName());
        dto.setFilePath(document.getFilePath());
        dto.setFileSize(document.getFileSize());
        dto.setFileSizeDisplay(formatFileSize(document.getFileSize()));
        dto.setMimeType(document.getMimeType());

        // Metadata
        dto.setDescription(document.getDescription());
        dto.setIsVerified(document.getIsVerified());
        dto.setVerifiedAt(document.getVerifiedAt());
        if (document.getVerifiedBy() != null) {
            dto.setVerifiedByUsername(document.getVerifiedBy().getUsername());
        }

        // Timestamp
        dto.setUploadedAt(document.getUploadedAt());
        if (document.getUploadedAt() != null) {
            dto.setUploadedAtDisplay(formatDateTime(document.getUploadedAt()));
        }

        // Computed fields
        dto.setImage(isImageMimeType(document.getMimeType()));
        dto.setPdf("application/pdf".equals(document.getMimeType()));
        dto.setFileExtension(extractFileExtension(document.getFileName()));
        dto.setDownloadUrl("/charity-partner/documents/" + document.getId() + "/download");

        return dto;
    }

    /**
     * Create a new Document entity from DTO (for uploads)
     */
    public Document toEntity() {
        Document document = new Document();
        document.setDocumentType(this.documentType);
        document.setFileName(this.fileName);
        document.setFilePath(this.filePath);
        document.setFileSize(this.fileSize);
        document.setMimeType(this.mimeType);
        document.setDescription(this.description);
        document.setIsVerified(false);
        return document;
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Format file size to human-readable string
     */
    private static String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        if (unitIndex == 0) {
            return String.format("%d %s", bytes, units[unitIndex]);
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * Format date time for display
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return dateTime.format(formatter);
    }

    /**
     * Check if mime type is an image
     */
    private static boolean isImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Extract file extension from filename
     */
    private static String extractFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Get Font Awesome icon class based on file type
     */
    public String getIconClass() {
        if (isPdf) {
            return "fa-file-pdf text-danger";
        }
        if (isImage) {
            return "fa-file-image text-primary";
        }

        String ext = getFileExtension();
        return switch (ext) {
            case "doc", "docx" -> "fa-file-word text-primary";
            case "xls", "xlsx" -> "fa-file-excel text-success";
            case "ppt", "pptx" -> "fa-file-powerpoint text-warning";
            case "txt" -> "fa-file-lines text-secondary";
            case "zip", "rar", "7z" -> "fa-file-zipper text-warning";
            default -> "fa-file text-secondary";
        };
    }

    /**
     * Get CSS class for verification badge
     */
    public String getVerificationBadgeClass() {
        return Boolean.TRUE.equals(isVerified) ? "bg-success" : "bg-warning";
    }

    /**
     * Get verification status text
     */
    public String getVerificationStatus() {
        return Boolean.TRUE.equals(isVerified) ? "Verified" : "Pending Verification";
    }

    /**
     * Check if document can be previewed in browser
     */
    public boolean isPreviewable() {
        return isImage || isPdf;
    }
}
