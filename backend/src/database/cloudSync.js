const { Pool } = require('pg');
const config = require('../config');

let pgPool = null;

function getPgPool() {
  if (pgPool) return pgPool;

  const connectionString = process.env.DATABASE_URL || config.databaseUrl || 
    `postgresql://postgres:${encodeURIComponent(process.env.PGPASSWORD || 'Q2Bek*S-RSVXyc-')}@${process.env.PGHOST || 'db.xgihneqqlatwxbevggci.supabase.co'}:${process.env.PGPORT || 5432}/${process.env.PGDATABASE || 'postgres'}`;

  pgPool = new Pool({
    connectionString,
    ssl: { rejectUnauthorized: false },
    max: 5,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 5000
  });

  pgPool.on('error', (err) => {
    console.error('[Supabase CloudSync Pool Error]', err.message);
  });

  return pgPool;
}

/**
 * Queue system for sequential execution of cloud sync writes.
 * Ensures parent records (e.g. conversations, users) are upserted before child records (e.g. conversation_members, messages).
 */
const syncQueue = [];
let isProcessingQueue = false;

async function processSyncQueue() {
  if (isProcessingQueue || syncQueue.length === 0) return;
  isProcessingQueue = true;

  while (syncQueue.length > 0) {
    const task = syncQueue.shift();
    try {
      await task();
    } catch (err) {
      console.error('[Supabase CloudSync Queue Error]', err.message);
    }
  }

  isProcessingQueue = false;
}

function isTestEnv() {
  return process.env.NODE_ENV === 'test' || 
         process.env.NODE_TEST_CONTEXT !== undefined || 
         process.argv.some(arg => arg.includes('test'));
}

function enqueueSyncTask(taskFn) {
  if (isTestEnv()) return;
  syncQueue.push(taskFn);
  setImmediate(processSyncQueue);
}

/**
 * Pull all data from Supabase Cloud PostgreSQL into the local SQLite database.
 * This runs when the server boots on Render, instantly restoring all state after an ephemeral disk wipe.
 */
async function pullFromSupabase(localDb) {
  if (isTestEnv()) {
    return;
  }

  const pool = getPgPool();
  try {
    console.log('[Supabase CloudSync] Pulling cloud state from Supabase PostgreSQL...');

    // 1. Users
    const usersRes = await pool.query('SELECT * FROM public.users;');
    if (usersRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO users (
          id, display_name, password_hash, plain_password, role, badge_color,
          public_key, recovery_code_hash, status, trial_started_at, trial_expires_at,
          subscription_status, premium_expires_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const u of usersRes.rows) {
        stmt.run(
          u.id, u.display_name, u.password_hash, u.plain_password, u.role, u.badge_color || '#FF9800',
          u.public_key, u.recovery_code_hash, u.status, Number(u.trial_started_at), Number(u.trial_expires_at),
          u.subscription_status, u.premium_expires_at ? Number(u.premium_expires_at) : null,
          Number(u.created_at), Number(u.updated_at)
        );
      }
      console.log(`[Supabase CloudSync] Restored ${usersRes.rows.length} users from Supabase.`);
    }

    // 2. Plans
    const plansRes = await pool.query('SELECT * FROM public.plans;');
    if (plansRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO plans (
          id, name, duration_days, description, features, is_active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const p of plansRes.rows) {
        stmt.run(p.id, p.name, p.duration_days, p.description, p.features, p.is_active, Number(p.created_at), Number(p.updated_at));
      }
    }

    // 3. Licenses
    const licRes = await pool.query('SELECT * FROM public.licenses;');
    if (licRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO licenses (
          id, license_key, plan_id, status, created_by_admin_id, redeemed_by_user_id,
          redeemed_at, revoked_at, notes, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const l of licRes.rows) {
        stmt.run(
          l.id, l.license_key, l.plan_id, l.status, l.created_by_admin_id, l.redeemed_by_user_id,
          l.redeemed_at ? Number(l.redeemed_at) : null, l.revoked_at ? Number(l.revoked_at) : null,
          l.notes, Number(l.created_at)
        );
      }
    }

    // 4. Conversations
    const convRes = await pool.query('SELECT * FROM public.conversations;');
    if (convRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO conversations (id, type, created_at, updated_at)
        VALUES (?, ?, ?, ?)
      `);
      for (const c of convRes.rows) {
        stmt.run(c.id, c.type, Number(c.created_at), Number(c.updated_at));
      }
    }

    // 5. Conversation Members
    const memRes = await pool.query('SELECT * FROM public.conversation_members;');
    if (memRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO conversation_members (id, conversation_id, user_id, joined_at, last_read_at)
        VALUES (?, ?, ?, ?, ?)
      `);
      for (const m of memRes.rows) {
        stmt.run(m.id, m.conversation_id, m.user_id, Number(m.joined_at), Number(m.last_read_at || 0));
      }
    }

    // 6. Messages
    const msgRes = await pool.query('SELECT * FROM public.messages;');
    if (msgRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO messages (
          id, conversation_id, sender_id, recipient_id, encrypted_content, iv, auth_tag,
          sender_ephemeral_key, status, delivered_at, read_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const m of msgRes.rows) {
        stmt.run(
          m.id, m.conversation_id, m.sender_id, m.recipient_id, m.encrypted_content,
          m.iv, m.auth_tag, m.sender_ephemeral_key, m.status,
          m.delivered_at ? Number(m.delivered_at) : null, m.read_at ? Number(m.read_at) : null,
          Number(m.created_at)
        );
      }
      console.log(`[Supabase CloudSync] Restored ${msgRes.rows.length} messages from Supabase.`);
    }

    // 7. Media Files
    const mediaRes = await pool.query('SELECT * FROM public.media_files;');
    if (mediaRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO media_files (
          id, sender_id, file_name, mime_type, file_size, file_path, created_at, expires_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const mf of mediaRes.rows) {
        stmt.run(
          mf.id, mf.sender_id, mf.file_name, mf.mime_type, Number(mf.file_size),
          mf.file_path, Number(mf.created_at), Number(mf.expires_at)
        );
      }
    }

    console.log('[Supabase CloudSync] Cloud synchronization completed. Local database is 100% in sync with Supabase Cloud!');
  } catch (err) {
    console.error('[Supabase CloudSync Error on Pull]', err.message);
  }
}

/**
 * Asynchronously push a new/updated record to Supabase Cloud PostgreSQL
 */
function pushRecord(table, record) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      const keys = Object.keys(record);
      if (keys.length === 0) return;

      const columns = keys.map(k => `"${k}"`).join(', ');
      const values = keys.map((_, i) => `$${i + 1}`).join(', ');
      const updates = keys
        .filter(k => k !== 'id')
        .map(k => `"${k}" = EXCLUDED."${k}"`)
        .join(', ');

      const query = `
        INSERT INTO public."${table}" (${columns})
        VALUES (${values})
        ON CONFLICT ("id") DO UPDATE SET ${updates};
      `;

      await pool.query(query, Object.values(record));
    } catch (err) {
      console.error(`[Supabase CloudSync Error pushing to ${table}]`, err.message);
    }
  });
}

/**
 * Asynchronously delete a record from Supabase Cloud PostgreSQL
 */
function deleteRecord(table, id) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      await pool.query(`DELETE FROM public."${table}" WHERE "id" = $1;`, [id]);
    } catch (err) {
      console.error(`[Supabase CloudSync Error deleting from ${table}]`, err.message);
    }
  });
}

module.exports = {
  getPgPool,
  pullFromSupabase,
  pushRecord,
  deleteRecord
};
