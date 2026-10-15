-- ==============================================================================
-- AnonChat PostgreSQL Schema for Supabase Cloud Database
-- Run this in Supabase Dashboard -> SQL Editor -> New Query -> Run
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS public.users (
  id TEXT PRIMARY KEY,
  display_name TEXT,
  password_hash TEXT NOT NULL,
  plain_password TEXT,
  role TEXT NOT NULL DEFAULT 'user',
  badge_color TEXT DEFAULT '#FF9800',
  public_key TEXT,
  recovery_code_hash TEXT,
  status TEXT NOT NULL DEFAULT 'active',
  trial_started_at BIGINT NOT NULL,
  trial_expires_at BIGINT NOT NULL,
  subscription_status TEXT NOT NULL DEFAULT 'trial',
  premium_expires_at BIGINT DEFAULT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON public.users(status);
CREATE INDEX IF NOT EXISTS idx_users_sub ON public.users(subscription_status, trial_expires_at);

-- 2. Plans Table
CREATE TABLE IF NOT EXISTS public.plans (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  duration_days INT NOT NULL,
  description TEXT,
  features TEXT,
  is_active INT NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

-- 3. Licenses Table
CREATE TABLE IF NOT EXISTS public.licenses (
  id TEXT PRIMARY KEY,
  license_key TEXT UNIQUE NOT NULL,
  plan_id TEXT NOT NULL REFERENCES public.plans(id),
  status TEXT NOT NULL DEFAULT 'unused',
  created_by_admin_id TEXT REFERENCES public.users(id),
  redeemed_by_user_id TEXT REFERENCES public.users(id),
  redeemed_at BIGINT,
  revoked_at BIGINT,
  notes TEXT,
  created_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_licenses_key ON public.licenses(license_key);
CREATE INDEX IF NOT EXISTS idx_licenses_status ON public.licenses(status);

-- 4. Conversations Table
CREATE TABLE IF NOT EXISTS public.conversations (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL DEFAULT 'direct',
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

-- 5. Conversation Members Table
CREATE TABLE IF NOT EXISTS public.conversation_members (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  joined_at BIGINT NOT NULL,
  last_read_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE(conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_conv_members_user ON public.conversation_members(user_id);
CREATE INDEX IF NOT EXISTS idx_conv_members_conv ON public.conversation_members(conversation_id);

-- 6. Messages Table (E2EE Payload)
CREATE TABLE IF NOT EXISTS public.messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
  sender_id TEXT NOT NULL REFERENCES public.users(id),
  recipient_id TEXT NOT NULL REFERENCES public.users(id),
  encrypted_content TEXT NOT NULL,
  iv TEXT NOT NULL,
  auth_tag TEXT NOT NULL,
  sender_ephemeral_key TEXT,
  status TEXT NOT NULL DEFAULT 'sent',
  delivered_at BIGINT,
  read_at BIGINT,
  created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conv_created ON public.messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_recipient_status ON public.messages(recipient_id, status);

-- 7. Blocked Users Table
CREATE TABLE IF NOT EXISTS public.blocked_users (
  id TEXT PRIMARY KEY,
  blocker_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  blocked_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  created_at BIGINT NOT NULL,
  UNIQUE(blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_blocked_blocker ON public.blocked_users(blocker_id);

-- 8. User Reports Table
CREATE TABLE IF NOT EXISTS public.user_reports (
  id TEXT PRIMARY KEY,
  reporter_id TEXT NOT NULL REFERENCES public.users(id),
  reported_id TEXT NOT NULL REFERENCES public.users(id),
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at BIGINT NOT NULL
);

-- 9. Audit Logs Table
CREATE TABLE IF NOT EXISTS public.audit_logs (
  id TEXT PRIMARY KEY,
  actor_id TEXT,
  action TEXT NOT NULL,
  target_id TEXT,
  details TEXT,
  ip_address TEXT,
  created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.audit_logs(created_at);

-- 10. Media Files Table (with 7-Day Auto Purge expiration)
CREATE TABLE IF NOT EXISTS public.media_files (
  id TEXT PRIMARY KEY,
  sender_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  file_name TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  file_size BIGINT NOT NULL,
  file_path TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_expires ON public.media_files(expires_at);

-- Disable Row Level Security (RLS) for backend service operations or enable full service role bypass
ALTER TABLE public.users DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.plans DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.licenses DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.blocked_users DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_reports DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.media_files DISABLE ROW LEVEL SECURITY;

-- Seed Default Subscription Plans (Both short IDs and long IDs for full backward/forward compatibility)
INSERT INTO public.plans (id, name, duration_days, description, features, is_active, created_at, updated_at)
VALUES 
  ('starter', 'Starter Anonymous', 30, '30 Days Full Anonymous E2EE Chat Access', '["e2ee_messaging","ephemeral_keys","zero_phone_email"]', 1, 1729000000000, 1729000000000),
  ('pro', 'Pro Confidential', 90, '90 Days High-Privacy Messaging + Media Share', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer"]', 1, 1729000000000, 1729000000000),
  ('elite', 'Elite Sovereign', 180, '180 Days Sovereign Encryption + Multi-Device Ready', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer","priority_relay"]', 1, 1729000000000, 1729000000000),
  ('annual', 'Annual Fortress', 365, '365 Days Unconditional Secure Anonymous Communications', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer","priority_relay","audit_shield"]', 1, 1729000000000, 1729000000000),
  ('plan_starter_30d', 'Starter Anonymous', 30, '30 Days Full Anonymous E2EE Chat Access', '["e2ee_messaging","ephemeral_keys","zero_phone_email"]', 1, 1729000000000, 1729000000000),
  ('plan_pro_90d', 'Pro Confidential', 90, '90 Days High-Privacy Messaging + Media Share', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer"]', 1, 1729000000000, 1729000000000),
  ('plan_elite_180d', 'Elite Sovereign', 180, '180 Days Sovereign Encryption + Multi-Device Ready', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer","priority_relay"]', 1, 1729000000000, 1729000000000),
  ('plan_annual_365d', 'Annual Fortress', 365, '365 Days Unconditional Secure Anonymous Communications', '["e2ee_messaging","ephemeral_keys","zero_phone_email","media_transfer","priority_relay","audit_shield"]', 1, 1729000000000, 1729000000000)
ON CONFLICT (id) DO NOTHING;
