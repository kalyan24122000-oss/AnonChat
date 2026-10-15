const { getDb } = require('./db');
const { hashPassword } = require('../utils/crypto');
const config = require('../config');

const DEFAULT_PLANS = [
  {
    id: 'starter',
    name: 'Starter Anonymous',
    duration_days: 30,
    description: 'Standard anonymous private messaging channel for 30 days',
    features: JSON.stringify([
      'Curve25519 / AES-256-GCM End-to-End Encryption',
      'FLAG_SECURE Screen Capture Shield',
      'Zero Identity Registration (No Phone / Email)',
      '30-Day Guaranteed Service'
    ])
  },
  {
    id: 'pro',
    name: 'Pro Confidential',
    duration_days: 90,
    description: 'Extended confidential privacy protection for 90 days',
    features: JSON.stringify([
      'Everything in Starter Plan',
      '90-Day Extended Access',
      'Zero IP Logging Architecture',
      'Priority Delivery Relays'
    ])
  },
  {
    id: 'elite',
    name: 'Elite Sovereign',
    duration_days: 180,
    description: 'Advanced sovereign communications for security-critical users',
    features: JSON.stringify([
      'Everything in Pro Plan',
      '180-Day Key Management Support',
      'Multi-Device Identity Support',
      'Dedicated Node Routing'
    ])
  },
  {
    id: 'annual',
    name: 'Annual Fortress',
    duration_days: 365,
    description: 'Full 1-year continuous high-privacy fortress channel',
    features: JSON.stringify([
      'Full 365-Day Unlimited Anonymous Access',
      'Highest Relay Priority',
      'Direct Channel with Platform Owner',
      'Immediate Access to All Future Privacy Features'
    ])
  }
];

/**
 * Seed initial database records
 * @param {DatabaseSync} [db]
 */
async function runSeeds(db = null) {
  const conn = db || getDb();
  const now = Date.now();

  // 1. Seed Plans if not present
  for (const plan of DEFAULT_PLANS) {
    const existing = conn.prepare('SELECT id FROM plans WHERE id = ?').get(plan.id);
    if (!existing) {
      conn.prepare(`
        INSERT INTO plans (id, name, duration_days, description, features, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 1, ?, ?)
      `).run(plan.id, plan.name, plan.duration_days, plan.description, plan.features, now, now);
      console.log(`[Database Seed] Seeded plan: ${plan.name} (${plan.duration_days} days)`);
    }
  }

  // 2. Seed Initial Admins (Support multiple admins e.g. ADM-MASTER-01 and ADM-MASTER-02)
  const ADMINS_TO_SEED = [
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

  for (const admin of ADMINS_TO_SEED) {
    const existingAdmin = conn.prepare("SELECT id FROM users WHERE id = ?").get(admin.id);
    if (!existingAdmin) {
      const passwordHash = await hashPassword(admin.password);
      const trialStartedAt = now;
      const trialExpiresAt = now + (100 * 365 * 24 * 60 * 60 * 1000);

      try {
        conn.prepare(`
          INSERT INTO users (
            id, display_name, password_hash, plain_password, badge_color, role, status,
            trial_started_at, trial_expires_at, subscription_status, premium_expires_at,
            created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, 'admin', 'active', ?, ?, 'active', ?, ?, ?)
        `).run(
          admin.id,
          admin.displayName,
          passwordHash,
          admin.password,
          admin.badgeColor,
          trialStartedAt,
          trialExpiresAt,
          trialExpiresAt,
          now,
          now
        );
      } catch (e) {
        conn.prepare(`
          INSERT INTO users (
            id, display_name, password_hash, role, status,
            trial_started_at, trial_expires_at, subscription_status, premium_expires_at,
            created_at, updated_at
          ) VALUES (?, ?, ?, 'admin', 'active', ?, ?, 'active', ?, ?, ?)
        `).run(
          admin.id,
          admin.displayName,
          passwordHash,
          trialStartedAt,
          trialExpiresAt,
          trialExpiresAt,
          now,
          now
        );
      }

      conn.prepare(`
        INSERT INTO audit_logs (id, actor_id, action, target_id, details, ip_address, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).run(
        `audit_${now}_${admin.id}`,
        'system',
        'ADMIN_BOOTSTRAPPED',
        admin.id,
        JSON.stringify({ adminId: admin.id, role: 'admin' }),
        '127.0.0.1',
        now
      );

      console.log(`[BOOTSTRAP] Administrator Configured: ${admin.id} (${admin.displayName})`);
    }
  }
}

module.exports = {
  runSeeds,
  DEFAULT_PLANS
};
