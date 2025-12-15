package com.learning.learning.dto;

import com.learning.learning.entity.Referral;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralDto {

    // ========================================
    // ID AND REFERENCE FIELDS
    // ========================================

    private Long id;
    private String referralNumber;

    // ========================================
    // CHARITY INFO
    // ========================================

    private Long charityId;
    private String charityName;

    // ========================================
    // PARTICIPANT INFO (with validation)
    // ========================================

    @NotBlank(message = "Participant name is required")
    private String participantName;

    @Email(message = "Invalid email format")
    private String participantEmail;

    private String participantPhone;

    @Min(value = 0, message = "Age must be positive")
    private Integer participantAge;

    @NotBlank(message = "Needs description is required")
    private String needsDescription;

    // ========================================
    // REFERRAL DETAILS
    // ========================================

    @NotBlank(message = "Urgency level is required")
    private String urgencyLevel; // LOW, MEDIUM, HIGH, URGENT

    private Referral.UrgencyLevel urgencyLevelEnum;
    private String urgencyLevelDisplay;

    private String referredByCharity; // Deprecated - use charity relationship
    private String referredByUsername;
    private String referredByFullName;

    // ========================================
    // STATUS
    // ========================================

    private Referral.ReferralStatus status;
    private String statusDisplay;

    // ========================================
    // DOCUMENT TRACKING
    // ========================================

    private Boolean documentsRequired;
    private Boolean documentsUploaded;
    private Integer documentCount;

    // ========================================
    // ZIP CODES
    // ========================================

    private String allowedZipCodes;

    // ========================================
    // APPROVAL INFO
    // ========================================

    private LocalDateTime approvedAt;
    private String approvedByUsername;
    private String facilitatorNotes;
    private String rejectedReason;

    // ========================================
    // TIMESTAMPS
    // ========================================

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdAtDisplay;
    private String updatedAtDisplay;

    // ========================================
    // COMPUTED FIELDS
    // ========================================

    private boolean editable;
    private boolean canBeApproved;
    private int inviteCount;

    // ========================================
    // FACTORY METHODS
    // ========================================

    /**
     * Create DTO from Referral entity (for display)
     */
    public static ReferralDto fromEntity(Referral referral) {
        if (referral == null) {
            return null;
        }

        ReferralDto dto = new ReferralDto();
        dto.setId(referral.getId());
        dto.setReferralNumber(referral.getReferralNumber());

        // Charity info
        if (referral.getCharity() != null) {
            dto.setCharityId(referral.getCharity().getId());
            dto.setCharityName(referral.getCharity().getCharityName());
        }

        // Participant info
        dto.setParticipantName(referral.getParticipantName());
        dto.setParticipantAge(referral.getParticipantAge());
        dto.setParticipantEmail(referral.getParticipantEmail());
        dto.setParticipantPhone(referral.getParticipantPhone());
        dto.setNeedsDescription(referral.getNeedsDescription());

        // Referred by
        dto.setReferredByCharity(referral.getReferredByCharity());
        if (referral.getReferredByUser() != null) {
            dto.setReferredByUsername(referral.getReferredByUser().getUsername());
            dto.setReferredByFullName(referral.getReferredByUser().getFullName());
        }

        // Urgency - handle both string and enum
        if (referral.getUrgencyLevel() != null) {
            dto.setUrgencyLevelEnum(referral.getUrgencyLevel());
            dto.setUrgencyLevel(referral.getUrgencyLevel().name());
            dto.setUrgencyLevelDisplay(formatUrgencyLevel(referral.getUrgencyLevel()));
        }

        // Status
        dto.setStatus(referral.getStatus());
        if (referral.getStatus() != null) {
            dto.setStatusDisplay(formatStatus(referral.getStatus()));
        }

        // Documents
        dto.setDocumentsRequired(referral.getDocumentsRequired());
        dto.setDocumentsUploaded(referral.getDocumentsUploaded());

        // Zip codes
        dto.setAllowedZipCodes(referral.getAllowedZipCodes());

        // Approval info
        dto.setApprovedAt(referral.getApprovedAt());
        if (referral.getApprovedByUser() != null) {
            dto.setApprovedByUsername(referral.getApprovedByUser().getUsername());
        }
        dto.setFacilitatorNotes(referral.getFacilitatorNotes());
        dto.setRejectedReason(referral.getRejectedReason());

        // Timestamps
        dto.setCreatedAt(referral.getCreatedAt());
        dto.setUpdatedAt(referral.getUpdatedAt());
        dto.setCreatedAtDisplay(formatDateTime(referral.getCreatedAt()));
        dto.setUpdatedAtDisplay(formatDateTime(referral.getUpdatedAt()));

        // Computed fields
        dto.setEditable(referral.isEditable());
        dto.setCanBeApproved(referral.canBeApproved());

        return dto;
    }

    /**
     * Create DTO with document and invite counts
     */
    public static ReferralDto fromEntity(Referral referral, int documentCount, int inviteCount) {
        ReferralDto dto = fromEntity(referral);
        if (dto != null) {
            dto.setDocumentCount(documentCount);
            dto.setInviteCount(inviteCount);
        }
        return dto;
    }

    /**
     * Convert DTO to new Referral entity (for creation from form)
     */
    public Referral toEntity() {
        Referral referral = new Referral();
        referral.setParticipantName(this.participantName);
        referral.setParticipantAge(this.participantAge);
        referral.setParticipantEmail(this.participantEmail);
        referral.setParticipantPhone(this.participantPhone);
        referral.setNeedsDescription(this.needsDescription);
        referral.setReferredByCharity(this.referredByCharity);
        referral.setDocumentsRequired(this.documentsRequired);
        referral.setAllowedZipCodes(this.allowedZipCodes);

        // Convert urgency level string to enum
        if (this.urgencyLevel != null && !this.urgencyLevel.isEmpty()) {
            try {
                referral.setUrgencyLevel(Referral.UrgencyLevel.valueOf(this.urgencyLevel.toUpperCase()));
            } catch (IllegalArgumentException e) {
                referral.setUrgencyLevel(Referral.UrgencyLevel.MEDIUM);
            }
        }

        return referral;
    }

    /**
     * Update existing Referral entity from DTO
     */
    public void updateEntity(Referral referral) {
        if (this.participantName != null) {
            referral.setParticipantName(this.participantName);
        }
        if (this.participantAge != null) {
            referral.setParticipantAge(this.participantAge);
        }
        if (this.participantEmail != null) {
            referral.setParticipantEmail(this.participantEmail);
        }
        if (this.participantPhone != null) {
            referral.setParticipantPhone(this.participantPhone);
        }
        if (this.needsDescription != null) {
            referral.setNeedsDescription(this.needsDescription);
        }
        if (this.urgencyLevel != null && !this.urgencyLevel.isEmpty()) {
            try {
                referral.setUrgencyLevel(Referral.UrgencyLevel.valueOf(this.urgencyLevel.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Keep existing value if invalid
            }
        }
        if (this.documentsRequired != null) {
            referral.setDocumentsRequired(this.documentsRequired);
        }
        if (this.allowedZipCodes != null) {
            referral.setAllowedZipCodes(this.allowedZipCodes);
        }
    }

    // ========================================
    // DISPLAY HELPER METHODS
    // ========================================

    private static String formatStatus(Referral.ReferralStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String formatUrgencyLevel(Referral.UrgencyLevel level) {
        if (level == null) return "";
        return switch (level) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
            case URGENT -> "Urgent";
        };
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return dateTime.format(formatter);
    }

    /**
     * Get CSS class for status badge
     */
    public String getStatusBadgeClass() {
        if (status == null) return "bg-secondary";
        return switch (status) {
            case PENDING -> "bg-warning text-dark";
            case APPROVED -> "bg-success";
            case REJECTED -> "bg-danger";
            case COMPLETED -> "bg-info";
            case CANCELLED -> "bg-secondary";
        };
    }

    /**
     * Get CSS class for urgency badge
     */
    public String getUrgencyBadgeClass() {
        if (urgencyLevelEnum == null && urgencyLevel != null) {
            try {
                urgencyLevelEnum = Referral.UrgencyLevel.valueOf(urgencyLevel.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "bg-secondary";
            }
        }
        if (urgencyLevelEnum == null) return "bg-secondary";
        return switch (urgencyLevelEnum) {
            case LOW -> "bg-success";
            case MEDIUM -> "bg-info";
            case HIGH -> "bg-warning text-dark";
            case URGENT -> "bg-danger";
        };
    }

    /**
     * Get icon class for status
     */
    public String getStatusIcon() {
        if (status == null) return "fa-circle-question";
        return switch (status) {
            case PENDING -> "fa-clock";
            case APPROVED -> "fa-circle-check";
            case REJECTED -> "fa-circle-xmark";
            case COMPLETED -> "fa-flag-checkered";
            case CANCELLED -> "fa-ban";
        };
    }

    /**
     * Get icon class for urgency
     */
    public String getUrgencyIcon() {
        if (urgencyLevelEnum == null && urgencyLevel != null) {
            try {
                urgencyLevelEnum = Referral.UrgencyLevel.valueOf(urgencyLevel.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "fa-circle";
            }
        }
        if (urgencyLevelEnum == null) return "fa-circle";
        return switch (urgencyLevelEnum) {
            case LOW -> "fa-arrow-down";
            case MEDIUM -> "fa-minus";
            case HIGH -> "fa-arrow-up";
            case URGENT -> "fa-exclamation-triangle";
        };
    }

    /**
     * Check if referral is in a final state
     */
    public boolean isFinalState() {
        return status == Referral.ReferralStatus.COMPLETED ||
                status == Referral.ReferralStatus.CANCELLED ||
                status == Referral.ReferralStatus.REJECTED;
    }

    /**
     * Get participant contact info for display
     */
    public String getContactDisplay() {
        StringBuilder sb = new StringBuilder();
        if (participantEmail != null && !participantEmail.isEmpty()) {
            sb.append(participantEmail);
        }
        if (participantPhone != null && !participantPhone.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(participantPhone);
        }
        return sb.length() > 0 ? sb.toString() : "No contact info";
    }
}
