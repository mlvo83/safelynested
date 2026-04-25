-- =============================================
-- LOCATION PARTNER — PHASE 3 MIGRATION
-- Adds the many-to-many link between
-- partner_locations and charities so admin can
-- assign a Location Partner's property to one or
-- more charities.
--
-- Phase 3 is admin-UI only — no booking integration
-- yet (that's Phase 4).
-- =============================================

CREATE TABLE partner_location_charities (
    id                          BIGSERIAL PRIMARY KEY,
    partner_location_id         BIGINT NOT NULL REFERENCES partner_locations(id) ON DELETE CASCADE,
    charity_id                  BIGINT NOT NULL REFERENCES charities(id) ON DELETE CASCADE,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id          BIGINT REFERENCES users(id),
    CONSTRAINT uq_plc UNIQUE (partner_location_id, charity_id)
);

CREATE INDEX idx_plc_partner_location ON partner_location_charities(partner_location_id);
CREATE INDEX idx_plc_charity ON partner_location_charities(charity_id);
