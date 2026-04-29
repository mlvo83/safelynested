-- =============================================
-- PARTNER LOCATION SELECTION FROM INVITES
--
-- Allows a participant clicking an invite link to select either:
--   - A CharityLocation (existing behavior, selected_location_id)
--   - A PartnerLocation (new, selected_partner_location_id)
--
-- Exactly one of the two FKs is set per referral / invite. Both nullable.
-- =============================================

ALTER TABLE referral_invites
    ADD COLUMN IF NOT EXISTS selected_partner_location_id BIGINT REFERENCES partner_locations(id);

ALTER TABLE referrals
    ADD COLUMN IF NOT EXISTS selected_partner_location_id BIGINT REFERENCES partner_locations(id);

CREATE INDEX IF NOT EXISTS idx_referral_invites_selected_partner
    ON referral_invites(selected_partner_location_id);

CREATE INDEX IF NOT EXISTS idx_referrals_selected_partner
    ON referrals(selected_partner_location_id);
