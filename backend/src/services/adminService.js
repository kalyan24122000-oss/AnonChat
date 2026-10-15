const crypto = require('node:crypto');
const { getDb } = require('../database/db');
const { hashPassword } = require('../utils/crypto');
const { generateRandomString } = require('../utils/idGenerator');
const { pushRecord } = require('../database/cloudSync');

/**
 * Get aggregated statistics for the admin dashboard
 */
function getDashboardStats() {
  const db = getDb();
  const now = Date.now();

  const totalUsers = db.prepare("SELECT COUNT(*) as count FROM users WHERE role != 'admin' AND status != 'deleted'").get().count;
  const activeTrials = db.prepare("SELECT COUNT(*) as count FROM users WHERE subscription_status = 'trial' AND trial_expires_at > ? AND status = 'active'").get(now).count;
  const activePremiums = db.prepare("SELECT COUNT(*) as count FROM users WHERE subscription_status = 'active' AND (premium_expires_at IS NULL OR premium_expires_at > ?) AND status = 'active'").get(now).count;
  const expiredUsers = db.prepare("SELECT COUNT(*) as count FROM users WHERE role != 'admin' AND trial_expires_at <= ? AND (subscription_status != 'active' OR premium_expires_at <= ?) AND status = 'active'").get(now, now).count;
  const suspendedUsers = db.prepare("SELECT COUNT(*) as count FROM users WHERE status = 'suspended'").get().count;

  const totalConversations = db.prepare('SELECT COUNT(*) as count FROM conversations').get().count;
  const totalMessages = db.prepare('SELECT COUNT(*) as count FROM messages').get().count;

  const totalLicenses = db.prepare('SELECT COUNT(*) as count FROM licenses').get().count;
  const unusedLicenses = db.prepare("SELECT COUNT(*) as count FROM licenses WHERE status = 'unused'").get().count;
  const redeemedLicenses = db.prepare("SELECT COUNT(*) as count FROM licenses WHERE status = 'redeemed'").get().count;
  const revokedLicenses = db.prepare("SELECT COUNT(*) as count FROM licenses WHERE status = 'revoked'").get().count;

  // Recent audit activities
  const recentAudits = db.prepare(`
    SELECT id, actor_id, action, target_id, details, ip_address, created_at
    FROM audit_logs
    ORDER BY created_at DESC
    LIMIT 10
  `).all();

  return {
    users: {
      total: totalUsers,
      activeTrials,
      activePremiums,
      expired: expiredUsers,
      suspended: suspendedUsers
    },
    messaging: {
      totalConversations,
      totalMessages
    },
    licenses: {
      total: totalLicenses,
      unused: unusedLicenses,
      redeemed: redeemedLicenses,
      revoked: revokedLicenses
    },
    recentAudits
  };
}

/**
 * List all users with filtering, sorting, and pagination
 */
function listUsers({ page = 1, limit = 20, search = '', role = '', status = '', subStatus = '' }) {
  const db = getDb();
  const offset = (page - 1) * limit;

  let whereClauses = ["status != 'deleted'"];
  let params = [];

  if (search) {
    whereClauses.push('(id LIKE ? OR display_name LIKE ?)');
    params.push(`%${search.trim()}%`, `%${search.trim()}%`);
  }

  if (role) {
    whereClauses.push('role = ?');
    params.push(role);
  }

  if (status) {
    whereClauses.push('status = ?');
    params.push(status);
  }

  if (subStatus) {
    whereClauses.push('subscription_status = ?');
    params.push(subStatus);
  }

  const whereStr = whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

  const total = db.prepare(`SELECT COUNT(*) as count FROM users ${whereStr}`).get(...params).count;

  const users = db.prepare(`
    SELECT id, display_name, role, status, plain_password as password, badge_color, trial_started_at, trial_expires_at,
           subscription_status, premium_expires_at, created_at, updated_at
    FROM users
    ${whereStr}
    ORDER BY created_at DESC
    LIMIT ? OFFSET ?
  `).all(...params, limit, offset);

  return {
    total,
    page,
    limit,
    totalPages: Math.ceil(total / limit),
    users
  };
}

/**
 * Suspend a user account
 */
function suspendUser(userId, adminId, reason = null) {
  const db = getDb();
  const user = db.prepare('SELECT id, role FROM users WHERE id = ?').get(userId);
  if (!user) {
    const error = new Error('User not found');
    error.statusCode = 404;
    throw error;
  }

  if (user.role === 'admin') {
    const error = new Error('Cannot suspend an administrator account');
    error.statusCode = 400;
    throw error;
  }

  const now = Date.now();
  db.prepare("UPDATE users SET status = 'suspended', updated_at = ? WHERE id = ?").run(now, userId);
  const updatedUser = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (updatedUser) pushRecord('users', updatedUser);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, 'USER_SUSPENDED', ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    userId,
    JSON.stringify({ reason: reason || 'Administrative action' }),
    now
  );

  return { success: true, userId, status: 'suspended' };
}

/**
 * Reactivate a suspended user
 */
function reactivateUser(userId, adminId) {
  const db = getDb();
  const now = Date.now();
  db.prepare("UPDATE users SET status = 'active', updated_at = ? WHERE id = ?").run(now, userId);
  const updatedUser = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (updatedUser) pushRecord('users', updatedUser);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, 'USER_REACTIVATED', ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    userId,
    JSON.stringify({}),
    now
  );

  return { success: true, userId, status: 'active' };
}

/**
 * Reset user password with a newly generated secure temporary password.
 * NEVER reveals existing password!
 */
async function resetUserPassword(userId, adminId) {
  const db = getDb();
  const user = db.prepare('SELECT id, role FROM users WHERE id = ?').get(userId);
  if (!user) {
    const error = new Error('User not found');
    error.statusCode = 404;
    throw error;
  }

  // Generate strong temporary password: 12 chars
  const tempPassword = `Tmp#${generateRandomString(8)}!`;
  const passwordHash = await hashPassword(tempPassword);
  const now = Date.now();

  db.prepare('UPDATE users SET password_hash = ?, plain_password = ?, updated_at = ? WHERE id = ?')
    .run(passwordHash, tempPassword, now, userId);
  const updatedUser = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
  if (updatedUser) pushRecord('users', updatedUser);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, 'USER_PASSWORD_RESET', ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    userId,
    JSON.stringify({ triggered_by_admin: true }),
    now
  );

  return {
    success: true,
    userId,
    temporaryPassword: tempPassword,
    message: 'Temporary password generated. Instruct user to change immediately upon login.'
  };
}

/**
 * List all license records with plan info
 */
function listLicenses({ page = 1, limit = 50, status = '', planId = '' }) {
  const db = getDb();
  const offset = (page - 1) * limit;

  let whereClauses = [];
  let params = [];

  if (status) {
    whereClauses.push('l.status = ?');
    params.push(status);
  }

  if (planId) {
    whereClauses.push('l.plan_id = ?');
    params.push(planId);
  }

  const whereStr = whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

  const total = db.prepare(`SELECT COUNT(*) as count FROM licenses l ${whereStr}`).get(...params).count;

  const licenses = db.prepare(`
    SELECT l.*, p.name as plan_name, p.duration_days,
           u_red.display_name as redeemed_by_display_name
    FROM licenses l
    JOIN plans p ON l.plan_id = p.id
    LEFT JOIN users u_red ON l.redeemed_by_user_id = u_red.id
    ${whereStr}
    ORDER BY l.created_at DESC
    LIMIT ? OFFSET ?
  `).all(...params, limit, offset);

  return {
    total,
    page,
    limit,
    totalPages: Math.ceil(total / limit),
    licenses
  };
}

/**
 * List audit logs
 */
function listAuditLogs({ page = 1, limit = 50, action = '' }) {
  const db = getDb();
  const offset = (page - 1) * limit;

  let whereClauses = [];
  let params = [];

  if (action) {
    whereClauses.push('action = ?');
    params.push(action);
  }

  const whereStr = whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

  const total = db.prepare(`SELECT COUNT(*) as count FROM audit_logs ${whereStr}`).get(...params).count;

  const logs = db.prepare(`
    SELECT * FROM audit_logs
    ${whereStr}
    ORDER BY created_at DESC
    LIMIT ? OFFSET ?
  `).all(...params, limit, offset);

  return {
    total,
    page,
    limit,
    totalPages: Math.ceil(total / limit),
    logs
  };
}

/**
 * Authorized database inspector.
 * Provides safe visibility into table sizes and sanitized metadata without revealing plaintext messages or password hashes.
 */
function getDatabaseInspection() {
  const db = getDb();
  const tables = [
    'users',
    'plans',
    'licenses',
    'conversations',
    'conversation_members',
    'messages',
    'blocked_users',
    'user_reports',
    'audit_logs'
  ];

  const summary = tables.map((tableName) => {
    const count = db.prepare(`SELECT COUNT(*) as count FROM ${tableName}`).get().count;
    return {
      table: tableName,
      recordCount: count
    };
  });

  return { tables: summary };
}

/**
 * Create a new administrator account
 */
async function createAdminAccount({ id, password, displayName, badgeColor = '#FF9800', actorId = 'system' }) {
  const db = getDb();
  if (!id || !password) {
    const error = new Error('Admin ID and password are required');
    error.statusCode = 400;
    throw error;
  }

  const cleanId = id.trim().toUpperCase().startsWith('ADM-') 
    ? id.trim().toUpperCase() 
    : `ADM-${id.trim().toUpperCase()}`;

  const existing = db.prepare('SELECT id FROM users WHERE id = ?').get(cleanId);
  if (existing) {
    const error = new Error(`Administrator or user with ID ${cleanId} already exists`);
    error.statusCode = 409;
    throw error;
  }

  const now = Date.now();
  const passwordHash = await hashPassword(password);
  const trialExpiresAt = now + (100 * 365 * 24 * 60 * 60 * 1000); // 100 years

  db.prepare(`
    INSERT INTO users (
      id, display_name, password_hash, plain_password, badge_color, role, status,
      trial_started_at, trial_expires_at, subscription_status, premium_expires_at,
      created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, 'admin', 'active', ?, ?, 'active', ?, ?, ?)
  `).run(
    cleanId,
    displayName ? displayName.trim() : 'System Administrator',
    passwordHash,
    password,
    badgeColor || '#FF9800',
    now,
    trialExpiresAt,
    trialExpiresAt,
    now,
    now
  );

  const createdAdmin = db.prepare('SELECT * FROM users WHERE id = ?').get(cleanId);
  if (createdAdmin) {
    pushRecord('users', createdAdmin);
  }

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, 'ADMIN_CREATED', ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    actorId,
    cleanId,
    JSON.stringify({ adminId: cleanId, displayName }),
    now
  );

  return {
    success: true,
    admin: {
      id: cleanId,
      displayName: displayName ? displayName.trim() : 'System Administrator',
      role: 'admin',
      badgeColor: badgeColor || '#FF9800',
      status: 'active'
    }
  };
}

module.exports = {
  getDashboardStats,
  listUsers,
  suspendUser,
  reactivateUser,
  resetUserPassword,
  listLicenses,
  listAuditLogs,
  getDatabaseInspection,
  createAdminAccount
};
