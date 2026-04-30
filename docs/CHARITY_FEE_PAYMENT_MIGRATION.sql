-- =====================================================
-- CHARITY FEE PAYMENT MIGRATION
-- Adds fields for tracking fee payments when charities
-- record donations received from donors
-- =====================================================

-- 1. Date the charity actually received the donation from the donor
ALTER TABLE donations ADD COLUMN date_received DATE;

-- 2. Stripe tracking for the fee payment session (separate from donation payment)
ALTER TABLE donations ADD COLUMN fee_stripe_session_id VARCHAR(255);
ALTER TABLE donations ADD COLUMN fee_stripe_payment_intent_id VARCHAR(255);

-- 3. Indexes for fee payment lookups
CREATE UNIQUE INDEX idx_donations_fee_stripe_session ON donations(fee_stripe_session_id)
    WHERE fee_stripe_session_id IS NOT NULL;
