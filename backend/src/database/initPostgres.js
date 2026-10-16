const { Pool } = require('pg');
const config = require('../config');

let pgPool = null;

function getPgPool() {
  if (pgPool) return pgPool;

  const connectionString = process.env.DATABASE_URL || config.databaseUrl;
  if (!connectionString) {
    return null;
  }

  const isInternal = connectionString.includes('@dpg-') && !connectionString.includes('.render.com');

  pgPool = new Pool({
    connectionString,
    ssl: isInternal ? false : { rejectUnauthorized: false }
  });

  pgPool.on('error', (err) => {
    console.error('[PostgreSQL Pool Error]', err.message);
  });

  return pgPool;
}

const POSTGRES_SCHEMA = `
  CREATE TABLE IF NOT EXISTS users (
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

  CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
  CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
  CREATE INDEX IF NOT EXISTS idx_users_sub ON users(subscription_status, trial_expires_at);

  CREATE TABLE IF NOT EXISTS plans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    duration_days INTEGER NOT NULL,
    description TEXT,
    features TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS licenses (
    id TEXT PRIMARY KEY,
    license_key TEXT UNIQUE NOT NULL,
    plan_id TEXT NOT NULL REFERENCES plans(id),
    status TEXT NOT NULL DEFAULT 'unused',
    created_by_admin_id TEXT REFERENCES users(id),
    redeemed_by_user_id TEXT REFERENCES users(id),
    redeemed_at BIGINT,
    revoked_at BIGINT,
    notes TEXT,
    created_at BIGINT NOT NULL
  );

  CREATE UNIQUE INDEX IF NOT EXISTS idx_licenses_key ON licenses(license_key);
  CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);

  CREATE TABLE IF NOT EXISTS conversations (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL DEFAULT 'direct',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS conversation_members (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at BIGINT NOT NULL,
    last_read_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE(conversation_id, user_id)
  );

  CREATE INDEX IF NOT EXISTS idx_conv_members_user ON conversation_members(user_id);
  CREATE INDEX IF NOT EXISTS idx_conv_members_conv ON conversation_members(conversation_id);

  CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id TEXT NOT NULL REFERENCES users(id),
    recipient_id TEXT NOT NULL REFERENCES users(id),
    encrypted_content TEXT NOT NULL,
    iv TEXT NOT NULL,
    auth_tag TEXT NOT NULL,
    sender_ephemeral_key TEXT,
    status TEXT NOT NULL DEFAULT 'sent',
    delivered_at BIGINT,
    read_at BIGINT,
    created_at BIGINT NOT NULL
  );

  CREATE INDEX IF NOT EXISTS idx_messages_conv_created ON messages(conversation_id, created_at);
  CREATE INDEX IF NOT EXISTS idx_messages_recipient_status ON messages(recipient_id, status);

  CREATE TABLE IF NOT EXISTS blocked_users (
    id TEXT PRIMARY KEY,
    blocker_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    UNIQUE(blocker_id, blocked_id)
  );

  CREATE TABLE IF NOT EXISTS user_reports (
    id TEXT PRIMARY KEY,
    reporter_id TEXT NOT NULL REFERENCES users(id),
    reported_id TEXT NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at BIGINT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    actor_id TEXT,
    action TEXT NOT NULL,
    target_id TEXT,
    details TEXT,
    ip_address TEXT,
    created_at BIGINT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS media_files (
    id TEXT PRIMARY KEY,
    sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    file_path TEXT NOT NULL,
    base64_data TEXT,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
  );

  CREATE INDEX IF NOT EXISTS idx_media_expires ON media_files(expires_at);
  CREATE INDEX IF NOT EXISTS idx_media_sender ON media_files(sender_id);
`;

async function initPostgresSchema() {
  const pool = getPgPool();
  if (!pool) {
    console.warn('[PostgreSQL Init] No PostgreSQL connection string configured, skipping schema init.');
    return;
  }

  try {
    const check = await pool.query("SELECT to_regclass('public.users') as exists");
    if (!check.rows[0].exists) {
      console.log('[PostgreSQL Init] Creating PostgreSQL schema...');
      await pool.query(POSTGRES_SCHEMA);
      console.log('[PostgreSQL Init] PostgreSQL tables and indices created.');
    } else {
      console.log('[PostgreSQL Init] PostgreSQL schema verified (tables already active).');
    }

    // Seed default plans into PostgreSQL if not present
    const { DEFAULT_PLANS } = require('./seeds');
    const now = Date.now();
    for (const plan of DEFAULT_PLANS) {
      const check = await pool.query('SELECT id FROM public.plans WHERE id = $1', [plan.id]);
      if (check.rows.length === 0) {
        await pool.query(
          `INSERT INTO public.plans (id, name, duration_days, description, features, is_active, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, 1, $6, $7)`,
          [plan.id, plan.name, plan.duration_days, plan.description, plan.features, now, now]
        );
        console.log(`[PostgreSQL Init] Seeded plan ${plan.name} into Render PostgreSQL.`);
      }
    }

    // Seed initial admins into PostgreSQL if not present
    const { hashPassword } = require('../utils/crypto');
    const adminsToSeed = [
      {
        id: config.adminInitialId || 'ADM-MASTER-01',
        password: config.adminInitialPassword || 'AnonAdminSecure2026!#',
        displayName: 'System Administrator 1',
        badgeColor: '#FF9800'
      },
      {
        id: 'ADM-MASTER-02',
        password: config.adminInitialPassword || 'AnonAdminSecure2026!#',
        displayName: 'System Administrator 2',
        badgeColor: '#FF9800'
      }
    ];

    for (const admin of adminsToSeed) {
      const checkAdmin = await pool.query('SELECT id FROM public.users WHERE id = $1', [admin.id]);
      if (checkAdmin.rows.length === 0) {
        const passwordHash = await hashPassword(admin.password);
        const trialStartedAt = now;
        const trialExpiresAt = now + (100 * 365 * 24 * 60 * 60 * 1000);
        await pool.query(
          `INSERT INTO public.users (
            id, display_name, password_hash, plain_password, badge_color, role, status,
            trial_started_at, trial_expires_at, subscription_status, premium_expires_at,
            created_at, updated_at
          ) VALUES ($1, $2, $3, $4, $5, 'admin', 'active', $6, $7, 'active', $8, $9, $10)`,
          [
            admin.id, admin.displayName, passwordHash, admin.password, admin.badgeColor,
            trialStartedAt, trialExpiresAt, trialExpiresAt, now, now
          ]
        );
        console.log(`[PostgreSQL Init] Seeded admin ${admin.id} into Render PostgreSQL.`);
      }
    }
  } catch (err) {
    console.error('[PostgreSQL Init Error]', err.message);
    throw err;
  }
}

module.exports = {
  getPgPool,
  initPostgresSchema
};
