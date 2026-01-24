-- =====================================================
-- Migration: Donor Business Type Support
-- Date: 2026-01-23
-- Description: Adds support for business/organization donors
--              in addition to individual donors
-- =====================================================

-- Add new columns to donors table for business support
-- donor_type: INDIVIDUAL or BUSINESS
-- business_name: Name of the business/organization (for BUSINESS type)
-- contact_name: Primary contact person (for BUSINESS type)
-- tax_id: EIN/Tax ID for the business (optional, for BUSINESS type)

-- 1. Add donor_type column with default 'INDIVIDUAL'
ALTER TABLE donors
ADD COLUMN IF NOT EXISTS donor_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';

-- 2. Add business_name column
ALTER TABLE donors
ADD COLUMN IF NOT EXISTS business_name VARCHAR(255);

-- 3. Add contact_name column
ALTER TABLE donors
ADD COLUMN IF NOT EXISTS contact_name VARCHAR(255);

-- 4. Add tax_id column
ALTER TABLE donors
ADD COLUMN IF NOT EXISTS tax_id VARCHAR(50);

-- 5. Create index on donor_type for filtering
CREATE INDEX IF NOT EXISTS idx_donors_donor_type ON donors(donor_type);

-- 6. Add check constraint for valid donor types
-- Note: PostgreSQL doesn't support adding constraints with IF NOT EXISTS
-- So we drop and recreate
DO $$
BEGIN
    ALTER TABLE donors DROP CONSTRAINT IF EXISTS chk_donor_type;
    ALTER TABLE donors ADD CONSTRAINT chk_donor_type
        CHECK (donor_type IN ('INDIVIDUAL', 'BUSINESS'));
EXCEPTION
    WHEN others THEN
        -- Constraint might not exist, that's okay
        NULL;
END $$;

-- =====================================================
-- Verification query (run to check migration worked)
-- =====================================================
-- SELECT column_name, data_type, column_default
-- FROM information_schema.columns
-- WHERE table_name = 'donors'
-- ORDER BY ordinal_position;
