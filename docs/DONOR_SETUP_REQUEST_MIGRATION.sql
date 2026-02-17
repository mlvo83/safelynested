-- =============================================
-- Donor Setup Request Migration
-- Feature: "Set Up a Donor" for Charity Partners
-- Database: PostgreSQL
-- =============================================

CREATE TABLE IF NOT EXISTS donor_setup_requests (
    id BIGSERIAL PRIMARY KEY,

    -- Request identification
    request_number VARCHAR(20) NOT NULL UNIQUE,

    -- Request type and status
    request_type VARCHAR(20) NOT NULL,   -- LINK_EXISTING, CREATE_NEW
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED

    -- Who is requesting and for which charity
    charity_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,

    -- For LINK_EXISTING requests: reference to existing donor
    existing_donor_id BIGINT NULL,

    -- For CREATE_NEW requests: new donor information
    donor_type VARCHAR(20) NULL,  -- INDIVIDUAL, BUSINESS
    first_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    email VARCHAR(100) NULL,
    phone VARCHAR(20) NULL,

    -- Address fields
    street_address VARCHAR(500) NULL,
    city VARCHAR(100) NULL,
    state VARCHAR(50) NULL,
    zip_code VARCHAR(20) NULL,

    -- Business-specific fields
    company_name VARCHAR(200) NULL,
    contact_name VARCHAR(200) NULL,
    tax_id VARCHAR(20) NULL,

    -- Preferences
    anonymity_preference VARCHAR(20) NULL,  -- FULL_NAME, FIRST_NAME_ONLY, ANONYMOUS
    preferred_contact_method VARCHAR(20) NULL,  -- EMAIL, PHONE, MAIL, NO_CONTACT

    -- Notes
    notes TEXT NULL,
    admin_notes TEXT NULL,
    rejection_reason TEXT NULL,

    -- Review tracking
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMP NULL,

    -- Result: the donor that was created or linked
    result_donor_id BIGINT NULL,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    CONSTRAINT fk_dsr_charity FOREIGN KEY (charity_id) REFERENCES charities(id),
    CONSTRAINT fk_dsr_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_dsr_existing_donor FOREIGN KEY (existing_donor_id) REFERENCES donors(id),
    CONSTRAINT fk_dsr_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_dsr_result_donor FOREIGN KEY (result_donor_id) REFERENCES donors(id)
);

-- Index for common queries
CREATE INDEX IF NOT EXISTS idx_dsr_charity_id ON donor_setup_requests(charity_id);
CREATE INDEX IF NOT EXISTS idx_dsr_status ON donor_setup_requests(status);
CREATE INDEX IF NOT EXISTS idx_dsr_charity_status ON donor_setup_requests(charity_id, status);
CREATE INDEX IF NOT EXISTS idx_dsr_request_number ON donor_setup_requests(request_number);
