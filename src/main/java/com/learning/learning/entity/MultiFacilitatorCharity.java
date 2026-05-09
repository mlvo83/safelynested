package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Records that a particular user (with ROLE_MULTI_FACILITATOR) is
 * authorized to facilitate for a particular charity. The pair
 * (user_id, charity_id) is unique. Tracks who created the assignment
 * and when, for audit purposes.
 */
@Entity
@Table(
    name = "multi_facilitator_charities",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_multi_facilitator_user_charity",
        columnNames = {"user_id", "charity_id"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiFacilitatorCharity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charity_id", nullable = false)
    private Charity charity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
