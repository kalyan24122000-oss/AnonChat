const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');

const testDbPath = path.resolve(__dirname, '../data/test_license.db');
if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
process.env.DB_PATH = testDbPath;
process.env.JWT_SECRET = 'test_secret_for_anonchat_units_2026';

const { getDb, closeDb } = require('../src/database/db');
const { runMigrations } = require('../src/database/migrations');
const { runSeeds } = require('../src/database/seeds');
const userService = require('../src/services/userService');
const licenseService = require('../src/services/licenseService');

test.before(async () => {
  runMigrations();
  await runSeeds();
});

test.after(() => {
  closeDb();
  if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
});

test('Licensing: 4 configurable plans exist in database', () => {
  const plans = licenseService.getPlans();
  assert.equal(plans.length, 4);
  const planIds = plans.map((p) => p.id);
  assert.ok(planIds.includes('starter'));
  assert.ok(planIds.includes('pro'));
  assert.ok(planIds.includes('elite'));
  assert.ok(planIds.includes('annual'));
});

test('Licensing: Generate license key and redeem for user', async () => {
  const regUser = await userService.registerUser({
    displayName: 'LicenseTester',
    password: 'PasswordTest123!'
  });

  // Check initial trial status
  let status = licenseService.getSubscriptionStatus(regUser.user.id);
  assert.equal(status.status, 'trial');
  assert.ok(status.canChat);
  assert.equal(status.daysRemaining, 7);

  // Generate a Pro (90-day) license key as admin
  const license = licenseService.createLicense({
    planId: 'pro',
    adminId: 'ADM-MASTER-01',
    notes: 'Test pro key'
  });

  assert.ok(license.license_key);
  assert.equal(license.status, 'unused');
  assert.equal(license.duration_days, 90);

  // User redeems the key
  const redeemResult = await licenseService.redeemLicense({
    key: license.license_key,
    userId: regUser.user.id
  });

  assert.ok(redeemResult.success);
  assert.equal(redeemResult.plan_id, 'pro');
  assert.equal(redeemResult.duration_days, 90);

  // Verify updated subscription status
  status = licenseService.getSubscriptionStatus(regUser.user.id);
  assert.equal(status.status, 'active');
  assert.ok(status.isPremium);
  assert.ok(status.canChat);
  assert.ok(status.daysRemaining >= 90);

  // Attempting to redeem the same key again MUST fail
  await assert.rejects(
    async () => {
      await licenseService.redeemLicense({
        key: license.license_key,
        userId: regUser.user.id
      });
    },
    { code: 'LICENSE_ALREADY_USED' }
  );
});

test('Licensing: Revoked license cannot be redeemed', async () => {
  const regUser = await userService.registerUser({
    displayName: 'RevokeTester',
    password: 'PasswordTest123!'
  });

  const license = licenseService.createLicense({
    planId: 'starter',
    adminId: 'ADM-MASTER-01'
  });

  // Admin revokes the key
  licenseService.revokeLicense(license.id, 'ADM-MASTER-01');

  // Redemption must be rejected
  await assert.rejects(
    async () => {
      await licenseService.redeemLicense({
        key: license.license_key,
        userId: regUser.user.id
      });
    },
    { code: 'LICENSE_REVOKED' }
  );
});

test('Licensing: Expired trial without premium blocks chat access', async () => {
  const db = getDb();
  const regUser = await userService.registerUser({
    displayName: 'ExpiredTester',
    password: 'PasswordTest123!'
  });

  // Simulate server-side trial expiration (e.g. 8 days ago)
  const eightDaysAgo = Date.now() - (8 * 24 * 60 * 60 * 1000);
  db.prepare(`
    UPDATE users 
    SET trial_started_at = ?, trial_expires_at = ?, subscription_status = 'expired'
    WHERE id = ?
  `).run(eightDaysAgo - 1000, eightDaysAgo, regUser.user.id);

  const status = licenseService.getSubscriptionStatus(regUser.user.id);
  assert.equal(status.status, 'expired');
  assert.equal(status.canChat, false);
  assert.equal(status.notice, 'Contact the Owner to Get Your License');
});
