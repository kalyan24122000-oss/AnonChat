const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');

const testDbPath = path.resolve(__dirname, '../data/test_messaging.db');
if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
process.env.DB_PATH = testDbPath;
process.env.JWT_SECRET = 'test_secret_for_anonchat_units_2026';

const { getDb, closeDb } = require('../src/database/db');
const { runMigrations } = require('../src/database/migrations');
const { runSeeds } = require('../src/database/seeds');
const userService = require('../src/services/userService');
const chatService = require('../src/services/chatService');

test.before(async () => {
  runMigrations();
  await runSeeds();
});

test.after(() => {
  closeDb();
  if (fs.existsSync(testDbPath)) fs.unlinkSync(testDbPath);
});

test('Messaging: Create conversation and relay E2EE payload', async () => {
  const alice = await userService.registerUser({ displayName: 'AliceChat', password: 'Password123!' });
  const bob = await userService.registerUser({ displayName: 'BobChat', password: 'Password123!' });

  // Alice starts conversation with Bob
  const conv = chatService.getOrCreateConversation(alice.user.id, bob.user.id);
  assert.ok(conv.id);
  assert.equal(conv.recipient.id, bob.user.id);

  // Alice sends encrypted message
  const msg = chatService.saveEncryptedMessage({
    conversationId: conv.id,
    senderId: alice.user.id,
    recipientId: bob.user.id,
    encryptedContent: 'ENCRYPTED_BASE64_CIPHERTEXT_ABC123',
    iv: 'RANDOM_12_BYTE_IV_HEX',
    authTag: 'AUTH_TAG_16_BYTE_HEX'
  });

  assert.ok(msg.id);
  assert.equal(msg.status, 'sent');
  assert.equal(msg.encryptedContent, 'ENCRYPTED_BASE64_CIPHERTEXT_ABC123');

  // Verify server database does NOT store any plaintext message column
  const db = getDb();
  const dbMsg = db.prepare('SELECT * FROM messages WHERE id = ?').get(msg.id);
  assert.equal(dbMsg.encrypted_content, 'ENCRYPTED_BASE64_CIPHERTEXT_ABC123');
  assert.equal(dbMsg.plaintext, undefined);

  // Mark message delivered
  chatService.markMessagesDelivered(bob.user.id, conv.id);
  const deliveredMsg = db.prepare('SELECT status, delivered_at FROM messages WHERE id = ?').get(msg.id);
  assert.equal(deliveredMsg.status, 'delivered');
  assert.ok(deliveredMsg.delivered_at);

  // Mark message read
  chatService.markMessagesRead(bob.user.id, conv.id);
  const readMsg = db.prepare('SELECT status, read_at FROM messages WHERE id = ?').get(msg.id);
  assert.equal(readMsg.status, 'read');
  assert.ok(readMsg.read_at);
});

test('Messaging: Blocked user cannot message blocker', async () => {
  const alice = await userService.registerUser({ displayName: 'AliceBlocker', password: 'Password123!' });
  const bob = await userService.registerUser({ displayName: 'BobBlocked', password: 'Password123!' });

  const conv = chatService.getOrCreateConversation(alice.user.id, bob.user.id);

  // Alice blocks Bob
  userService.blockUser(alice.user.id, bob.user.id);

  // Bob attempts to send message to Alice -> MUST reject
  await assert.rejects(
    async () => {
      chatService.saveEncryptedMessage({
        conversationId: conv.id,
        senderId: bob.user.id,
        recipientId: alice.user.id,
        encryptedContent: 'SPAM_CONTENT',
        iv: 'IV123',
        authTag: 'TAG123'
      });
    },
    { code: 'BLOCKED_BY_USER' }
  );

  // Alice unblocks Bob
  userService.unblockUser(alice.user.id, bob.user.id);

  // Bob can now send message
  const allowedMsg = chatService.saveEncryptedMessage({
    conversationId: conv.id,
    senderId: bob.user.id,
    recipientId: alice.user.id,
    encryptedContent: 'UNBLOCKED_MSG',
    iv: 'IV123',
    authTag: 'TAG123'
  });
  assert.ok(allowedMsg.id);
});

test('Messaging: Delete single message, clear chat, and delete conversation', async () => {
  const user1 = await userService.registerUser({ displayName: 'UserClear1', password: 'Password123!' });
  const user2 = await userService.registerUser({ displayName: 'UserClear2', password: 'Password123!' });

  const conv = chatService.getOrCreateConversation(user1.user.id, user2.user.id);

  // Send two messages
  const msg1 = chatService.saveEncryptedMessage({
    conversationId: conv.id,
    senderId: user1.user.id,
    recipientId: user2.user.id,
    encryptedContent: 'CIPHERTEXT_1',
    iv: 'IV1',
    authTag: 'TAG1'
  });

  const msg2 = chatService.saveEncryptedMessage({
    conversationId: conv.id,
    senderId: user2.user.id,
    recipientId: user1.user.id,
    encryptedContent: 'CIPHERTEXT_2',
    iv: 'IV2',
    authTag: 'TAG2'
  });

  let msgs = chatService.getConversationMessages(conv.id, user1.user.id);
  assert.equal(msgs.length, 2);

  // 1. Delete single message msg1
  const delMsgResult = chatService.deleteSingleMessage(msg1.id, user1.user.id);
  assert.equal(delMsgResult.success, true);
  msgs = chatService.getConversationMessages(conv.id, user1.user.id);
  assert.equal(msgs.length, 1);
  assert.equal(msgs[0].id, msg2.id);

  // 2. Clear conversation messages
  const clearResult = chatService.clearConversationMessages(conv.id, user1.user.id);
  assert.equal(clearResult.success, true);
  msgs = chatService.getConversationMessages(conv.id, user1.user.id);
  assert.equal(msgs.length, 0);

  // 3. Delete entire conversation
  const delConvResult = chatService.deleteConversation(conv.id, user1.user.id);
  assert.equal(delConvResult.success, true);
  const userConvs = chatService.getUserConversations(user1.user.id);
  assert.ok(!userConvs.some(c => c.id === conv.id));
});

