const crypto = require('node:crypto');
const { getDb, runTransaction } = require('../database/db');
const { generateLicenseKey } = require('../utils/idGenerator');

/**
 * Get user subscription & trial status with exact server-side calculations
 */
function getSubscriptionStatus(userId) {
  const db = getDb();
  const user = db.prepare(`
    SELECT id, role, status, trial_started_at, trial_expires_at, 
           subscription_status, premium_expires_at 
    FROM users 
    WHERE id = ?
  `).get(userId);

  if (!user) {
    const error = new Error('User not found');
    error.statusCode = 404;
    throw error;
  }

  const now = Date.now();
  const isMasterAdmin = user.role === 'admin';

  let isTrialActive = false;
  let isPremiumActive = false;
  let effectiveStatus = user.subscription_status;
  let remainingMs = 0;

  if (isMasterAdmin) {
    return {
      status: 'admin',
      isTrial: false,
      isPremium: true,
      canChat: true,
      trialExpiresAt: user.trial_expires_at,
      premiumExpiresAt: user.premium_expires_at,
      daysRemaining: 9999,
      notice: 'Administrator Unlimited Access'
    };
  }

  // Check premium first
  if (user.subscription_status === 'active' && user.premium_expires_at && user.premium_expires_at > now) {
    isPremiumActive = true;
    effectiveStatus = 'active';
    remainingMs = user.premium_expires_at - now;
  } 
  // Otherwise check trial
  else if (user.trial_expires_at && user.trial_expires_at > now) {
    isTrialActive = true;
    effectiveStatus = 'trial';
    remainingMs = user.trial_expires_at - now;
  } 
  // Expired
  else {
    effectiveStatus = 'expired';
    remainingMs = 0;
  }

  const daysRemaining = Math.max(0, Math.ceil(remainingMs / (24 * 60 * 60 * 1000)));
  const canChat = isPremiumActive || isTrialActive;

  return {
    status: effectiveStatus,
    isTrial: isTrialActive,
    isPremium: isPremiumActive,
    canChat,
    trialExpiresAt: user.trial_expires_at,
    premiumExpiresAt: user.premium_expires_at,
    daysRemaining,
    notice: canChat ? (isTrialActive ? `${daysRemaining} days remaining in trial` : `${daysRemaining} days of premium active`) : 'Contact the Owner to Get Your License'
  };
}

/**
 * Redeem a license key for a user
 * Fully transactional and tamper-proof against client clocks
 */
function redeemLicense({ key, userId }) {
  if (!key || typeof key !== 'string') {
    const error = new Error('License key is required');
    error.statusCode = 400;
    error.code = 'INVALID_LICENSE_KEY';
    throw error;
  }

  const normalizedKey = key.trim().toUpperCase();

  return runTransaction((db) => {
    // 1. Fetch license
    const license = db.prepare(`
      SELECT * FROM licenses WHERE license_key = ?
    `).get(normalizedKey);

    if (!license) {
      const error = new Error('Invalid license key');
      error.statusCode = 404;
      error.code = 'LICENSE_NOT_FOUND';
      throw error;
    }

    if (license.status === 'revoked') {
      const error = new Error('This license has been revoked by an administrator');
      error.statusCode = 400;
      error.code = 'LICENSE_REVOKED';
      throw error;
    }

    if (license.status === 'redeemed') {
      const error = new Error('This license key has already been redeemed');
      error.statusCode = 400;
      error.code = 'LICENSE_ALREADY_USED';
      throw error;
    }

    // 2. Fetch associated plan
    const plan = db.prepare('SELECT * FROM plans WHERE id = ?').get(license.plan_id);
    if (!plan || !plan.is_active) {
      const error = new Error('The plan associated with this license is no longer active');
      error.statusCode = 400;
      error.code = 'PLAN_INACTIVE';
      throw error;
    }

    // 3. Fetch user
    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
    if (!user || user.status !== 'active') {
      const error = new Error('User not eligible for license activation');
      error.statusCode = 400;
      error.code = 'USER_NOT_ELIGIBLE';
      throw error;
    }

    const now = Date.now();
    const durationMs = plan.duration_days * 24 * 60 * 60 * 1000;

    // If user already has active premium, extend from their current expiry
    let baseTime = now;
    if (user.subscription_status === 'active' && user.premium_expires_at && user.premium_expires_at > now) {
      baseTime = user.premium_expires_at;
    }
    const newPremiumExpiresAt = baseTime + durationMs;

    // 4. Update license record
    db.prepare(`
      UPDATE licenses 
      SET status = 'redeemed', redeemed_by_user_id = ?, redeemed_at = ?
      WHERE id = ?
    `).run(user.id, now, license.id);

    // 5. Update user record
    db.prepare(`
      UPDATE users 
      SET subscription_status = 'active', premium_expires_at = ?, updated_at = ?
      WHERE id = ?
    `).run(newPremiumExpiresAt, now, user.id);

    // 6. Record audit log
    db.prepare(`
      INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(
      `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
      user.id,
      'LICENSE_REDEEMED',
      license.id,
      JSON.stringify({
        key: license.license_key,
        plan_id: plan.id,
        plan_name: plan.name,
        duration_days: plan.duration_days,
        new_expires_at: newPremiumExpiresAt
      }),
      now
    );

    const daysTotal = Math.ceil((newPremiumExpiresAt - now) / (24 * 60 * 60 * 1000));

    return {
      success: true,
      message: `Successfully activated ${plan.name} (${plan.duration_days} days added)`,
      plan_id: plan.id,
      plan_name: plan.name,
      duration_days: plan.duration_days,
      premium_expires_at: newPremiumExpiresAt,
      days_remaining: daysTotal
    };
  });
}

/**
 * Generate a new license key (Admin function)
 */
function createLicense({ planId, adminId, notes = null }) {
  const db = getDb();
  const plan = db.prepare('SELECT * FROM plans WHERE id = ?').get(planId);
  if (!plan) {
    const error = new Error(`Plan '${planId}' does not exist`);
    error.statusCode = 400;
    error.code = 'INVALID_PLAN';
    throw error;
  }

  const id = `lic_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
  const key = generateLicenseKey();
  const now = Date.now();

  db.prepare(`
    INSERT INTO licenses (
      id, license_key, plan_id, status, created_by_admin_id, notes, created_at
    ) VALUES (?, ?, ?, 'unused', ?, ?, ?)
  `).run(id, key, planId, adminId, notes ? notes.trim() : null, now);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    'LICENSE_GENERATED',
    id,
    JSON.stringify({ license_key: key, plan_id: planId, plan_name: plan.name }),
    now
  );

  return {
    id,
    license_key: key,
    plan_id: planId,
    plan_name: plan.name,
    duration_days: plan.duration_days,
    status: 'unused',
    notes,
    created_at: now
  };
}

/**
 * Revoke a license key (Admin function)
 */
function revokeLicense(licenseId, adminId) {
  const db = getDb();
  const now = Date.now();
  const license = db.prepare('SELECT * FROM licenses WHERE id = ?').get(licenseId);
  if (!license) {
    const error = new Error('License not found');
    error.statusCode = 404;
    throw error;
  }

  db.prepare(`
    UPDATE licenses SET status = 'revoked', revoked_at = ? WHERE id = ?
  `).run(now, licenseId);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    'LICENSE_REVOKED',
    licenseId,
    JSON.stringify({ license_key: license.license_key }),
    now
  );

  return { success: true, licenseId, status: 'revoked' };
}

/**
 * List all available plans
 */
function getPlans() {
  const db = getDb();
  const rows = db.prepare('SELECT * FROM plans WHERE is_active = 1 ORDER BY duration_days ASC').all();
  return rows.map((p) => ({
    ...p,
    features: p.features ? JSON.parse(p.features) : []
  }));
}

/**
 * Update plan details (Admin function)
 */
function updatePlan(planId, { name, duration_days, description, features, is_active }, adminId) {
  const db = getDb();
  const plan = db.prepare('SELECT * FROM plans WHERE id = ?').get(planId);
  if (!plan) {
    const error = new Error('Plan not found');
    error.statusCode = 404;
    throw error;
  }

  const updatedName = name ? name.trim() : plan.name;
  const updatedDuration = duration_days ? parseInt(duration_days, 10) : plan.duration_days;
  const updatedDesc = description !== undefined ? description : plan.description;
  const updatedFeatures = features !== undefined ? (typeof features === 'string' ? features : JSON.stringify(features)) : plan.features;
  const updatedActive = is_active !== undefined ? (is_active ? 1 : 0) : plan.is_active;
  const now = Date.now();

  db.prepare(`
    UPDATE plans 
    SET name = ?, duration_days = ?, description = ?, features = ?, is_active = ?, updated_at = ?
    WHERE id = ?
  `).run(updatedName, updatedDuration, updatedDesc, updatedFeatures, updatedActive, now, planId);

  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, action, target_id, details, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(
    `audit_${now}_${crypto.randomBytes(4).toString('hex')}`,
    adminId,
    'PLAN_UPDATED',
    planId,
    JSON.stringify({ name: updatedName, duration_days: updatedDuration }),
    now
  );

  return {
    id: planId,
    name: updatedName,
    duration_days: updatedDuration,
    description: updatedDesc,
    features: JSON.parse(updatedFeatures),
    is_active: updatedActive === 1,
    updated_at: now
  };
}

module.exports = {
  getSubscriptionStatus,
  redeemLicense,
  createLicense,
  revokeLicense,
  getPlans,
  updatePlan
};
