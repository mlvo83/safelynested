-- Adds a flag that forces a user to set a new password before using the app.
-- Set TRUE when an admin assigns a temporary password (e.g. a charity primary-contact
-- reset via /admin/charity-applications). Cleared when the user changes it at /change-password.
--
-- Run this against PostgreSQL BEFORE deploying the code change: the User entity now selects
-- this column, so with ddl-auto=none the app will fail to query users until the column exists.

ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
