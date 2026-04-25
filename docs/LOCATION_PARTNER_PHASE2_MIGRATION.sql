-- =============================================
-- LOCATION PARTNER — PHASE 2 MIGRATION
-- Extends registration_tokens so an approved
-- Stay Partner application can invite the applicant
-- as a self-service Location Partner.
--
-- Prerequisites:
--   - LOCATION_PARTNER_MIGRATION.sql (Phase 1)
--     must have been applied first — it creates
--     location_partners, partner_locations,
--     location_availability and seeds the
--     ROLE_LOCATION_PARTNER role.
-- =============================================

-- 1. Relax charity_id to allow LOCATION_PARTNER tokens
--    (charity-related tokens still populate this column)
ALTER TABLE registration_tokens
    ALTER COLUMN charity_id DROP NOT NULL;

-- 2. Link a token to a newly created LocationPartner
ALTER TABLE registration_tokens
    ADD COLUMN location_partner_id BIGINT REFERENCES location_partners(id);

-- 3. Link a token back to the source StayPartnerApplication
ALTER TABLE registration_tokens
    ADD COLUMN stay_partner_application_id BIGINT REFERENCES stay_partner_applications(id);

CREATE INDEX idx_rt_location_partner ON registration_tokens(location_partner_id);
CREATE INDEX idx_rt_stay_partner_app ON registration_tokens(stay_partner_application_id);
