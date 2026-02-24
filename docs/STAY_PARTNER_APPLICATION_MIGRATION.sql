-- Stay Partner Application Migration
-- Run this manually against your PostgreSQL database

CREATE TABLE stay_partner_applications (
    id                      BIGSERIAL PRIMARY KEY,
    application_number      VARCHAR(20) NOT NULL UNIQUE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applicant_type          VARCHAR(20) NOT NULL,
    -- Individual
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    email                   VARCHAR(100) NOT NULL,
    phone                   VARCHAR(20),
    -- Business
    business_name           VARCHAR(200),
    contact_name            VARCHAR(200),
    business_email          VARCHAR(100),
    business_phone          VARCHAR(20),
    tax_id                  VARCHAR(20),
    -- Property
    property_type           VARCHAR(20) NOT NULL,
    property_name           VARCHAR(200) NOT NULL,
    number_of_bedrooms      INTEGER,
    max_guests              INTEGER,
    description             TEXT,
    -- Address
    street_address          VARCHAR(500) NOT NULL,
    city                    VARCHAR(100) NOT NULL,
    state                   VARCHAR(50) NOT NULL,
    zip_code                VARCHAR(20) NOT NULL,
    country                 VARCHAR(100) DEFAULT 'USA',
    -- Preferences
    nightly_rate            DECIMAL(10,2),
    amenities               TEXT,
    availability_notes      TEXT,
    pets_allowed            BOOLEAN DEFAULT FALSE,
    accessibility_features  TEXT,
    -- Communication
    preferred_contact_method VARCHAR(20) DEFAULT 'EMAIL',
    additional_notes        TEXT,
    -- Admin review
    reviewed_by_user_id     BIGINT REFERENCES users(id),
    reviewed_at             TIMESTAMP,
    admin_notes             TEXT,
    rejection_reason        TEXT,
    -- Result
    result_location_id      BIGINT REFERENCES charity_locations(id),
    -- Timestamps
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_spa_status ON stay_partner_applications(status);
CREATE INDEX idx_spa_email ON stay_partner_applications(email);
CREATE INDEX idx_spa_application_number ON stay_partner_applications(application_number);
