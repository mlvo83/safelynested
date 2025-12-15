package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    // Additional user information
    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Multi-tenant support - Link to charity for CHARITY_PARTNER users
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charity_id")
    private Charity charity;

    // Timestamps
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // Existing ManyToMany relationship with roles
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Existing role management methods
    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    // Helper methods for role checking
    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    public boolean isCharityPartner() {
        return hasRole("ROLE_CHARITY_PARTNER") || hasRole("CHARITY_PARTNER");
    }

    public boolean isFacilitator() {
        return hasRole("ROLE_FACILITATOR") || hasRole("FACILITATOR");
    }

    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN") || hasRole("ADMIN");
    }

    public String getPrimaryRole() {
        if (roles.isEmpty()) {
            return "USER";
        }
        // Return the first role, or the highest priority role
        if (isAdmin()) return "ADMIN";
        if (isFacilitator()) return "FACILITATOR";
        if (isCharityPartner()) return "CHARITY_PARTNER";
        return roles.iterator().next().getName();
    }

    public Set<String> getRoleNames() {
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    // Helper method for display name
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return username;
    }

    // Helper method to check if user belongs to a charity
    public boolean belongsToCharity(Long charityId) {
        return charity != null && charity.getId().equals(charityId);
    }
}