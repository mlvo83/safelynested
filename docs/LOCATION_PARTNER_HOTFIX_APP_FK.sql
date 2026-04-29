-- =============================================
-- LOCATION PARTNER — HOTFIX
-- Adds stay_partner_application_id to location_partners
-- if it wasn't present in the originally-applied
-- LOCATION_PARTNER_MIGRATION.sql.
--
-- Safe to run multiple times (uses IF NOT EXISTS).
-- =============================================

ALTER TABLE location_partners
    ADD COLUMN IF NOT EXISTS stay_partner_application_id BIGINT REFERENCES stay_partner_applications(id);

CREATE INDEX IF NOT EXISTS idx_lp_application ON location_partners(stay_partner_application_id);
