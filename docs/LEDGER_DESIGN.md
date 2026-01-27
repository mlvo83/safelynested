# SafelyNested Ledger System Design

## Overview

A double-entry accounting ledger to track all financial transactions with full visibility and audit trail.

## Core Principles

1. **Double-Entry**: Every transaction has balanced debits and credits
2. **Immutable**: Ledger entries are never modified or deleted (corrections are new entries)
3. **Auditable**: Complete history with timestamps and user attribution
4. **Real-time Balances**: Current fund availability always calculable

## Account Types

| Type | Description | Increases With |
|------|-------------|----------------|
| ASSET | Things we have (cash, receivables) | Debit |
| LIABILITY | Obligations (funds held for others) | Credit |
| REVENUE | Income earned (fees) | Credit |
| EXPENSE | Money paid out (disbursements) | Debit |

## Chart of Accounts

### System Accounts (Auto-created)
```
1000 - ASSET - Cash/Bank (main operating account)
1100 - ASSET - Accounts Receivable (pledged donations)

2000 - LIABILITY - Funds Held for Charities (pooled)
2100 - LIABILITY - Allocated to Situations (committed funds)

4000 - REVENUE - Platform Fee Revenue (7%)
4100 - REVENUE - Facilitator Fee Revenue (3%)

5000 - EXPENSE - Housing Disbursements
5100 - EXPENSE - Refunds Issued
```

### Per-Charity Accounts (Auto-created when charity added)
```
2000-{charityId} - LIABILITY - Charity Fund: {Charity Name}
```

## Transaction Types & Journal Entries

### 1. DONATION_RECEIVED
When a $1000 donation is received:
```
DEBIT   1000 Cash                    $1,000.00
CREDIT  2000-1 Charity Fund (ABC)      $900.00  (net amount)
CREDIT  4000 Platform Fee Revenue       $70.00  (7%)
CREDIT  4100 Facilitator Fee Revenue    $30.00  (3%)
```

### 2. FUND_ALLOCATED
When $500 allocated to a situation:
```
DEBIT   2000-1 Charity Fund (ABC)      $500.00
CREDIT  2100 Allocated to Situations   $500.00
```

### 3. FUND_DISBURSED
When $500 paid to hotel for booking:
```
DEBIT   2100 Allocated to Situations   $500.00
CREDIT  1000 Cash                      $500.00
```

### 4. DONATION_REFUND
If donation is refunded:
```
DEBIT   2000-1 Charity Fund (ABC)      $900.00
DEBIT   4000 Platform Fee Revenue       $70.00
DEBIT   4100 Facilitator Fee Revenue    $30.00
CREDIT  1000 Cash                    $1,000.00
```

## Entity Design

### Account
```java
- id: Long
- accountCode: String (unique, e.g., "2000-1")
- accountName: String
- accountType: Enum (ASSET, LIABILITY, REVENUE, EXPENSE)
- parentAccount: Account (for hierarchy)
- charity: Charity (nullable, for charity-specific accounts)
- isSystemAccount: Boolean
- isActive: Boolean
- createdAt: LocalDateTime
```

### LedgerTransaction
```java
- id: Long
- transactionCode: String (unique, e.g., "TXN-20260127-00001")
- transactionType: Enum (DONATION_RECEIVED, FUND_ALLOCATED, FUND_DISBURSED, REFUND, ADJUSTMENT)
- transactionDate: LocalDateTime
- description: String
- referenceType: String (e.g., "DONATION", "BOOKING")
- referenceId: Long (links to Donation.id, Booking.id, etc.)
- createdBy: User
- createdAt: LocalDateTime
- entries: List<LedgerEntry>
```

### LedgerEntry
```java
- id: Long
- transaction: LedgerTransaction
- account: Account
- entryType: Enum (DEBIT, CREDIT)
- amount: BigDecimal
- runningBalance: BigDecimal (account balance after this entry)
- memo: String
- createdAt: LocalDateTime
```

## Integration Points

### Existing Entities to Update

1. **Donation** - Add `ledgerTransactionId` to link to ledger
2. **SituationFunding** - Add `allocationTransactionId`
3. **Booking** - Add `disbursementTransactionId`

### Service Methods

```java
LedgerService {
    // Record transactions
    LedgerTransaction recordDonation(Donation donation);
    LedgerTransaction recordAllocation(SituationFunding funding);
    LedgerTransaction recordDisbursement(Booking booking, BigDecimal amount);
    LedgerTransaction recordRefund(Donation donation, String reason);

    // Queries
    BigDecimal getAccountBalance(Account account);
    BigDecimal getCharityAvailableFunds(Charity charity);
    List<LedgerEntry> getAccountHistory(Account account, LocalDate from, LocalDate to);

    // Reports
    TrialBalance generateTrialBalance(LocalDate asOf);
    List<LedgerTransaction> getTransactionsByReference(String refType, Long refId);
}
```

## Reports

1. **Trial Balance** - All accounts with debit/credit totals (must balance)
2. **Charity Fund Statement** - Per-charity: donations, allocations, disbursements, balance
3. **Transaction History** - Filterable list of all transactions
4. **Audit Log** - Who did what, when

## Implementation Phases

### Phase 1: Core Entities & Service
- [ ] Account entity
- [ ] LedgerTransaction entity
- [ ] LedgerEntry entity
- [ ] LedgerService with basic recording
- [ ] AccountRepository, LedgerTransactionRepository

### Phase 2: Integration with Donations
- [ ] Auto-record ledger entry when donation is created
- [ ] Link Donation to LedgerTransaction
- [ ] Calculate charity available funds from ledger

### Phase 3: Allocation & Disbursement
- [ ] Record allocation when SituationFunding created
- [ ] Record disbursement when booking is paid
- [ ] Track fund flow end-to-end

### Phase 4: Admin UI
- [ ] Chart of Accounts view
- [ ] Transaction history view
- [ ] Charity fund statement
- [ ] Trial balance report

### Phase 5: Validation & Reconciliation
- [ ] Balance validation (debits = credits)
- [ ] Reconciliation tools
- [ ] Discrepancy alerts

## Security Considerations

- Ledger entries are IMMUTABLE - no updates or deletes
- All entries require authenticated user attribution
- Admin-only access to ledger reports
- Corrections made via reversing entries, not modifications
