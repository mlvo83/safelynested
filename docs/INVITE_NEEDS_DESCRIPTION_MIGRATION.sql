-- Migration: Add needs_description field to referral_invites table
-- Allows charity partners to describe participant needs when sending an invite
-- This flows through to the auto-created referral's needsDescription field

ALTER TABLE referral_invites ADD COLUMN needs_description TEXT;
