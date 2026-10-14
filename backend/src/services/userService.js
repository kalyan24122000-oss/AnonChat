const crypto = require('node:crypto');
const { getDb } = require('../database/db');
const { generateUserId, generateRecoveryCode } = require('../utils/idGenerator');
const { hashPassword, verifyPassword, hashRecoveryCode, verifyRecoveryCode, signToken } = require('../utils/crypto');
const config = require('../config');

/**
 * Register a new anonymous user
 * Zero email / phone required. Generates unique USR ID.
 */
async function registerUser({ displayName, password, publicKey = null }) {
  const db = getDb();

  if (!password || password.length < 8) {
    const error = new Error('Password must be at least 8 characters long');
    error.statusCode = 400;
    error.code = 'INVALID_PASSWORD';
    throw error;
  }

  // Generate unique User ID
  let userId;
  let attempts = 0;
  while (attempts < 10) {
    const candidate = generateUserId();
    const existing = db.prepare('SELECT id FROM users WHERE id = ?').get(candidate);
    if (!existing) {
      userId = candidate;
      break;
    }
    attempts++;
  }

  if (!userId) {
    const error = new Error('Failed to generate a unique user ID, please try again');
    error.statusCode = 500;
    throw error;
  }

  const passwordHash = await hashPassword(password);
  const recoveryCode = generateRecoveryCode();
  const recoveryHash = hashRecoveryCode(recoveryCode);

  const now = Date.now();
  const trialDurationMs = config.trialDurationDays * 24 * 60 * 60 * 1000;
  const trialExpiresAt = now + trialDurationMs;

  db.prepare(`
    INSERT INTO users (
      id, display_name, password_hash, role, public_key, recovery_code_hash,
      status, trial_started_at, trial_expires_at, subscription_status,
      created_at, updated_at
    ) VALUES (?, ?, ?, 'user', ?, ?, 'active', ?, ?, 'trial', ?, ?)
  `).run(
    userId,
    displayName ? displayName.trim().substring(0, 50) : null,
    passwordHash,
    publicKey || null,
    recoveryHash,
    now,
    trialExpiresAt,
    now,
    now
  );

  const user = {
    id: userId,
    display_name: displayName ? displayName.trim().substring(0, 50) : null,
    role: 'user',
    status: 'active',
    public_key: publicKey || null,
    trial_started_at: now,
    trial_expires_at: trialExpiresAt,
    subscription_status: 'trial',
    premium_expires_at: null,
    created_at: now
  };

  const token = signToken({ id: user.id, role: user.role });

  // Record audit log
  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`,
    userId,
    'USER_REGISTERED',
    userId,
    JSON.stringify({ trial_expires_at: trialExpiresAt }),
    now
  );

  return {
    user,
    token,
    recoveryCode // Provided ONCE to user upon registration
  };
}

/**
 * Authenticate user with ID and password
 */
async function loginUser({ id, password, clientIp = null }) {
  const db = getDb();
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(id ? id.trim() : '');

  if (!user || user.status === 'deleted') {
    const error = new Error('Invalid User ID or password');
    error.statusCode = 401;
    error.code = 'INVALID_CREDENTIALS';
    throw error;
  }

  if (user.status === 'suspended') {
    const error = new Error('This account has been suspended by an administrator');
    error.statusCode = 403;
    error.code = 'ACCOUNT_SUSPENDED';
    throw error;
  }

  const isValid = await verifyPassword(password, user.password_hash);
  if (!isValid) {
    const error = new Error('Invalid User ID or password');
    error.statusCode = 401;
    error.code = 'INVALID_CREDENTIALS';
    throw error;
  }

  // Update subscription status if trial or premium expired
  const now = Date.now();
  let subStatus = user.subscription_status;
  if (subStatus === 'trial' && user.trial_expires_at < now) {
    subStatus = 'expired';
    db.prepare('UPDATE users SET subscription_status = ?, updated_at = ? WHERE id = ?')
      .run('expired', now, user.id);
  } else if (subStatus === 'active' && user.premium_expires_at && user.premium_expires_at < now) {
    subStatus = 'expired';
    db.prepare('UPDATE users SET subscription_status = ?, updated_at = ? WHERE id = ?')
      .run('expired', now, user.id);
  }

  const token = signToken({ id: user.id, role: user.role });

  // Safe user profile without password or recovery code hashes
  const safeUser = {
    id: user.id,
    display_name: user.display_name,
    role: user.role,
    status: user.status,
    public_key: user.public_key,
    trial_started_at: user.trial_started_at,
    trial_expires_at: user.trial_expires_at,
    subscription_status: subStatus,
    premium_expires_at: user.premium_expires_at,
    created_at: user.created_at
  };

  return { user: safeUser, token };
}

/**
 * Recover account using ID and Recovery Code
 */
async function recoverAccount({ id, recoveryCode, newPassword }) {
  const db = getDb();
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(id ? id.trim() : '');

  if (!user || !user.recovery_code_hash || user.status === 'deleted') {
    const error = new Error('Account not found or no recovery credentials configured');
    error.statusCode = 400;
    error.code = 'RECOVERY_FAILED';
    throw error;
  }

  const matches = verifyRecoveryCode(recoveryCode, user.recovery_code_hash);
  if (!matches) {
    const error = new Error('Invalid recovery code');
    error.statusCode = 401;
    error.code = 'INVALID_RECOVERY_CODE';
    throw error;
  }

  if (!newPassword || newPassword.length < 8) {
    const error = new Error('New password must be at least 8 characters long');
    error.statusCode = 400;
    error.code = 'INVALID_PASSWORD';
    throw error;
  }

  const newPasswordHash = await hashPassword(newPassword);
  const newRecoveryCode = generateRecoveryCode();
  const newRecoveryHash = hashRecoveryCode(newRecoveryCode);
  const now = Date.now();

  db.prepare(`
    UPDATE users 
    SET password_hash = ?, recovery_code_hash = ?, updated_at = ?
    WHERE id = ?
  `).run(newPasswordHash, newRecoveryHash, now, user.id);

  // Audit log
  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    user.id,
    'ACCOUNT_RECOVERED',
    user.id,
    JSON.stringify({ method: 'recovery_code' }),
    now
  );

  return {
    success: true,
    message: 'Account recovered successfully. Please save your new recovery code.',
    newRecoveryCode
  };
}

/**
 * Permanently delete or anonymize an account
 */
async function deleteAccount(userId) {
  const db = getDb();
  const now = Date.now();

  db.prepare(`
    UPDATE users 
    SET status = 'deleted', display_name = 'Deleted User', password_hash = 'DELETED',
        public_key = NULL, recovery_code_hash = NULL, updated_at = ?
    WHERE id = ?
  `).run(now, userId);

  // Record audit log
  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    userId,
    'USER_DELETED_ACCOUNT',
    userId,
    JSON.stringify({ timestamp: now }),
    now
  );

  return { success: true };
}

/**
 * Search user by ID (exact or prefix)
 */
function searchUserById(searchId, currentUserId) {
  const db = getDb();
  const cleaned = searchId ? searchId.trim().toUpperCase() : '';
  if (!cleaned || cleaned.length < 3) {
    return [];
  }

  const rows = db.prepare(`
    SELECT id, display_name, public_key, status
    FROM users 
    WHERE id LIKE ? AND status = 'active' AND id != ?
    LIMIT 10
  `).all(`${cleaned}%`, currentUserId);

  return rows;
}

/**
 * Block another user
 */
function blockUser(blockerId, blockedId) {
  if (blockerId === blockedId) {
    const error = new Error('Cannot block yourself');
    error.statusCode = 400;
    throw error;
  }
  const db = getDb();
  const id = `block_${blockerId}_${blockedId}`;
  try {
    db.prepare(`
      INSERT OR IGNORE INTO blocked_users (id, blocker_id, blocked_id, created_at)
      VALUES (?, ?, ?, ?)
    `).run(id, blockerId, blockedId, Date.now());
  } catch (e) {
    // Already blocked
  }
  return { success: true, blockedId };
}

/**
 * Unblock user
 */
function unblockUser(blockerId, blockedId) {
  const db = getDb();
  db.prepare('DELETE FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?')
    .run(blockerId, blockedId);
  return { success: true, unblockedId: blockedId };
}

/**
 * List blocked users for user
 */
function getBlockedUsers(userId) {
  const db = getDb();
  return db.prepare(`
    SELECT b.blocked_id, u.display_name, b.created_at
    FROM blocked_users b
    LEFT JOIN users u ON b.blocked_id = u.id
    WHERE b.blocker_id = ?
  `).all(userId);
}

/**
 * Report a user
 */
function reportUser(reporterId, reportedId, reason) {
  const db = getDb();
  const id = `report_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
  db.prepare(`
    INSERT INTO user_reports (id, reporter_id, reported_id, reason, status, created_at)
    VALUES (?, ?, ?, ?, 'pending', ?)
  `).run(id, reporterId, reportedId, reason ? reason.trim().substring(0, 500) : 'Unspecified', Date.now());
  return { success: true, reportId: id };
}

/**
 * Update user's E2EE public key
 */
function updatePublicKey(userId, publicKey) {
  const db = getDb();
  db.prepare('UPDATE users SET public_key = ?, updated_at = ? WHERE id = ?')
    .run(publicKey, Date.now(), userId);
  return { success: true, publicKey };
}

module.exports = {
  registerUser,
  loginUser,
  recoverAccount,
  deleteAccount,
  searchUserById,
  blockUser,
  unblockUser,
  getBlockedUsers,
  reportUser,
  updatePublicKey
};
