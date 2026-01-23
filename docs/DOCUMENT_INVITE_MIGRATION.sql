-- =====================================================
-- Migration: Add invite support to documents table
-- Date: 2026-01-23
-- Description: Allows documents to be linked to invites
--              and uploaded by participants
-- =====================================================

-- Add invite_id column to link documents to invites
ALTER TABLE documents
ADD COLUMN invite_id BIGINT NULL;

-- Add foreign key constraint
ALTER TABLE documents
ADD CONSTRAINT fk_documents_invite
FOREIGN KEY (invite_id) REFERENCES referral_invites(id);

-- Make uploaded_by nullable (for participant uploads)
ALTER TABLE documents
ALTER COLUMN uploaded_by DROP NOT NULL;

-- Add columns to track participant uploads
ALTER TABLE documents
ADD COLUMN uploaded_by_participant BOOLEAN DEFAULT FALSE;

ALTER TABLE documents
ADD COLUMN participant_name VARCHAR(255) NULL;

-- Create index for faster lookups by invite
CREATE INDEX idx_documents_invite_id ON documents(invite_id);

-- =====================================================
-- Rollback (if needed):
-- =====================================================
-- DROP INDEX IF EXISTS idx_documents_invite_id;
-- ALTER TABLE documents DROP COLUMN participant_name;
-- ALTER TABLE documents DROP COLUMN uploaded_by_participant;
-- ALTER TABLE documents ALTER COLUMN uploaded_by SET NOT NULL;
-- ALTER TABLE documents DROP CONSTRAINT fk_documents_invite;
-- ALTER TABLE documents DROP COLUMN invite_id;
