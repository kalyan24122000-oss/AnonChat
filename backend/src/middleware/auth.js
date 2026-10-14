const { verifyToken } = require('../utils/crypto');
const { getDb } = require('../database/db');

/**
 * Middleware to authenticate requests via Bearer JWT token
 */
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.startsWith('Bearer ') 
    ? authHeader.substring(7).trim() 
    : null;

  if (!token) {
    return res.status(401).json({
      error: 'UNAUTHORIZED',
      message: 'Authentication token required'
    });
  }

  try {
    const payload = verifyToken(token);
    const db = getDb();
    const user = db.prepare(`
      SELECT id, display_name, role, status, trial_started_at, trial_expires_at, 
             subscription_status, premium_expires_at, created_at, public_key
      FROM users 
      WHERE id = ?
    `).get(payload.id);

    if (!user) {
      return res.status(401).json({
        error: 'USER_NOT_FOUND',
        message: 'Account no longer exists'
      });
    }

    if (user.status === 'suspended') {
      return res.status(403).json({
        error: 'ACCOUNT_SUSPENDED',
        message: 'This account has been suspended by an administrator'
      });
    }

    if (user.status === 'deleted') {
      return res.status(401).json({
        error: 'ACCOUNT_DELETED',
        message: 'This account has been deleted'
      });
    }

    req.user = user;
    next();
  } catch (err) {
    return res.status(401).json({
      error: 'INVALID_TOKEN',
      message: 'Token has expired or is invalid'
    });
  }
}

/**
 * Enforce administrator role.
 * IMPORTANT: Role is verified from the database record, NEVER from the ID prefix.
 */
function requireAdmin(req, res, next) {
  if (!req.user) {
    return res.status(401).json({
      error: 'UNAUTHORIZED',
      message: 'Authentication required'
    });
  }

  if (req.user.role !== 'admin') {
    return res.status(403).json({
      error: 'FORBIDDEN',
      message: 'Administrator privileges required'
    });
  }

  next();
}

/**
 * Middleware to check trial & subscription status for messaging
 * Device clock changes cannot bypass this because server time Date.now() is verified.
 */
function requireActiveSubscription(req, res, next) {
  if (!req.user) {
    return res.status(401).json({
      error: 'UNAUTHORIZED',
      message: 'Authentication required'
    });
  }

  // Admins always have active access
  if (req.user.role === 'admin') {
    return next();
  }

  const now = Date.now();
  const trialExpired = userTrialExpired(req.user, now);
  const premiumExpired = userPremiumExpired(req.user, now);

  if (trialExpired && premiumExpired) {
    return res.status(403).json({
      error: 'SUBSCRIPTION_EXPIRED',
      message: 'Contact the Owner to Get Your License',
      trial_expires_at: req.user.trial_expires_at,
      subscription_status: 'expired'
    });
  }

  next();
}

function userTrialExpired(user, now) {
  return !user.trial_expires_at || user.trial_expires_at < now;
}

function userPremiumExpired(user, now) {
  if (user.subscription_status !== 'active') return true;
  return !user.premium_expires_at || user.premium_expires_at < now;
}

module.exports = {
  authenticateToken,
  requireAdmin,
  requireActiveSubscription,
  userTrialExpired,
  userPremiumExpired
};
