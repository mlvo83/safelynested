-- =====================================================
-- LEDGER PHASE 3 MIGRATION: Allocation & Disbursement Integration
-- =====================================================
-- This migration adds ledger transaction references to
-- SituationFunding and Booking tables to link allocations
-- and disbursements to their ledger entries.
-- =====================================================

-- Add ledger transaction reference to situation_funding table
ALTER TABLE situation_funding
ADD COLUMN IF NOT EXISTS ledger_transaction_id BIGINT;

-- Add ledger transaction reference to bookings table
ALTER TABLE bookings
ADD COLUMN IF NOT EXISTS ledger_transaction_id BIGINT;

-- Add foreign key constraints (using DO block to handle if already exists)
DO $$
BEGIN
    -- Add FK for situation_funding if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_situation_funding_ledger_transaction'
        AND table_name = 'situation_funding'
    ) THEN
        ALTER TABLE situation_funding
        ADD CONSTRAINT fk_situation_funding_ledger_transaction
        FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions(id);
    END IF;

    -- Add FK for bookings if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_bookings_ledger_transaction'
        AND table_name = 'bookings'
    ) THEN
        ALTER TABLE bookings
        ADD CONSTRAINT fk_bookings_ledger_transaction
        FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions(id);
    END IF;
END $$;

-- Create indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_situation_funding_ledger_txn
ON situation_funding(ledger_transaction_id);

CREATE INDEX IF NOT EXISTS idx_bookings_ledger_txn
ON bookings(ledger_transaction_id);

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Verify columns were added
SELECT
    table_name,
    column_name,
    data_type
FROM information_schema.columns
WHERE table_name IN ('situation_funding', 'bookings')
  AND column_name = 'ledger_transaction_id';

-- Verify foreign keys were added
SELECT
    tc.table_name,
    tc.constraint_name,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_name IN ('situation_funding', 'bookings')
  AND kcu.column_name = 'ledger_transaction_id';
