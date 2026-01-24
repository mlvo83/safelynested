-- =====================================================
-- Migration: Donor Dashboard Feature
-- Date: 2026-01-23
-- Description: Creates tables for donor management,
--              donations tracking, and situation
--              privacy abstraction layer
-- =====================================================

-- 1. Add DONOR role
INSERT INTO roles (name) VALUES ('ROLE_DONOR')
ON CONFLICT (name) DO NOTHING;

-- 2. Create donors table
CREATE TABLE IF NOT EXISTS donors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    is_verified BOOLEAN DEFAULT FALSE,
    verification_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_donors_user_id ON donors(user_id);

-- 3. Create donor_charities join table (donors can donate to multiple charities)
CREATE TABLE IF NOT EXISTS donor_charities (
    donor_id BIGINT NOT NULL REFERENCES donors(id) ON DELETE CASCADE,
    charity_id BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id),
    PRIMARY KEY (donor_id, charity_id)
);

CREATE INDEX IF NOT EXISTS idx_donor_charities_donor ON donor_charities(donor_id);
CREATE INDEX IF NOT EXISTS idx_donor_charities_charity ON donor_charities(charity_id);

-- 4. Create donations table
CREATE TABLE IF NOT EXISTS donations (
    id BIGSERIAL PRIMARY KEY,
    donor_id BIGINT NOT NULL REFERENCES donors(id) ON DELETE CASCADE,
    charity_id BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,

    -- Financial breakdown
    gross_amount DECIMAL(10,2) NOT NULL,
    platform_fee DECIMAL(10,2),
    facilitator_fee DECIMAL(10,2),
    processing_fee DECIMAL(10,2),
    net_amount DECIMAL(10,2),

    -- Nights calculation
    nights_funded INTEGER,
    avg_nightly_rate_at_donation DECIMAL(10,2),

    -- Status
    status VARCHAR(50) DEFAULT 'PENDING',
    verification_status VARCHAR(50) DEFAULT 'PENDING',

    -- Audit
    fee_structure_version VARCHAR(20) DEFAULT 'v1.0',
    donated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    recorded_by BIGINT REFERENCES users(id),
    verified_at TIMESTAMP,
    verified_by BIGINT REFERENCES users(id),
    notes TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_donations_donor ON donations(donor_id);
CREATE INDEX IF NOT EXISTS idx_donations_charity ON donations(charity_id);
CREATE INDEX IF NOT EXISTS idx_donations_status ON donations(status);
CREATE INDEX IF NOT EXISTS idx_donations_donated_at ON donations(donated_at);

-- 5. Create situations table (privacy abstraction layer)
CREATE TABLE IF NOT EXISTS situations (
    id BIGSERIAL PRIMARY KEY,
    charity_id BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,

    -- Generic description - NO PII
    -- Examples: "Family displaced due to eviction"
    --           "Emergency relocation due to domestic violence"
    description VARCHAR(500) NOT NULL,
    category VARCHAR(50),

    -- Internal link - NEVER exposed to donors
    referral_id BIGINT REFERENCES referrals(id) ON DELETE SET NULL,

    start_date DATE,
    end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_situations_charity ON situations(charity_id);
CREATE INDEX IF NOT EXISTS idx_situations_category ON situations(category);
CREATE INDEX IF NOT EXISTS idx_situations_active ON situations(is_active);
CREATE INDEX IF NOT EXISTS idx_situations_referral ON situations(referral_id);

-- 6. Create situation_funding table (links donations to situations)
CREATE TABLE IF NOT EXISTS situation_funding (
    id BIGSERIAL PRIMARY KEY,
    donation_id BIGINT NOT NULL REFERENCES donations(id) ON DELETE CASCADE,
    situation_id BIGINT NOT NULL REFERENCES situations(id) ON DELETE CASCADE,

    amount_allocated DECIMAL(10,2),
    nights_allocated INTEGER,
    nights_used INTEGER DEFAULT 0,

    allocated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    allocated_by BIGINT REFERENCES users(id),
    usage_explanation TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_situation_funding_donation ON situation_funding(donation_id);
CREATE INDEX IF NOT EXISTS idx_situation_funding_situation ON situation_funding(situation_id);

-- 7. Create nightly_rates table (for dynamic rate calculation)
CREATE TABLE IF NOT EXISTS nightly_rates (
    id BIGSERIAL PRIMARY KEY,
    location_id BIGINT NOT NULL REFERENCES charity_locations(id) ON DELETE CASCADE,

    rate DECIMAL(10,2) NOT NULL,
    effective_date DATE NOT NULL,
    end_date DATE,
    notes TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_nightly_rates_location ON nightly_rates(location_id);
CREATE INDEX IF NOT EXISTS idx_nightly_rates_effective ON nightly_rates(effective_date);

-- 8. Add donor_id to documents table (for donor document uploads)
ALTER TABLE documents
ADD COLUMN IF NOT EXISTS donor_id BIGINT REFERENCES donors(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_documents_donor ON documents(donor_id);

-- =====================================================
-- Rollback Script (if needed):
-- =====================================================
-- DROP INDEX IF EXISTS idx_documents_donor;
-- ALTER TABLE documents DROP COLUMN IF EXISTS donor_id;
-- DROP TABLE IF EXISTS nightly_rates;
-- DROP TABLE IF EXISTS situation_funding;
-- DROP TABLE IF EXISTS situations;
-- DROP TABLE IF EXISTS donations;
-- DROP TABLE IF EXISTS donor_charities;
-- DROP TABLE IF EXISTS donors;
-- DELETE FROM roles WHERE name = 'ROLE_DONOR';
