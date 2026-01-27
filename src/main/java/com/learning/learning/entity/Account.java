package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account entity for the chart of accounts.
 * Represents a single account in the double-entry ledger system.
 */
@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 50)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charity_id")
    private Charity charity;

    @Column(name = "is_system_account")
    private Boolean isSystemAccount = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "current_balance", precision = 15, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Account types following standard accounting principles.
     * ASSET and EXPENSE accounts increase with DEBIT.
     * LIABILITY, EQUITY, and REVENUE accounts increase with CREDIT.
     */
    public enum AccountType {
        ASSET("Asset", true),           // Debits increase
        LIABILITY("Liability", false),   // Credits increase
        EQUITY("Equity", false),         // Credits increase
        REVENUE("Revenue", false),       // Credits increase
        EXPENSE("Expense", true);        // Debits increase

        private final String displayName;
        private final boolean debitIncreases;

        AccountType(String displayName, boolean debitIncreases) {
            this.displayName = displayName;
            this.debitIncreases = debitIncreases;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isDebitIncreases() {
            return debitIncreases;
        }
    }

    // Helper methods
    public boolean isAssetOrExpense() {
        return accountType == AccountType.ASSET || accountType == AccountType.EXPENSE;
    }

    public String getFullAccountName() {
        return accountCode + " - " + accountName;
    }
}
