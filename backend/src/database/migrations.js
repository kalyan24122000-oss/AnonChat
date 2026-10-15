const { getDb } = require('./db');

const MIGRATIONS = [
  {
    id: 1,
    name: '001_initial_schema',
    up: (db) => {
      // 1. Users Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS users (
          id TEXT PRIMARY KEY,
          display_name TEXT,
          password_hash TEXT NOT NULL,
          role TEXT NOT NULL DEFAULT 'user',
          public_key TEXT,
          recovery_code_hash TEXT,
          status TEXT NOT NULL DEFAULT 'active',
          trial_started_at INTEGER NOT NULL,
          trial_expires_at INTEGER NOT NULL,
          subscription_status TEXT NOT NULL DEFAULT 'trial',
          premium_expires_at INTEGER DEFAULT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
        CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
        CREATE INDEX IF NOT EXISTS idx_users_sub ON users(subscription_status, trial_expires_at);
      `);

      // 2. Plans Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS plans (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          duration_days INTEGER NOT NULL,
          description TEXT,
          features TEXT,
          is_active INTEGER NOT NULL DEFAULT 1,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        );
      `);

      // 3. Licenses Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS licenses (
          id TEXT PRIMARY KEY,
          license_key TEXT UNIQUE NOT NULL,
          plan_id TEXT NOT NULL REFERENCES plans(id),
          status TEXT NOT NULL DEFAULT 'unused',
          created_by_admin_id TEXT REFERENCES users(id),
          redeemed_by_user_id TEXT REFERENCES users(id),
          redeemed_at INTEGER,
          revoked_at INTEGER,
          notes TEXT,
          created_at INTEGER NOT NULL
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_licenses_key ON licenses(license_key);
        CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);
      `);

      // 4. Conversations Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS conversations (
          id TEXT PRIMARY KEY,
          type TEXT NOT NULL DEFAULT 'direct',
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        );
      `);

      // 5. Conversation Members Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS conversation_members (
          id TEXT PRIMARY KEY,
          conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
          user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          joined_at INTEGER NOT NULL,
          last_read_at INTEGER NOT NULL DEFAULT 0,
          UNIQUE(conversation_id, user_id)
        );

        CREATE INDEX IF NOT EXISTS idx_conv_members_user ON conversation_members(user_id);
        CREATE INDEX IF NOT EXISTS idx_conv_members_conv ON conversation_members(conversation_id);
      `);

      // 6. Messages Table (E2EE Encrypted Payload only)
      db.exec(`
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
          delivered_at INTEGER,
          read_at INTEGER,
          created_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_messages_conv_created ON messages(conversation_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_messages_recipient_status ON messages(recipient_id, status);
      `);

      // 7. Blocked Users Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS blocked_users (
          id TEXT PRIMARY KEY,
          blocker_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          blocked_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at INTEGER NOT NULL,
          UNIQUE(blocker_id, blocked_id)
        );

        CREATE INDEX IF NOT EXISTS idx_blocked_blocker ON blocked_users(blocker_id);
      `);

      // 8. User Reports Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS user_reports (
          id TEXT PRIMARY KEY,
          reporter_id TEXT NOT NULL REFERENCES users(id),
          reported_id TEXT NOT NULL REFERENCES users(id),
          reason TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'pending',
          created_at INTEGER NOT NULL
        );
      `);

      // 9. Audit Logs Table
      db.exec(`
        CREATE TABLE IF NOT EXISTS audit_logs (
          id TEXT PRIMARY KEY,
          actor_id TEXT,
          action TEXT NOT NULL,
          target_id TEXT,
          details TEXT,
          ip_address TEXT,
          created_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);
      `);
    }
  },
  {
    id: 2,
    name: '002_media_and_plain_passwords',
    up: (db) => {
      // 1. Add plain_password and badge_color to users table if not exists
      try {
        db.exec("ALTER TABLE users ADD COLUMN plain_password TEXT;");
      } catch (e) {
        // column may already exist
      }
      try {
        db.exec("ALTER TABLE users ADD COLUMN badge_color TEXT DEFAULT '#FF9800';");
      } catch (e) {
        // column may already exist
      }

      // 2. Media files table with 7-day expiration
      db.exec(`
        CREATE TABLE IF NOT EXISTS media_files (
          id TEXT PRIMARY KEY,
          sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          file_name TEXT NOT NULL,
          mime_type TEXT NOT NULL,
          file_size INTEGER NOT NULL,
          file_path TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          expires_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_media_expires ON media_files(expires_at);
        CREATE INDEX IF NOT EXISTS idx_media_sender ON media_files(sender_id);
      `);
    }
  }
];

/**
 * Execute all pending database migrations
 * @param {DatabaseSync} [db]
 */
function runMigrations(db = null) {
  const conn = db || getDb();

  conn.exec(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      applied_at INTEGER NOT NULL
    );
  `);

  const appliedRows = conn.prepare('SELECT id FROM schema_migrations').all();
  const appliedIds = new Set(appliedRows.map((r) => r.id));

  for (const migration of MIGRATIONS) {
    if (!appliedIds.has(migration.id)) {
      console.log(`[Database] Applying migration ${migration.id}: ${migration.name}`);
      migration.up(conn);
      conn.prepare('INSERT INTO schema_migrations (id, name, applied_at) VALUES (?, ?, ?)')
        .run(migration.id, migration.name, Date.now());
    }
  }
}

module.exports = {
  runMigrations
};
