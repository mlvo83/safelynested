package com.learning.learning.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LedgerEntry entity - Individual debit or credit entry.
 * Part of a LedgerTransaction, representing one side of a double-entry.
 *
 * Ledger entries are IMMUTABLE - they should never be updated or deleted.
 * Corrections are made by creating reversing entries.
 */
@Entity
@Table(name = "ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "running_balance", precision = 15, scale = 2)
    private BigDecimal runningBalance;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Entry type - Debit or Credit
     */
    public enum EntryType {
        DEBIT("Dr"),
        CREDIT("Cr");

        private final String abbreviation;

        EntryType(String abbreviation) {
            this.abbreviation = abbreviation;
        }

        public String getAbbreviation() {
            return abbreviation;
        }
    }

    // Helper methods

    /**
     * Calculate the signed amount based on account type and entry type.
     * For reporting purposes where we need positive/negative representation.
     */
    public BigDecimal getSignedAmount() {
        boolean accountIncreasesWithDebit = account.isAssetOrExpense();

        if (entryType == EntryType.DEBIT) {
            return accountIncreasesWithDebit ? amount : amount.negate();
        } else {
            return accountIncreasesWithDebit ? amount.negate() : amount;
        }
    }

    /**
     * Get formatted entry for display (e.g., "Dr $100.00" or "Cr $50.00")
     */
    public String getFormattedEntry() {
        return String.format("%s $%,.2f", entryType.getAbbreviation(), amount);
    }

    // Builder-style methods for fluent creation
    public static LedgerEntry debit(Account account, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setEntryType(EntryType.DEBIT);
        entry.setAmount(amount);
        return entry;
    }

    public static LedgerEntry credit(Account account, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setEntryType(EntryType.CREDIT);
        entry.setAmount(amount);
        return entry;
    }

    public LedgerEntry withMemo(String memo) {
        this.memo = memo;
        return this;
    }
}
