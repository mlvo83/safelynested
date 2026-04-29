-- =============================================
-- LOCATION PARTNER MIGRATION
-- Feature: Self-service Location Partner accounts,
--          partner-owned locations, and availability
--          windows.
--
-- Design note:
-- The existing stay_partner_applications table is
-- reused as the single entry point for partner
-- applications. On admin approval, an applicant can
-- now be approved either as a passive CharityLocation
-- (existing behavior) OR as a self-service Location
-- Partner — which creates a User account, a
-- LocationPartner profile, and a RegistrationToken.
--
-- This migration only creates the net-new tables
-- needed for the partner dashboard and availability.
-- =============================================

-- 1. Seed LOCATION_PARTNER role (idempotent)
INSERT INTO roles (name) VALUES ('ROLE_LOCATION_PARTNER')
ON CONFLICT (name) DO NOTHING;

-- 2. Location Partners (created when a stay_partner_application
--    is approved as "self-service"; links to a User account)
CREATE TABLE location_partners (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT UNIQUE REFERENCES users(id),
    stay_partner_application_id BIGINT REFERENCES stay_partner_applications(id),
    applicant_type              VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    business_name               VARCHAR(200),
    contact_email               VARCHAR(100) NOT NULL,
    contact_phone               VARCHAR(20),
    ein_tax_id                  VARCHAR(20),
    is_verified                 BOOLEAN DEFAULT TRUE,
    is_active                   BOOLEAN DEFAULT TRUE,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lp_user ON location_partners(user_id);
CREATE INDEX idx_lp_email ON location_partners(contact_email);
CREATE INDEX idx_lp_application ON location_partners(stay_partner_application_id);

-- 3. Partner Locations (owned by a LocationPartner, distinct from charity_locations)
CREATE TABLE partner_locations (
    id                          BIGSERIAL PRIMARY KEY,
    location_partner_id         BIGINT NOT NULL REFERENCES location_partners(id),
    name                        VARCHAR(200) NOT NULL,
    property_type               VARCHAR(30),
    address                     VARCHAR(500) NOT NULL,
    city                        VARCHAR(100) NOT NULL,
    state                       VARCHAR(50) NOT NULL,
    zip_code                    VARCHAR(20) NOT NULL,
    country                     VARCHAR(100) DEFAULT 'USA',
    number_of_bedrooms          INTEGER,
    max_guests                  INTEGER,
    nightly_rate                DECIMAL(10,2),
    amenities                   TEXT,
    accessibility_features      TEXT,
    pets_allowed                BOOLEAN DEFAULT FALSE,
    description                 TEXT,
    is_active                   BOOLEAN DEFAULT TRUE,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pl_partner ON partner_locations(location_partner_id);
CREATE INDEX idx_pl_zip ON partner_locations(zip_code);
CREATE INDEX idx_pl_active ON partner_locations(is_active);

-- 4. Location Availability windows (dates/times a location is empty/available)
CREATE TABLE location_availability (
    id                          BIGSERIAL PRIMARY KEY,
    partner_location_id         BIGINT NOT NULL REFERENCES partner_locations(id),
    start_date                  DATE NOT NULL,
    end_date                    DATE NOT NULL,
    start_time                  TIME,
    end_time                    TIME,
    status                      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    notes                       TEXT,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_la_location ON location_availability(partner_location_id);
CREATE INDEX idx_la_date_range ON location_availability(start_date, end_date);
CREATE INDEX idx_la_status ON location_availability(status);
