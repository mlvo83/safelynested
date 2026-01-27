# SafelyNested Ledger System - User Guide

## Overview

The SafelyNested ledger is a **double-entry accounting system** that tracks every dollar flowing through the platform. It provides full visibility into donations, fees, fund allocations, and disbursements.

### Why Double-Entry?

In double-entry accounting, every transaction has two sides that must balance:
- **Debits** = Money coming in or expenses going out
- **Credits** = Money owed to others or revenue earned

This ensures accuracy - if debits don't equal credits, something is wrong.

---

## Account Structure

### What is an Account?

An account is like a bucket that tracks a specific type of money. Each account has:
- **Code** - Unique identifier (e.g., "1000", "2000-1")
- **Name** - Human-readable description
- **Type** - Asset, Liability, Revenue, or Expense
- **Balance** - Current amount in the account

### Account Types

| Type | What It Represents | Examples |
|------|-------------------|----------|
| **Asset** | Money we have | Cash, Accounts Receivable |
| **Liability** | Money we owe to others | Funds held for charities |
| **Revenue** | Income earned | Platform fees, Facilitator fees |
| **Expense** | Money paid out | Housing disbursements, Refunds |

### System Accounts (Pre-created)

These accounts are created automatically and shared across all charities:

| Code | Name | Type | Purpose |
|------|------|------|---------|
| 1000 | Cash / Bank | Asset | Main operating account - all money received |
| 1100 | Accounts Receivable | Asset | Pledged donations not yet received |
| 2000 | Funds Held for Charities | Liability | Parent account for charity funds |
| 2100 | Funds Allocated to Situations | Liability | Money committed to specific situations |
| 4000 | Platform Fee Revenue (7%) | Revenue | SafelyNested's operating revenue |
| 4100 | Facilitator Fee Revenue (3%) | Revenue | Anchor charity coordination fees |
| 5000 | Housing Disbursements | Expense | Payments made to housing locations |
| 5100 | Refunds Issued | Expense | Donation refunds |

### Charity Fund Accounts (Auto-created)

Each charity gets its own fund account, created automatically when they receive their first donation:

| Code | Name | Purpose |
|------|------|---------|
| 2000-1 | Charity Fund: Hope Shelter | Tracks funds for Charity ID 1 |
| 2000-2 | Charity Fund: Safe Haven | Tracks funds for Charity ID 2 |
| 2000-3 | Charity Fund: New Beginnings | Tracks funds for Charity ID 3 |

**Key Point:** One account per charity, NOT per donation. All donations to the same charity accumulate in that charity's fund account.

---

## Transaction Types

### 1. Donation Received

**When:** A donation is verified by an admin

**What happens:** The gross donation amount is split into:
- Net amount (90%) → Charity's fund account
- Platform fee (7%) → Platform revenue
- Facilitator fee (3%) → Facilitator revenue

**Example:** $1,000 donation to Hope Shelter

| Account | Debit | Credit |
|---------|-------|--------|
| 1000 - Cash | $1,000.00 | |
| 2000-1 - Charity Fund: Hope Shelter | | $900.00 |
| 4000 - Platform Fee Revenue | | $70.00 |
| 4100 - Facilitator Fee Revenue | | $30.00 |
| **Totals** | **$1,000.00** | **$1,000.00** |

Notice: Debits = Credits (balanced!)

### 2. Fund Allocated

**When:** Funds are assigned to a specific situation

**What happens:** Money moves from the charity's available funds to "allocated" status (committed but not yet spent).

**Example:** $500 allocated to a situation

| Account | Debit | Credit |
|---------|-------|--------|
| 2000-1 - Charity Fund: Hope Shelter | $500.00 | |
| 2100 - Funds Allocated to Situations | | $500.00 |
| **Totals** | **$500.00** | **$500.00** |

### 3. Fund Disbursed

**When:** Payment is made to a housing location for a booking

**What happens:** Money leaves the platform to pay for housing.

**Example:** $500 paid to hotel for booking

| Account | Debit | Credit |
|---------|-------|--------|
| 2100 - Funds Allocated to Situations | $500.00 | |
| 1000 - Cash | | $500.00 |
| **Totals** | **$500.00** | **$500.00** |

### 4. Donation Refund

**When:** A verified donation is refunded

**What happens:** Reverses the original donation entry.

**Example:** Refund of $1,000 donation

| Account | Debit | Credit |
|---------|-------|--------|
| 2000-1 - Charity Fund: Hope Shelter | $900.00 | |
| 4000 - Platform Fee Revenue | $70.00 | |
| 4100 - Facilitator Fee Revenue | $30.00 | |
| 1000 - Cash | | $1,000.00 |
| **Totals** | **$1,000.00** | **$1,000.00** |

---

## Money Flow Visualization

```
                    DONATION RECEIVED ($1,000)
                            │
                            ▼
                    ┌───────────────┐
                    │   1000 Cash   │  ← $1,000 comes in
                    │   (Asset)     │
                    └───────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
    │ Charity Fund│  │Platform Fee │  │Facilitator  │
    │   $900      │  │   $70       │  │ Fee $30     │
    │ (Liability) │  │ (Revenue)   │  │ (Revenue)   │
    └─────────────┘  └─────────────┘  └─────────────┘
            │
            ▼ ALLOCATION
    ┌─────────────┐
    │  Allocated  │  ← $500 committed to situation
    │   Funds     │
    │ (Liability) │
    └─────────────┘
            │
            ▼ DISBURSEMENT
    ┌─────────────┐
    │   1000 Cash │  ← $500 paid to hotel
    │   (Asset)   │
    └─────────────┘
```

---

## Reading the Ledger Dashboard

### Summary Cards

- **Total Assets:** Money the platform has (cash on hand)
- **Total Liabilities:** Money held for charities (must be available to spend)
- **Total Revenue:** Fees earned by the platform
- **Total Expenses:** Money paid out for housing

### Trial Balance

The trial balance shows:
- **Total Debits:** Sum of all debit entries ever recorded
- **Total Credits:** Sum of all credit entries ever recorded

**These must be equal!** If they're not, there's an error in the system.

### Chart of Accounts

Lists all accounts with their current balances. Use this to see:
- How much cash is on hand (Account 1000)
- How much each charity has available (Accounts 2000-X)
- Total revenue earned (Accounts 4000, 4100)

---

## Common Questions

### Q: Why don't I see ledger entries for a donation?

**A:** Ledger entries are only created when a donation is **verified**, not when it's first recorded. Unverified donations don't affect the books.

### Q: What's the difference between "Charity Fund" and "Allocated Funds"?

**A:**
- **Charity Fund (2000-X):** Available balance that can be used for any situation
- **Allocated Funds (2100):** Money committed to a specific situation but not yet paid out

### Q: Can ledger entries be edited or deleted?

**A:** No. Ledger entries are **immutable** (cannot be changed). This is intentional for audit purposes. If there's an error, it must be corrected with a new reversing entry.

### Q: How do I know if the books are accurate?

**A:** Check the Trial Balance. If Total Debits = Total Credits, the books are mathematically balanced. Regular reconciliation with bank statements confirms accuracy.

### Q: What happens if a charity is deleted?

**A:** Their fund account remains in the ledger for historical records. The account can be marked inactive but the transaction history is preserved.

---

## Glossary

| Term | Definition |
|------|------------|
| **Debit (Dr)** | Left side of an entry; increases assets/expenses, decreases liabilities/revenue |
| **Credit (Cr)** | Right side of an entry; increases liabilities/revenue, decreases assets/expenses |
| **Journal Entry** | A single transaction with its debits and credits |
| **Trial Balance** | Report showing all accounts and their balances; debits must equal credits |
| **Chart of Accounts** | Complete list of all accounts in the system |
| **Running Balance** | Account balance after each transaction |
| **Fund Accounting** | Tracking money separately for different purposes (e.g., per charity) |

---

## For Developers

See `LEDGER_DESIGN.md` for technical implementation details including:
- Entity relationships
- Service methods
- Database schema
- Integration points
