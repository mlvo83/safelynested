-- =============================================
-- CHARITY ONBOARDING MIGRATION
-- Feature: Self-service charity application,
--          registration tokens, and team invites
-- =============================================

-- 1. Charity Applications table
CREATE TABLE charity_applications (
    id                          BIGSERIAL PRIMARY KEY,
    application_number          VARCHAR(20) NOT NULL UNIQUE,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Organization info
    charity_name                VARCHAR(200) NOT NULL,
    organization_type           VARCHAR(100),
    ein_tax_id                  VARCHAR(20),

    -- Contact
    contact_name                VARCHAR(200) NOT NULL,
    contact_email               VARCHAR(100) NOT NULL,
    contact_phone               VARCHAR(20),

    -- Address
    address                     VARCHAR(500),
    city                        VARCHAR(100),
    state                       VARCHAR(50),
    zip_code                    VARCHAR(20),
    country                     VARCHAR(100) DEFAULT 'USA',

    -- Details
    description                 TEXT,
    mission_statement           TEXT,
    website                     VARCHAR(255),
    estimated_referrals_per_month INTEGER,

    -- Admin review
    reviewed_by_user_id         BIGINT REFERENCES users(id),
    reviewed_at                 TIMESTAMP,
    admin_notes                 TEXT,
    rejection_reason            TEXT,

    -- Result
    result_charity_id           BIGINT REFERENCES charities(id),

    -- Timestamps
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ca_status ON charity_applications(status);
CREATE INDEX idx_ca_email ON charity_applications(contact_email);
CREATE INDEX idx_ca_app_number ON charity_applications(application_number);

-- 2. Registration Tokens table
CREATE TABLE registration_tokens (
    id                          BIGSERIAL PRIMARY KEY,
    token                       VARCHAR(100) NOT NULL UNIQUE,
    email                       VARCHAR(100) NOT NULL,
    token_type                  VARCHAR(20) NOT NULL,
    charity_id                  BIGINT NOT NULL REFERENCES charities(id),
    charity_application_id      BIGINT REFERENCES charity_applications(id),
    team_invite_id              BIGINT,
    role_to_assign              VARCHAR(50) NOT NULL DEFAULT 'ROLE_CHARITY_PARTNER',
    is_used                     BOOLEAN DEFAULT FALSE,
    used_at                     TIMESTAMP,
    used_by_user_id             BIGINT REFERENCES users(id),
    expires_at                  TIMESTAMP NOT NULL,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rt_token ON registration_tokens(token);
CREATE INDEX idx_rt_email ON registration_tokens(email);

-- 3. Team Invites table
CREATE TABLE team_invites (
    id                          BIGSERIAL PRIMARY KEY,
    charity_id                  BIGINT NOT NULL REFERENCES charities(id),
    invited_by_user_id          BIGINT NOT NULL REFERENCES users(id),
    email                       VARCHAR(100) NOT NULL,
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    role_to_assign              VARCHAR(50) NOT NULL DEFAULT 'ROLE_CHARITY_PARTNER',
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message                     TEXT,
    token                       VARCHAR(100) NOT NULL UNIQUE,
    expires_at                  TIMESTAMP NOT NULL,
    accepted_at                 TIMESTAMP,
    result_user_id              BIGINT REFERENCES users(id),
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ti_charity ON team_invites(charity_id);
CREATE INDEX idx_ti_token ON team_invites(token);
CREATE INDEX idx_ti_email ON team_invites(email);

-- 4. Add FK from registration_tokens to team_invites
ALTER TABLE registration_tokens
    ADD CONSTRAINT fk_rt_team_invite
    FOREIGN KEY (team_invite_id) REFERENCES team_invites(id);

-- 5. Add primary_contact_id and allowed_email_domain to charities
ALTER TABLE charities ADD COLUMN primary_contact_id BIGINT REFERENCES users(id);
ALTER TABLE charities ADD COLUMN allowed_email_domain VARCHAR(100);

CREATE INDEX idx_charities_primary_contact ON charities(primary_contact_id);
