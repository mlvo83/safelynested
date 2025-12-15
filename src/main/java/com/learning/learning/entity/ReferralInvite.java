package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "referral_invites")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralInvite {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id", nullable = true)  // NOW NULLABLE
    private Referral referral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // Recipient information
    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "recipient_email", length = 100)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    // Invite details
    @Column(name = "invite_token", nullable = false, unique = true, length = 100)
    private String inviteToken;

    @Column(name = "invite_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InviteType inviteType;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Zip codes allowed for this referral
    @Column(name = "allowed_zip_codes", columnDefinition = "TEXT")
    private String allowedZipCodes;

    // Status tracking
    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Timestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;


    /**
     * The location selected by the participant
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_location_id")
    private CharityLocation selectedLocation;

    /**
     * When the participant selected the location
     */
    @Column(name = "location_selected_at")
    private LocalDateTime locationSelectedAt;

    /**
     * Optional notes from the participant
     */
    @Column(name = "participant_notes", length = 500)
    private String participantNotes;




    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (inviteToken == null) {
            inviteToken = UUID.randomUUID().toString();
        }
        if (expiresAt == null) {
            // Default expiration: 30 days
            expiresAt = LocalDateTime.now().plusDays(30);
        }
    }

    // Enums
    public enum InviteType {
        EMAIL,
        SMS,
        BOTH
    }

    public enum InviteStatus {
        PENDING,
        SENT,
        OPENED,
        COMPLETED,
        EXPIRED,
        CANCELLED
    }

    // Helper methods
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public String getInviteUrl() {
        return "/referral/invite/" + inviteToken;
    }

    public CharityLocation getSelectedLocation() {
        return selectedLocation;
    }

    public void setSelectedLocation(CharityLocation selectedLocation) {
        this.selectedLocation = selectedLocation;
    }

    public LocalDateTime getLocationSelectedAt() {
        return locationSelectedAt;
    }

    public void setLocationSelectedAt(LocalDateTime locationSelectedAt) {
        this.locationSelectedAt = locationSelectedAt;
    }

    public String getParticipantNotes() {
        return participantNotes;
    }

    public void setParticipantNotes(String participantNotes) {
        this.participantNotes = participantNotes;
    }

    /**
     * Helper method to check if participant has selected a location
     */
    public boolean hasSelectedLocation() {
        return selectedLocation != null;
    }

    public Charity getCharity() {
        // Return direct charity if set, otherwise get from referral
        if (charity != null) {
            return charity;
        }
        return referral != null ? referral.getCharity() : null;
    }

    public void setCharity(Charity charity) {
        this.charity = charity;
    }

    /**
     * Helper to get charity ID regardless of how invite was created
     */
    public Long getCharityId() {
        Charity c = getCharity();
        return c != null ? c.getId() : null;
    }
}
