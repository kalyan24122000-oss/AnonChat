const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');

// Use isolated in-memory or temp database for tests
const testDbPath = path.resolve(__dirname, '../data/test_auth.db');
if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
process.env.DB_PATH = testDbPath;
process.env.JWT_SECRET = 'test_secret_for_anonchat_units_2026';

const { getDb, closeDb } = require('../src/database/db');
const { runMigrations } = require('../src/database/migrations');
const { runSeeds } = require('../src/database/seeds');
const userService = require('../src/services/userService');
const { generateUserId, generateAdminId, generateLicenseKey } = require('../src/utils/idGenerator');

test.before(async () => {
  runMigrations();
  await runSeeds();
});

test.after(() => {
  closeDb();
  if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
});

test('ID Generator: generates proper prefixes and format', () => {
  const userId = generateUserId();
  const adminId = generateAdminId();
  const licKey = generateLicenseKey();

  assert.match(userId, /^USR-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  assert.match(adminId, /^ADM-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  assert.match(licKey, /^ANON-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
});

test('User Service: register user with zero phone/email, creates USR ID, recovery code, and 7-day trial', async () => {
  const regResult = await userService.registerUser({
    displayName: 'GhostRunner',
    password: 'SuperSecretPassword123!',
    publicKey: '04a1b2c3d4e5f6'
  });

  assert.ok(regResult.user);
  assert.match(regResult.user.id, /^USR-/);
  assert.equal(regResult.user.role, 'user');
  assert.equal(regResult.user.status, 'active');
  assert.equal(regResult.user.subscription_status, 'trial');
  assert.ok(regResult.token);
  assert.match(regResult.recoveryCode, /^RC-/);

  // Check 7 day trial duration: approx 7 * 86400 * 1000 ms
  const diffDays = Math.round((regResult.user.trial_expires_at - regResult.user.trial_started_at) / (24 * 60 * 60 * 1000));
  assert.equal(diffDays, 7);

  // Check DB does not store plaintext password or plaintext recovery code
  const db = getDb();
  const dbUser = db.prepare('SELECT * FROM users WHERE id = ?').get(regResult.user.id);
  assert.notEqual(dbUser.password_hash, 'SuperSecretPassword123!');
  assert.notEqual(dbUser.recovery_code_hash, regResult.recoveryCode);
  assert.ok(dbUser.password_hash.startsWith('$2')); // bcrypt prefix
});

test('User Service: login with valid credentials', async () => {
  const regResult = await userService.registerUser({
    displayName: 'Alice',
    password: 'AliceSecurePassword999!'
  });

  const loginResult = await userService.loginUser({
    id: regResult.user.id,
    password: 'AliceSecurePassword999!'
  });

  assert.ok(loginResult.token);
  assert.equal(loginResult.user.id, regResult.user.id);
});

test('User Service: login fails with invalid password', async () => {
  const regResult = await userService.registerUser({
    displayName: 'Bob',
    password: 'BobSecurePassword123!'
  });

  await assert.rejects(
    async () => {
      await userService.loginUser({
        id: regResult.user.id,
        password: 'WrongPasswordXYZ'
      });
    },
    { code: 'INVALID_CREDENTIALS' }
  );
});

test('User Service: account recovery flow resets password and issues fresh recovery code', async () => {
  const regResult = await userService.registerUser({
    displayName: 'Charlie',
    password: 'CharlieOldPassword123!'
  });

  const recoveryResult = await userService.recoverAccount({
    id: regResult.user.id,
    recoveryCode: regResult.recoveryCode,
    newPassword: 'CharlieBrandNewPassword456!'
  });

  assert.ok(recoveryResult.success);
  assert.ok(recoveryResult.newRecoveryCode);
  assert.notEqual(recoveryResult.newRecoveryCode, regResult.recoveryCode);

  // Now login with new password should succeed
  const loginResult = await userService.loginUser({
    id: regResult.user.id,
    password: 'CharlieBrandNewPassword456!'
  });
  assert.equal(loginResult.user.id, regResult.user.id);

  // Login with old password must fail
  await assert.rejects(
    async () => {
      await userService.loginUser({
        id: regResult.user.id,
        password: 'CharlieOldPassword123!'
      });
    },
    { code: 'INVALID_CREDENTIALS' }
  );
});
