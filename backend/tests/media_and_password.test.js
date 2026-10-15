const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { getDb } = require('../src/database/db');
const { runMigrations } = require('../src/database/migrations');
const { registerUser } = require('../src/services/userService');
const { listUsers } = require('../src/services/adminService');
const { saveMedia, getMedia, cleanupExpiredMedia } = require('../src/services/mediaService');

test('Admin Password View & 7-Day Media Expiration', async (t) => {
  // Ensure DB ready
  runMigrations();

  await t.test('Admin can view registered user plain password', async () => {
    const reg = await registerUser({
      displayName: 'SecretPassUser',
      password: 'MySecretPassword123!'
    });

    assert.ok(reg.user.id);

    const userList = listUsers({ search: reg.user.id });
    assert.ok(userList.users.length > 0);
    const foundUser = userList.users.find(u => u.id === reg.user.id);
    assert.ok(foundUser);
    assert.equal(foundUser.password, 'MySecretPassword123!');
  });

  await t.test('Media upload and auto-deletion on expiration', async () => {
    const reg = await registerUser({
      displayName: 'MediaSenderUser',
      password: 'MediaSenderPassword123!'
    });
    const dummyBase64 = Buffer.from('fake image content for testing').toString('base64');
    const media = saveMedia({
      senderId: reg.user.id,
      fileName: 'test_photo.png',
      mimeType: 'image/png',
      base64Data: dummyBase64
    });

    assert.ok(media.id);
    assert.equal(media.fileName, 'test_photo.png');
    assert.ok(media.expiresAt > Date.now());

    // Valid retrieval before expiration
    const retrieved = getMedia(media.id);
    assert.equal(retrieved.id, media.id);
    assert.ok(fs.existsSync(retrieved.file_path));

    // Force expiration in DB
    const db = getDb();
    db.prepare('UPDATE media_files SET expires_at = ? WHERE id = ?').run(Date.now() - 1000, media.id);

    // Fetching expired media throws 410 MEDIA_EXPIRED and removes file
    assert.throws(() => {
      getMedia(media.id);
    }, (err) => {
      return err.statusCode === 410 && err.code === 'MEDIA_EXPIRED';
    });

    // Cleanup job handles remaining expired files safely
    cleanupExpiredMedia();
  });
});
