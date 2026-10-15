const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');

const testDbPath = path.resolve(__dirname, '../data/test_suspension.db');
if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
process.env.DB_PATH = testDbPath;
process.env.JWT_SECRET = 'test_secret_for_anonchat_units_2026';

const { getDb, closeDb } = require('../src/database/db');
const { runMigrations } = require('../src/database/migrations');
const { runSeeds } = require('../src/database/seeds');
const { createAdminAccount } = require('../src/services/adminService');
const userService = require('../src/services/userService');
const app = require('../src/app');

let server;
let baseUrl;

test.before(async () => {
  runMigrations();
  await runSeeds();
  server = http.createServer(app);
  await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      baseUrl = `http://127.0.0.1:${port}`;
      resolve();
    });
  });
});

test.after(async () => {
  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }
  closeDb();
  if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
});

test('Multi-Admin and Auto-Suspension Feature Suite', async (t) => {
  const db = getDb();

  await t.test('1. Two Admins Exist in Database (ADM-MASTER-01 and ADM-MASTER-02)', async () => {
    const admin1 = db.prepare("SELECT * FROM users WHERE id = 'ADM-MASTER-01'").get();
    const admin2 = db.prepare("SELECT * FROM users WHERE id = 'ADM-MASTER-02'").get();

    assert.ok(admin1, 'ADM-MASTER-01 should exist');
    assert.equal(admin1.role, 'admin');
    assert.equal(admin1.status, 'active');

    assert.ok(admin2, 'ADM-MASTER-02 should exist');
    assert.equal(admin2.role, 'admin');
    assert.equal(admin2.status, 'active');
  });

  await t.test('2. Create Third Admin Dynamically via adminService', async () => {
    const newAdmin = await createAdminAccount({
      id: 'ADM-CUSTOM-03',
      password: 'CustomAdminPassword123!',
      displayName: 'Third Security Admin',
      badgeColor: '#10B981'
    });

    assert.equal(newAdmin.success, true);
    assert.equal(newAdmin.admin.id, 'ADM-CUSTOM-03');
    assert.equal(newAdmin.admin.role, 'admin');

    const inDb = db.prepare("SELECT * FROM users WHERE id = 'ADM-CUSTOM-03'").get();
    assert.ok(inDb);
    assert.equal(inDb.role, 'admin');
    assert.equal(inDb.status, 'active');
  });

  await t.test('3. User Auto-Suspension on "please suspend my account admin" Message', async () => {
    // Register normal user
    const regRes = await userService.registerUser({
      password: 'UserPass2026!#',
      displayName: 'Self-Suspender'
    });
    const userToken = regRes.token;
    const userId = regRes.user.id;

    // Verify user is active
    let userRow = db.prepare("SELECT status FROM users WHERE id = ?").get(userId);
    assert.equal(userRow.status, 'active');

    // Create conversation between user and ADM-MASTER-01
    const convResponse = await fetch(`${baseUrl}/api/chat/conversations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userToken}`
      },
      body: JSON.stringify({ recipientId: 'ADM-MASTER-01' })
    });
    assert.equal(convResponse.status, 200);
    const convData = await convResponse.json();
    const convId = convData.id;

    // Send the suspension trigger message
    const sendResponse = await fetch(`${baseUrl}/api/chat/conversations/${convId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userToken}`
      },
      body: JSON.stringify({
        recipientId: 'ADM-MASTER-01',
        encryptedContent: 'CIPHERTEXT_SIMULATION',
        iv: 'IV_BASE64',
        authTag: 'AUTHTAG_BASE64',
        plainCommand: 'Please suspend my account admin'
      })
    });

    // Must return 403 ACCOUNT_SUSPENDED
    assert.equal(sendResponse.status, 403);
    const sendData = await sendResponse.json();
    assert.equal(sendData.error, 'ACCOUNT_SUSPENDED');
    assert.equal(sendData.message, 'Account Suspended / Not Found');

    // Verify database record has been suspended immediately
    userRow = db.prepare("SELECT status FROM users WHERE id = ?").get(userId);
    assert.equal(userRow.status, 'suspended', 'User status should be suspended in DB');

    // Attempting login must fail with 403 "Account Suspended / Not Found"
    const loginResponse = await fetch(`${baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: userId,
        password: 'UserPass2026!#'
      })
    });
    assert.equal(loginResponse.status, 403);
    const loginData = await loginResponse.json();
    assert.equal(loginData.message, 'Account Suspended / Not Found');

    // Attempting authenticated API call with old token must return 403
    const apiResponse = await fetch(`${baseUrl}/api/users/me`, {
      headers: { 'Authorization': `Bearer ${userToken}` }
    });
    assert.equal(apiResponse.status, 403);
    const apiData = await apiResponse.json();
    assert.equal(apiData.message, 'Account Suspended / Not Found');
  });
});
