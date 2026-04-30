-- =====================================================
-- STRIPE ONLINE DONATION MIGRATION
-- Makes donor_id nullable for anonymous donations
-- and adds Stripe tracking columns
-- =====================================================

-- 1. Make donor_id nullable (for anonymous online donations)
ALTER TABLE donations ALTER COLUMN donor_id DROP NOT NULL;

-- 2. Add Stripe tracking fields
ALTER TABLE donations ADD COLUMN stripe_session_id VARCHAR(255);
ALTER TABLE donations ADD COLUMN stripe_payment_intent_id VARCHAR(255);

-- 3. Add anonymous donor info (when no Donor entity exists)
ALTER TABLE donations ADD COLUMN donor_email VARCHAR(255);
ALTER TABLE donations ADD COLUMN donor_name VARCHAR(255);

-- 4. Add payment source tracking
ALTER TABLE donations ADD COLUMN payment_source VARCHAR(50) DEFAULT 'MANUAL';

-- 5. Add cover-fees tracking
ALTER TABLE donations ADD COLUMN cover_fees BOOLEAN DEFAULT FALSE;
ALTER TABLE donations ADD COLUMN original_amount DECIMAL(10,2);

-- 6. Create indexes for Stripe lookups
CREATE UNIQUE INDEX idx_donations_stripe_session ON donations(stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;
CREATE UNIQUE INDEX idx_donations_stripe_payment_intent ON donations(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
CREATE INDEX idx_donations_payment_source ON donations(payment_source);
