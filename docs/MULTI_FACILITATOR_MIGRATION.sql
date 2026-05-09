-- =============================================
-- MULTI-CHARITY FACILITATOR MIGRATION
-- Feature: A single user can facilitate for multiple
--          charities. Authorizations are recorded per
--          (user, charity) with audit trail.
-- =============================================

-- 1. New role: ROLE_MULTI_FACILITATOR
INSERT INTO roles (name)
SELECT 'ROLE_MULTI_FACILITATOR'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ROLE_MULTI_FACILITATOR'
);

-- 2. Authorization assignments table
CREATE TABLE IF NOT EXISTS multi_facilitator_charities (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    charity_id          BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id  BIGINT REFERENCES users(id),
    CONSTRAINT uq_multi_facilitator_user_charity UNIQUE (user_id, charity_id)
);

CREATE INDEX IF NOT EXISTS idx_mfc_user    ON multi_facilitator_charities(user_id);
CREATE INDEX IF NOT EXISTS idx_mfc_charity ON multi_facilitator_charities(charity_id);
