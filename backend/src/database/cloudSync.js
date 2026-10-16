const { getPgPool } = require('./initPostgres');
const config = require('../config');

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
      console.error('[CloudSync Queue Error]', err.message);
    }
  }

  isProcessingQueue = false;
}

function isTestEnv() {
  return process.env.NODE_ENV === 'test' ||
         process.env.NODE_TEST_CONTEXT !== undefined ||
         Boolean(process.env.npm_lifecycle_event && process.env.npm_lifecycle_event.includes('test')) ||
         process.argv.some(arg => typeof arg === 'string' && (arg.includes('test') || arg.includes('--test')));
}

function enqueueSyncTask(taskFn) {
  if (isTestEnv()) return;
  syncQueue.push(taskFn);
  setImmediate(processSyncQueue);
}

/**
 * Pull all data from PostgreSQL Cloud into the local SQLite database.
 * This runs when the server boots on Render, instantly restoring all state after an ephemeral disk wipe.
 */
async function pullFromCloud(localDb) {
  if (isTestEnv()) {
    return;
  }

  const pool = getPgPool();
  if (!pool) {
    console.warn('[CloudSync] PostgreSQL not configured, skipping cloud pull.');
    return;
  }

  try {
    console.log('[CloudSync] Pulling state from Render PostgreSQL...');

    // 1. Users
    const usersRes = await pool.query('SELECT * FROM public.users;');
    if (usersRes.rows && usersRes.rows.length > 0) {
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
      console.log(`[CloudSync] Restored ${usersRes.rows.length} users from Render PostgreSQL.`);
    }

    // 2. Plans
    const plansRes = await pool.query('SELECT * FROM public.plans;');
    if (plansRes.rows && plansRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO plans (
          id, name, duration_days, description, features, is_active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const p of plansRes.rows) {
        const featuresStr = typeof p.features === 'string' ? p.features : JSON.stringify(p.features || []);
        stmt.run(
          p.id, p.name, p.duration_days, p.description, featuresStr,
          p.is_active ? 1 : 0, Number(p.created_at), Number(p.updated_at)
        );
      }
      console.log(`[CloudSync] Restored ${plansRes.rows.length} plans from Render PostgreSQL.`);
    }

    // 3. Licenses
    const licRes = await pool.query('SELECT * FROM public.licenses;');
    if (licRes.rows && licRes.rows.length > 0) {
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
      console.log(`[CloudSync] Restored ${licRes.rows.length} licenses from Render PostgreSQL.`);
    }

    // 4. Conversations
    const convRes = await pool.query('SELECT * FROM public.conversations;');
    if (convRes.rows && convRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO conversations (id, type, created_at, updated_at)
        VALUES (?, ?, ?, ?)
      `);
      for (const c of convRes.rows) {
        stmt.run(c.id, c.type, Number(c.created_at), Number(c.updated_at));
      }
      console.log(`[CloudSync] Restored ${convRes.rows.length} conversations from Render PostgreSQL.`);
    }

    // 5. Conversation Members
    const memRes = await pool.query('SELECT * FROM public.conversation_members;');
    if (memRes.rows && memRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO conversation_members (id, conversation_id, user_id, joined_at, last_read_at)
        VALUES (?, ?, ?, ?, ?)
      `);
      for (const m of memRes.rows) {
        stmt.run(m.id, m.conversation_id, m.user_id, Number(m.joined_at), Number(m.last_read_at || 0));
      }
      console.log(`[CloudSync] Restored ${memRes.rows.length} conversation members.`);
    }

    // 6. Messages
    const msgRes = await pool.query('SELECT * FROM public.messages;');
    if (msgRes.rows && msgRes.rows.length > 0) {
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
      console.log(`[CloudSync] Restored ${msgRes.rows.length} messages from Render PostgreSQL.`);
    }

    // 7. Media Files
    const mediaRes = await pool.query('SELECT * FROM public.media_files;');
    if (mediaRes.rows && mediaRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO media_files (
          id, sender_id, file_name, mime_type, file_size, file_path, base64_data, created_at, expires_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const mf of mediaRes.rows) {
        stmt.run(
          mf.id, mf.sender_id, mf.file_name, mf.mime_type, Number(mf.file_size),
          mf.file_path, mf.base64_data || null, Number(mf.created_at), Number(mf.expires_at)
        );
      }
      console.log(`[CloudSync] Restored ${mediaRes.rows.length} media files from Render PostgreSQL.`);
    }

    // 8. Blocked Users
    const blkRes = await pool.query('SELECT * FROM public.blocked_users;');
    if (blkRes.rows && blkRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO blocked_users (id, blocker_id, blocked_id, created_at)
        VALUES (?, ?, ?, ?)
      `);
      for (const b of blkRes.rows) {
        stmt.run(b.id, b.blocker_id, b.blocked_id, Number(b.created_at));
      }
    }

    // 9. User Reports
    const repRes = await pool.query('SELECT * FROM public.user_reports;');
    if (repRes.rows && repRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO user_reports (id, reporter_id, reported_id, reason, details, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `);
      for (const r of repRes.rows) {
        stmt.run(r.id, r.reporter_id, r.reported_id, r.reason, r.details, r.status, Number(r.created_at));
      }
    }

    // 10. Audit Logs
    const logRes = await pool.query('SELECT * FROM public.audit_logs;');
    if (logRes.rows && logRes.rows.length > 0) {
      const stmt = localDb.prepare(`
        INSERT OR REPLACE INTO audit_logs (id, actor_id, action, target_id, details, ip_address, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `);
      for (const a of logRes.rows) {
        stmt.run(a.id, a.actor_id, a.action, a.target_id, a.details, a.ip_address || null, Number(a.created_at));
      }
    }

    console.log('[CloudSync] Render PostgreSQL sync completed successfully!');
  } catch (err) {
    console.error('[CloudSync Error on Pull]', err.message);
  }
}

/**
 * Asynchronously push a new/updated record to Render PostgreSQL
 */
function pushRecord(table, record) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      if (!pool) return;

      const keys = Object.keys(record);
      const cols = keys.map(k => `"${k}"`).join(', ');
      const placeholders = keys.map((_, i) => `$${i + 1}`).join(', ');
      const updateCols = keys.filter(k => k !== 'id').map(k => `"${k}" = EXCLUDED."${k}"`).join(', ');

      const query = `
        INSERT INTO public."${table}" (${cols})
        VALUES (${placeholders})
        ON CONFLICT ("id") DO UPDATE SET ${updateCols};
      `;

      await pool.query(query, Object.values(record));
      console.log(`[CloudSync] Pushed ${table} [${record.id || 'id'}] to Render PostgreSQL.`);
    } catch (err) {
      console.error(`[CloudSync Error pushing to ${table}]`, err.message);
    }
  });
}

/**
 * Asynchronously delete a record from Render PostgreSQL
 */
function deleteRecord(table, id) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      if (!pool) return;

      await pool.query(`DELETE FROM public."${table}" WHERE "id" = $1;`, [id]);
      console.log(`[CloudSync] Deleted ${table} [${id}] from Render PostgreSQL.`);
    } catch (err) {
      console.error(`[CloudSync Error deleting from ${table}]`, err.message);
    }
  });
}

/**
 * Asynchronously update a record in Render PostgreSQL
 */
function updateRecord(table, id, updates) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      if (!pool) return;

      const keys = Object.keys(updates);
      const setClause = keys.map((k, i) => `"${k}" = $${i + 1}`).join(', ');
      const values = [...Object.values(updates), id];

      const query = `
        UPDATE public."${table}"
        SET ${setClause}
        WHERE "id" = $${values.length};
      `;

      await pool.query(query, values);
      console.log(`[CloudSync] Updated ${table} [${id}] in Render PostgreSQL.`);
    } catch (err) {
      console.error(`[CloudSync Error updating ${table}]`, err.message);
    }
  });
}

// Keep pullFromSupabase as alias for backward compatibility
const pullFromSupabase = pullFromCloud;

/**
 * Asynchronously clear messages of a conversation in Render PostgreSQL
 */
function clearMessagesCloud(conversationId) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      if (!pool) return;

      await pool.query('DELETE FROM public.messages WHERE conversation_id = $1;', [conversationId]);
      console.log(`[CloudSync] Cleared messages for conversation [${conversationId}] in Render PostgreSQL.`);
    } catch (err) {
      console.error('[CloudSync Error clearing messages]', err.message);
    }
  });
}

/**
 * Asynchronously delete an entire conversation in Render PostgreSQL
 */
function deleteConversationCloud(conversationId) {
  if (isTestEnv()) return;

  enqueueSyncTask(async () => {
    try {
      const pool = getPgPool();
      if (!pool) return;

      await pool.query('DELETE FROM public.messages WHERE conversation_id = $1;', [conversationId]);
      await pool.query('DELETE FROM public.conversation_members WHERE conversation_id = $1;', [conversationId]);
      await pool.query('DELETE FROM public.conversations WHERE id = $1;', [conversationId]);
      console.log(`[CloudSync] Deleted conversation [${conversationId}] and members in Render PostgreSQL.`);
    } catch (err) {
      console.error('[CloudSync Error deleting conversation]', err.message);
    }
  });
}

module.exports = {
  pullFromCloud,
  pullFromSupabase,
  pushRecord,
  deleteRecord,
  updateRecord,
  clearMessagesCloud,
  deleteConversationCloud
};
