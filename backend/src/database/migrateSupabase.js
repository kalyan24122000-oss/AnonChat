const fs = require('node:fs');
const path = require('node:path');
const { Client } = require('pg');
const bcrypt = require('bcryptjs');
const config = require('../config');

async function migrateSupabase() {
  const dbUrl = process.env.DATABASE_URL || config.databaseUrl;
  let client;

  if (dbUrl) {
    client = new Client({
      connectionString: dbUrl,
      ssl: { rejectUnauthorized: false }
    });
  } else {
    client = new Client({
      host: process.env.PGHOST || 'db.xgihneqqlatwxbevggci.supabase.co',
      port: parseInt(process.env.PGPORT, 10) || 5432,
      database: process.env.PGDATABASE || 'postgres',
      user: process.env.PGUSER || 'postgres',
      password: process.env.PGPASSWORD || 'Q2Bek*S-RSVXyc-',
      ssl: { rejectUnauthorized: false }
    });
  }

  console.log('[Supabase Migration] Connecting to PostgreSQL database...');
  await client.connect();
  console.log('[Supabase Migration] Connected successfully!');

  // Read SQL schema file
  const schemaPath = path.join(__dirname, 'supabase_schema.sql');
  const sql = fs.readFileSync(schemaPath, 'utf8');

  console.log('[Supabase Migration] Executing schema DDL...');
  await client.query(sql);
  console.log('[Supabase Migration] DDL schema executed successfully.');

  // Pre-seed the 2 System Administrators
  const now = Date.now();
  const farFuture = now + (100 * 365 * 24 * 60 * 60 * 1000); // 100 years

  const admin1Pass = config.adminInitialPassword || 'AnonAdminSecure2026!#';
  const admin1Hash = bcrypt.hashSync(admin1Pass, 10);
  await client.query(`
    INSERT INTO public.users (
      id, display_name, password_hash, plain_password, role, badge_color,
      status, trial_started_at, trial_expires_at, subscription_status,
      premium_expires_at, created_at, updated_at
    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    ON CONFLICT (id) DO UPDATE SET
      plain_password = EXCLUDED.plain_password,
      password_hash = EXCLUDED.password_hash,
      role = 'admin',
      badge_color = EXCLUDED.badge_color;
  `, [
    'ADM-MASTER-01', 'System Administrator 1', admin1Hash, admin1Pass, 'admin', '#FF9800',
    'active', now, farFuture, 'active', farFuture, now, now
  ]);
  console.log('[Supabase Migration] Seeded Administrator 1: ADM-MASTER-01');

  const admin2Pass = 'AnonAdmin2Secure2026!#';
  const admin2Hash = bcrypt.hashSync(admin2Pass, 10);
  await client.query(`
    INSERT INTO public.users (
      id, display_name, password_hash, plain_password, role, badge_color,
      status, trial_started_at, trial_expires_at, subscription_status,
      premium_expires_at, created_at, updated_at
    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    ON CONFLICT (id) DO UPDATE SET
      plain_password = EXCLUDED.plain_password,
      password_hash = EXCLUDED.password_hash,
      role = 'admin',
      badge_color = EXCLUDED.badge_color;
  `, [
    'ADM-MASTER-02', 'System Administrator 2', admin2Hash, admin2Pass, 'admin', '#FF9800',
    'active', now, farFuture, 'active', farFuture, now, now
  ]);
  console.log('[Supabase Migration] Seeded Administrator 2: ADM-MASTER-02');

  // Seed the 4 standard plans matching backend/src/database/seeds.js
  const standardPlans = [
    { id: 'starter', name: 'Starter Anonymous', duration_days: 30, description: 'Standard anonymous private messaging channel for 30 days', features: '["Curve25519 / AES-256-GCM End-to-End Encryption","FLAG_SECURE Screen Capture Shield","Zero Identity Registration (No Phone / Email)","30-Day Guaranteed Service"]' },
    { id: 'pro', name: 'Pro Confidential', duration_days: 90, description: 'Extended confidential privacy protection for 90 days', features: '["Everything in Starter Plan","90-Day Extended Access","Zero IP Logging Architecture","Priority Delivery Relays"]' },
    { id: 'elite', name: 'Elite Sovereign', duration_days: 180, description: 'Advanced sovereign communications for security-critical users', features: '["Everything in Pro Plan","180-Day Key Management Support","Multi-Device Identity Support","Dedicated Node Routing"]' },
    { id: 'annual', name: 'Annual Fortress', duration_days: 365, description: 'Full 1-year continuous high-privacy fortress channel', features: '["Full 365-Day Unlimited Anonymous Access","Highest Relay Priority","Direct Channel with Platform Owner","Immediate Access to All Future Privacy Features"]' }
  ];

  for (const p of standardPlans) {
    await client.query(`
      INSERT INTO public.plans (id, name, duration_days, description, features, is_active, created_at, updated_at)
      VALUES ($1, $2, $3, $4, $5, 1, $6, $7)
      ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        duration_days = EXCLUDED.duration_days,
        description = EXCLUDED.description,
        features = EXCLUDED.features;
    `, [p.id, p.name, p.duration_days, p.description, p.features, now, now]);
  }
  console.log('[Supabase Migration] Seeded standard plans: starter, pro, elite, annual');

  // Verify created tables
  const tablesRes = await client.query(`
    SELECT table_name 
    FROM information_schema.tables 
    WHERE table_schema = 'public' 
    ORDER BY table_name;
  `);
  console.log('[Supabase Migration] Active Cloud Tables:', tablesRes.rows.map(r => r.table_name));

  await client.end();
  console.log('[Supabase Migration] Finished successfully.');
}

if (require.main === module) {
  migrateSupabase().catch(err => {
    console.error('[Supabase Migration Failed]', err);
    process.exit(1);
  });
}

module.exports = { migrateSupabase };
