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

  // 2. Seed Initial Admin if no admin exists
  const adminCount = conn.prepare("SELECT COUNT(*) as count FROM users WHERE role = 'admin'").get();
  if (adminCount.count === 0) {
    const adminId = config.adminInitialId;
    const adminPassword = config.adminInitialPassword;
    const passwordHash = await hashPassword(adminPassword);

    // 100 years trial/premium for master admin
    const trialStartedAt = now;
    const trialExpiresAt = now + (100 * 365 * 24 * 60 * 60 * 1000);

    conn.prepare(`
      INSERT INTO users (
        id, display_name, password_hash, role, status,
        trial_started_at, trial_expires_at, subscription_status, premium_expires_at,
        created_at, updated_at
      ) VALUES (?, ?, ?, 'admin', 'active', ?, ?, 'active', ?, ?, ?)
    `).run(
      adminId,
      'System Administrator',
      passwordHash,
      trialStartedAt,
      trialExpiresAt,
      trialExpiresAt,
      now,
      now
    );

    // Record audit log
    conn.prepare(`
      INSERT INTO audit_logs (id, actor_id, action, target_id, details, ip_address, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(
      `audit_${now}`,
      'system',
      'INITIAL_ADMIN_BOOTSTRAPPED',
      adminId,
      JSON.stringify({ adminId, role: 'admin' }),
      '127.0.0.1',
      now
    );

    console.log(`=======================================================`);
    console.log(`[BOOTSTRAP] Initial Admin Created:`);
    console.log(`  Admin ID: ${adminId}`);
    console.log(`  Password: ${adminPassword}`);
    console.log(`  (Role: 'admin' explicitly enforced in database)`);
    console.log(`=======================================================`);
  }
}

module.exports = {
  runSeeds,
  DEFAULT_PLANS
};
