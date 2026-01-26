-- DATABASE_RESET.sql
-- Deletes all user-related data while preserving system data (roles and admin users)
-- Run this script to reset the application to a clean state
--
-- WARNING: This will DELETE ALL DATA except roles and admin users!
-- Make sure you have a backup before running this.
--
-- Note: Deletes are done in dependency order so no superuser privileges are needed.

-- Identify admin user IDs to preserve
-- (Users who have ROLE_ADMIN assigned)
CREATE TEMP TABLE admin_user_ids AS
SELECT DISTINCT ur.user_id
FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
WHERE r.name = 'ROLE_ADMIN';

-- Delete in reverse dependency order (children first, parents last)

-- 1. Junction/Bridge tables first
DELETE FROM situation_funding;
DELETE FROM donor_charities;
-- Keep user_roles for admin users only
DELETE FROM user_roles WHERE user_id NOT IN (SELECT user_id FROM admin_user_ids);

-- 2. Tables that reference multiple parents
DELETE FROM documents;
DELETE FROM donations;

-- 3. Tables that reference users and other entities
DELETE FROM bookings;
DELETE FROM referral_invites;
DELETE FROM situations;

-- 4. Tables with user references
DELETE FROM referrals;
DELETE FROM nightly_rates;

-- 5. Donor table (references users)
DELETE FROM donors;

-- 6. Location-related tables
DELETE FROM charity_locations;
DELETE FROM locations;

-- 7. Charities (may have verified_by user reference)
DELETE FROM charities;

-- 8. Delete non-admin users only
DELETE FROM users WHERE id NOT IN (SELECT user_id FROM admin_user_ids);

-- Clean up temp table
DROP TABLE admin_user_ids;

-- Reset auto-increment sequences (PostgreSQL)
-- Set sequences to continue after the highest existing ID (for tables with preserved data)
-- For empty tables, restart at 1
SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 0) + 1, false);
SELECT setval('charities_id_seq', COALESCE((SELECT MAX(id) FROM charities), 0) + 1, false);
SELECT setval('locations_id_seq', COALESCE((SELECT MAX(id) FROM locations), 0) + 1, false);
SELECT setval('charity_locations_id_seq', COALESCE((SELECT MAX(id) FROM charity_locations), 0) + 1, false);
SELECT setval('referrals_id_seq', COALESCE((SELECT MAX(id) FROM referrals), 0) + 1, false);
SELECT setval('referral_invites_id_seq', COALESCE((SELECT MAX(id) FROM referral_invites), 0) + 1, false);
SELECT setval('bookings_id_seq', COALESCE((SELECT MAX(id) FROM bookings), 0) + 1, false);
SELECT setval('documents_id_seq', COALESCE((SELECT MAX(id) FROM documents), 0) + 1, false);
SELECT setval('donors_id_seq', COALESCE((SELECT MAX(id) FROM donors), 0) + 1, false);
SELECT setval('donations_id_seq', COALESCE((SELECT MAX(id) FROM donations), 0) + 1, false);
SELECT setval('situations_id_seq', COALESCE((SELECT MAX(id) FROM situations), 0) + 1, false);
SELECT setval('situation_funding_id_seq', COALESCE((SELECT MAX(id) FROM situation_funding), 0) + 1, false);
SELECT setval('nightly_rates_id_seq', COALESCE((SELECT MAX(id) FROM nightly_rates), 0) + 1, false);

-- =====================================================
-- VERIFY RESET
-- =====================================================
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL SELECT 'charities', COUNT(*) FROM charities
UNION ALL SELECT 'donors', COUNT(*) FROM donors
UNION ALL SELECT 'donations', COUNT(*) FROM donations
UNION ALL SELECT 'referrals', COUNT(*) FROM referrals
UNION ALL SELECT 'bookings', COUNT(*) FROM bookings
UNION ALL SELECT 'situations', COUNT(*) FROM situations;

-- Show preserved admin users
SELECT u.id, u.username, u.email, u.enabled, r.name as role
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
WHERE r.name = 'ROLE_ADMIN';
