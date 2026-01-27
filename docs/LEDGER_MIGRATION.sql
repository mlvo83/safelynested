-- LEDGER_MIGRATION.sql
-- Creates the tables for the double-entry accounting ledger system
-- Run this script to add ledger functionality

-- =====================================================
-- ACCOUNTS TABLE (Chart of Accounts)
-- =====================================================
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_code VARCHAR(50) NOT NULL UNIQUE,
    account_name VARCHAR(200) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    parent_account_id BIGINT REFERENCES accounts(id),
    charity_id BIGINT REFERENCES charities(id),
    is_system_account BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    current_balance DECIMAL(15, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_accounts_code ON accounts(account_code);
CREATE INDEX idx_accounts_type ON accounts(account_type);
CREATE INDEX idx_accounts_charity ON accounts(charity_id);

-- =====================================================
-- LEDGER_TRANSACTIONS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS ledger_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_code VARCHAR(50) NOT NULL UNIQUE,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    description VARCHAR(500) NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    charity_id BIGINT REFERENCES charities(id),
    created_by BIGINT REFERENCES users(id),
    total_amount DECIMAL(15, 2),
    notes TEXT,
    is_reversed BOOLEAN DEFAULT FALSE,
    reversal_of_id BIGINT REFERENCES ledger_transactions(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ledger_trans_code ON ledger_transactions(transaction_code);
CREATE INDEX idx_ledger_trans_type ON ledger_transactions(transaction_type);
CREATE INDEX idx_ledger_trans_date ON ledger_transactions(transaction_date);
CREATE INDEX idx_ledger_trans_ref ON ledger_transactions(reference_type, reference_id);
CREATE INDEX idx_ledger_trans_charity ON ledger_transactions(charity_id);

-- =====================================================
-- LEDGER_ENTRIES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES ledger_transactions(id),
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(10) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    running_balance DECIMAL(15, 2),
    memo VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ledger_entries_trans ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_type ON ledger_entries(entry_type);

-- =====================================================
-- INSERT SYSTEM ACCOUNTS
-- =====================================================
INSERT INTO accounts (account_code, account_name, account_type, is_system_account, description)
VALUES
    ('1000', 'Cash / Bank', 'ASSET', TRUE, 'Main operating cash account'),
    ('1100', 'Accounts Receivable', 'ASSET', TRUE, 'Pledged donations not yet received'),
    ('2000', 'Funds Held for Charities', 'LIABILITY', TRUE, 'Pooled funds held on behalf of charities'),
    ('2100', 'Funds Allocated to Situations', 'LIABILITY', TRUE, 'Funds committed to specific situations'),
    ('4000', 'Platform Fee Revenue (7%)', 'REVENUE', TRUE, 'Revenue from platform fees'),
    ('4100', 'Facilitator Fee Revenue (3%)', 'REVENUE', TRUE, 'Revenue from facilitator fees'),
    ('5000', 'Housing Disbursements', 'EXPENSE', TRUE, 'Payments made for housing'),
    ('5100', 'Refunds Issued', 'EXPENSE', TRUE, 'Donation refunds')
ON CONFLICT (account_code) DO NOTHING;

-- =====================================================
-- ADD LEDGER REFERENCES TO EXISTING TABLES
-- =====================================================

-- Add ledger transaction reference to donations
ALTER TABLE donations
ADD COLUMN IF NOT EXISTS ledger_transaction_id BIGINT REFERENCES ledger_transactions(id);

-- Add ledger transaction reference to situation_funding
ALTER TABLE situation_funding
ADD COLUMN IF NOT EXISTS allocation_transaction_id BIGINT REFERENCES ledger_transactions(id);

-- Add ledger transaction reference to bookings
ALTER TABLE bookings
ADD COLUMN IF NOT EXISTS disbursement_transaction_id BIGINT REFERENCES ledger_transactions(id);

-- =====================================================
-- VERIFICATION QUERY
-- =====================================================
SELECT
    'accounts' as table_name,
    COUNT(*) as count
FROM accounts
UNION ALL
SELECT 'ledger_transactions', COUNT(*) FROM ledger_transactions
UNION ALL
SELECT 'ledger_entries', COUNT(*) FROM ledger_entries;
