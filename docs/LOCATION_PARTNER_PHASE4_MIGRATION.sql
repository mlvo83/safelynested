-- =============================================
-- LOCATION PARTNER — PHASE 4 MIGRATION
-- Booking integration: charities linked to a
-- partner property can now book against the
-- partner's availability windows.
-- =============================================

-- 1. Bookings can reference a partner_location instead of a charity location
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS partner_location_id BIGINT REFERENCES partner_locations(id);

CREATE INDEX IF NOT EXISTS idx_bookings_partner_location ON bookings(partner_location_id);

-- 2. Availability rows can be linked to the booking that consumed them
--    (used to release a window back to AVAILABLE when a booking is cancelled)
ALTER TABLE location_availability
    ADD COLUMN IF NOT EXISTS booking_id BIGINT REFERENCES bookings(id);

CREATE INDEX IF NOT EXISTS idx_la_booking ON location_availability(booking_id);
