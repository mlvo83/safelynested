package com.learning.learning.dto;

import com.learning.learning.entity.ReferralInvite;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteDto {

    private Long id;
    private String inviteToken;

    // Referral info
    private Long referralId;
    private String referralNumber;
    private String participantName;

    // Charity info
    private Long charityId;
    private String charityName;

    // Creator info
    private Long createdById;
    private String createdByUsername;
    private String createdByFullName;

    // Recipient information
    private String recipientName;
    private String recipientEmail;
    private String recipientPhone;

    // Invite details
    private ReferralInvite.InviteType inviteType;
    private String inviteTypeDisplay;
    private String message;
    private String allowedZipCodes;

    // Status tracking
    private ReferralInvite.InviteStatus status;
    private String statusDisplay;
    private LocalDateTime sentAt;
    private LocalDateTime openedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;

    // Timestamp
    private LocalDateTime createdAt;

    // Computed fields
    private boolean expired;
    private boolean canResend;
    private boolean canCancel;
    private String inviteUrl;
    private long daysUntilExpiry;
    private String expiryDisplay;

    // Display formatted dates
    private String sentAtDisplay;
    private String openedAtDisplay;
    private String completedAtDisplay;
    private String expiresAtDisplay;
    private String createdAtDisplay;

    // ========================================
    // FACTORY METHODS
    // ========================================

    /**
     * Create DTO from ReferralInvite entity
     */
    public static InviteDto fromEntity(ReferralInvite invite) {
        if (invite == null) {
            return null;
        }

        InviteDto dto = new InviteDto();
        dto.setId(invite.getId());
        dto.setInviteToken(invite.getInviteToken());

        // Referral info
        if (invite.getReferral() != null) {
            dto.setReferralId(invite.getReferral().getId());
            dto.setReferralNumber(invite.getReferral().getReferralNumber());
            dto.setParticipantName(invite.getReferral().getParticipantName());
        }

        // Charity info
        if (invite.getCharity() != null) {
            dto.setCharityId(invite.getCharity().getId());
            dto.setCharityName(invite.getCharity().getCharityName());
        }

        // Creator info
        if (invite.getCreatedBy() != null) {
            dto.setCreatedById(invite.getCreatedBy().getId());
            dto.setCreatedByUsername(invite.getCreatedBy().getUsername());
            dto.setCreatedByFullName(invite.getCreatedBy().getFullName());
        }

        // Recipient info
        dto.setRecipientName(invite.getRecipientName());
        dto.setRecipientEmail(invite.getRecipientEmail());
        dto.setRecipientPhone(invite.getRecipientPhone());

        // Invite details
        dto.setInviteType(invite.getInviteType());
        dto.setInviteTypeDisplay(formatInviteType(invite.getInviteType()));
        dto.setMessage(invite.getMessage());
        dto.setAllowedZipCodes(invite.getAllowedZipCodes());

        // Status
        dto.setStatus(invite.getStatus());
        dto.setStatusDisplay(formatStatus(invite.getStatus()));

        // Timestamps
        dto.setSentAt(invite.getSentAt());
        dto.setOpenedAt(invite.getOpenedAt());
        dto.setCompletedAt(invite.getCompletedAt());
        dto.setExpiresAt(invite.getExpiresAt());
        dto.setCreatedAt(invite.getCreatedAt());

        // Formatted dates
        dto.setSentAtDisplay(formatDateTime(invite.getSentAt()));
        dto.setOpenedAtDisplay(formatDateTime(invite.getOpenedAt()));
        dto.setCompletedAtDisplay(formatDateTime(invite.getCompletedAt()));
        dto.setExpiresAtDisplay(formatDate(invite.getExpiresAt()));
        dto.setCreatedAtDisplay(formatDateTime(invite.getCreatedAt()));

        // Computed fields
        dto.setExpired(invite.isExpired());
        dto.setCanResend(canResend(invite));
        dto.setCanCancel(canCancel(invite));
        dto.setInviteUrl(invite.getInviteUrl());

        // Expiry calculations
        if (invite.getExpiresAt() != null) {
            long days = ChronoUnit.DAYS.between(LocalDateTime.now(), invite.getExpiresAt());
            dto.setDaysUntilExpiry(Math.max(0, days));
            dto.setExpiryDisplay(formatExpiryDisplay(invite.getExpiresAt()));
        }

        return dto;
    }

    /**
     * Create a new ReferralInvite entity from DTO
     */
    public ReferralInvite toEntity() {
        ReferralInvite invite = new ReferralInvite();
        invite.setRecipientName(this.recipientName);
        invite.setRecipientEmail(this.recipientEmail);
        invite.setRecipientPhone(this.recipientPhone);
        invite.setInviteType(this.inviteType);
        invite.setMessage(this.message);
        invite.setAllowedZipCodes(this.allowedZipCodes);
        return invite;
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private static String formatInviteType(ReferralInvite.InviteType type) {
        if (type == null) return "";
        return switch (type) {
            case EMAIL -> "Email";
            case SMS -> "SMS";
            case BOTH -> "Email & SMS";
        };
    }

    private static String formatStatus(ReferralInvite.InviteStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Pending";
            case SENT -> "Sent";
            case OPENED -> "Opened";
            case COMPLETED -> "Completed";
            case EXPIRED -> "Expired";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return dateTime.format(formatter);
    }

    private static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        return dateTime.format(formatter);
    }

    private static String formatExpiryDisplay(LocalDateTime expiresAt) {
        if (expiresAt == null) return "";

        LocalDateTime now = LocalDateTime.now();
        if (expiresAt.isBefore(now)) {
            return "Expired";
        }

        long days = ChronoUnit.DAYS.between(now, expiresAt);
        if (days == 0) {
            long hours = ChronoUnit.HOURS.between(now, expiresAt);
            if (hours == 0) {
                return "Expires soon";
            }
            return "Expires in " + hours + " hour" + (hours == 1 ? "" : "s");
        } else if (days == 1) {
            return "Expires tomorrow";
        } else if (days < 7) {
            return "Expires in " + days + " days";
        } else {
            return "Expires " + formatDate(expiresAt);
        }
    }

    private static boolean canResend(ReferralInvite invite) {
        if (invite == null || invite.getStatus() == null) return false;
        return switch (invite.getStatus()) {
            case PENDING, SENT, EXPIRED -> true;
            case OPENED, COMPLETED, CANCELLED -> false;
        };
    }

    private static boolean canCancel(ReferralInvite invite) {
        if (invite == null || invite.getStatus() == null) return false;
        return switch (invite.getStatus()) {
            case PENDING, SENT, OPENED -> true;
            case COMPLETED, EXPIRED, CANCELLED -> false;
        };
    }

    /**
     * Get CSS class for status badge
     */
    public String getStatusBadgeClass() {
        if (status == null) return "bg-secondary";
        return switch (status) {
            case PENDING -> "bg-warning";
            case SENT -> "bg-info";
            case OPENED -> "bg-primary";
            case COMPLETED -> "bg-success";
            case EXPIRED -> "bg-secondary";
            case CANCELLED -> "bg-danger";
        };
    }

    /**
     * Get CSS class for invite type badge
     */
    public String getInviteTypeBadgeClass() {
        if (inviteType == null) return "bg-secondary";
        return switch (inviteType) {
            case EMAIL -> "bg-primary";
            case SMS -> "bg-success";
            case BOTH -> "bg-info";
        };
    }

    /**
     * Get icon class for invite type
     */
    public String getInviteTypeIcon() {
        if (inviteType == null) return "fa-envelope";
        return switch (inviteType) {
            case EMAIL -> "fa-envelope";
            case SMS -> "fa-mobile-screen";
            case BOTH -> "fa-paper-plane";
        };
    }

    /**
     * Get icon class for status
     */
    public String getStatusIcon() {
        if (status == null) return "fa-circle-question";
        return switch (status) {
            case PENDING -> "fa-clock";
            case SENT -> "fa-paper-plane";
            case OPENED -> "fa-envelope-open";
            case COMPLETED -> "fa-circle-check";
            case EXPIRED -> "fa-calendar-xmark";
            case CANCELLED -> "fa-ban";
        };
    }

    /**
     * Check if invite is in a final state
     */
    public boolean isFinalState() {
        return status == ReferralInvite.InviteStatus.COMPLETED ||
                status == ReferralInvite.InviteStatus.CANCELLED ||
                status == ReferralInvite.InviteStatus.EXPIRED;
    }

    /**
     * Get contact display (email or phone or both)
     */
    public String getContactDisplay() {
        StringBuilder sb = new StringBuilder();
        if (recipientEmail != null && !recipientEmail.isEmpty()) {
            sb.append(recipientEmail);
        }
        if (recipientPhone != null && !recipientPhone.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(recipientPhone);
        }
        return sb.toString();
    }
}