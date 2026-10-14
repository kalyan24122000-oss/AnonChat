const crypto = require('node:crypto');
const { getDb, runTransaction } = require('../database/db');

/**
 * Get or create a direct conversation between two users
 */
function getOrCreateConversation(userAId, userBId) {
  if (userAId === userBId) {
    const error = new Error('Cannot start conversation with yourself');
    error.statusCode = 400;
    throw error;
  }

  const db = getDb();

  // Check if both users exist
  const userB = db.prepare("SELECT id, display_name, public_key, status FROM users WHERE id = ? AND status != 'deleted'").get(userBId);
  if (!userB) {
    const error = new Error('Recipient user does not exist');
    error.statusCode = 404;
    error.code = 'RECIPIENT_NOT_FOUND';
    throw error;
  }

  // Check if existing conversation exists between both users
  const existingConv = db.prepare(`
    SELECT cm1.conversation_id 
    FROM conversation_members cm1
    JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id
    WHERE cm1.user_id = ? AND cm2.user_id = ?
  `).get(userAId, userBId);

  if (existingConv) {
    return {
      id: existingConv.conversation_id,
      recipient: userB,
      isNew: false
    };
  }

  // Create new conversation
  return runTransaction((conn) => {
    const convId = `conv_${crypto.randomBytes(8).toString('hex')}`;
    const now = Date.now();

    conn.prepare('INSERT INTO conversations (id, type, created_at, updated_at) VALUES (?, ?, ?, ?)')
      .run(convId, 'direct', now, now);

    conn.prepare('INSERT INTO conversation_members (id, conversation_id, user_id, joined_at, last_read_at) VALUES (?, ?, ?, ?, ?)')
      .run(`cm_${convId}_${userAId}`, convId, userAId, now, now);

    conn.prepare('INSERT INTO conversation_members (id, conversation_id, user_id, joined_at, last_read_at) VALUES (?, ?, ?, ?, ?)')
      .run(`cm_${convId}_${userBId}`, convId, userBId, now, 0);

    return {
      id: convId,
      recipient: userB,
      isNew: true
    };
  });
}

/**
 * List all conversations for a user with unread counts and last message snippet
 */
function getUserConversations(userId) {
  const db = getDb();

  // Fetch all conversations user participates in
  const rows = db.prepare(`
    SELECT 
      c.id as conversation_id,
      c.updated_at,
      u.id as recipient_id,
      u.display_name as recipient_name,
      u.public_key as recipient_public_key,
      u.status as recipient_status,
      cm.last_read_at
    FROM conversations c
    JOIN conversation_members cm ON c.id = cm.conversation_id AND cm.user_id = ?
    JOIN conversation_members other_cm ON c.id = other_cm.conversation_id AND other_cm.user_id != ?
    JOIN users u ON other_cm.user_id = u.id
    ORDER BY c.updated_at DESC
  `).all(userId, userId);

  // For each conversation, fetch last message metadata and unread count
  const conversations = rows.map((conv) => {
    const lastMsg = db.prepare(`
      SELECT id, sender_id, recipient_id, status, created_at
      FROM messages
      WHERE conversation_id = ?
      ORDER BY created_at DESC
      LIMIT 1
    `).get(conv.conversation_id);

    const unreadCountRow = db.prepare(`
      SELECT COUNT(*) as count
      FROM messages
      WHERE conversation_id = ? AND recipient_id = ? AND status != 'read'
    `).get(conv.conversation_id, userId);

    return {
      id: conv.conversation_id,
      recipient: {
        id: conv.recipient_id,
        displayName: conv.recipient_name,
        publicKey: conv.recipient_public_key,
        status: conv.recipient_status
      },
      lastMessage: lastMsg ? {
        id: lastMsg.id,
        senderId: lastMsg.sender_id,
        status: lastMsg.status,
        timestamp: lastMsg.created_at
      } : null,
      unreadCount: unreadCountRow ? unreadCountRow.count : 0,
      updatedAt: conv.updated_at
    };
  });

  return conversations;
}

/**
 * Store an E2EE encrypted message
 * Server stores ONLY ciphertext, IV, and authTag. Plaintext is never seen by server.
 */
function saveEncryptedMessage({
  conversationId,
  senderId,
  recipientId,
  encryptedContent,
  iv,
  authTag,
  senderEphemeralKey = null
}) {
  const db = getDb();

  // Verify sender is not blocked by recipient
  const isBlocked = db.prepare(`
    SELECT 1 FROM blocked_users WHERE blocker_id = ? AND blocked_id = ?
  `).get(recipientId, senderId);

  if (isBlocked) {
    const error = new Error('Message cannot be delivered: recipient has blocked communications');
    error.statusCode = 403;
    error.code = 'BLOCKED_BY_USER';
    throw error;
  }

  const messageId = `msg_${crypto.randomBytes(12).toString('hex')}`;
  const now = Date.now();

  runTransaction((conn) => {
    conn.prepare(`
      INSERT INTO messages (
        id, conversation_id, sender_id, recipient_id,
        encrypted_content, iv, auth_tag, sender_ephemeral_key,
        status, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'sent', ?)
    `).run(
      messageId,
      conversationId,
      senderId,
      recipientId,
      encryptedContent,
      iv,
      authTag,
      senderEphemeralKey || null,
      now
    );

    conn.prepare('UPDATE conversations SET updated_at = ? WHERE id = ?')
      .run(now, conversationId);
  });

  return {
    id: messageId,
    conversationId,
    senderId,
    recipientId,
    encryptedContent,
    iv,
    authTag,
    senderEphemeralKey,
    status: 'sent',
    createdAt: now
  };
}

/**
 * Get messages for a conversation
 */
function getConversationMessages(conversationId, userId, limit = 50, beforeTimestamp = null) {
  const db = getDb();

  // Verify membership
  const member = db.prepare('SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?')
    .get(conversationId, userId);

  if (!member) {
    const error = new Error('Access denied: not a participant of this conversation');
    error.statusCode = 403;
    throw error;
  }

  let query = `
    SELECT id, conversation_id, sender_id, recipient_id,
           encrypted_content, iv, auth_tag, sender_ephemeral_key,
           status, delivered_at, read_at, created_at
    FROM messages
    WHERE conversation_id = ?
  `;
  const params = [conversationId];

  if (beforeTimestamp) {
    query += ' AND created_at < ?';
    params.push(beforeTimestamp);
  }

  query += ' ORDER BY created_at ASC LIMIT ?';
  params.push(Math.min(limit, 100));

  const rows = db.prepare(query).all(...params);

  return rows.map((r) => ({
    id: r.id,
    conversationId: r.conversation_id,
    senderId: r.sender_id,
    recipientId: r.recipient_id,
    encryptedContent: r.encrypted_content,
    iv: r.iv,
    authTag: r.auth_tag,
    senderEphemeralKey: r.sender_ephemeral_key,
    status: r.status,
    deliveredAt: r.delivered_at,
    readAt: r.read_at,
    createdAt: r.created_at
  }));
}

/**
 * Mark messages as delivered for a recipient
 */
function markMessagesDelivered(userId, conversationId = null) {
  const db = getDb();
  const now = Date.now();

  if (conversationId) {
    db.prepare(`
      UPDATE messages 
      SET status = 'delivered', delivered_at = ?
      WHERE conversation_id = ? AND recipient_id = ? AND status = 'sent'
    `).run(now, conversationId, userId);
  } else {
    db.prepare(`
      UPDATE messages 
      SET status = 'delivered', delivered_at = ?
      WHERE recipient_id = ? AND status = 'sent'
    `).run(now, userId);
  }
}

/**
 * Mark messages as read by recipient
 */
function markMessagesRead(userId, conversationId) {
  const db = getDb();
  const now = Date.now();

  runTransaction((conn) => {
    conn.prepare(`
      UPDATE messages 
      SET status = 'read', read_at = ?
      WHERE conversation_id = ? AND recipient_id = ? AND status != 'read'
    `).run(now, conversationId, userId);

    conn.prepare(`
      UPDATE conversation_members
      SET last_read_at = ?
      WHERE conversation_id = ? AND user_id = ?
    `).run(now, conversationId, userId);
  });
}

module.exports = {
  getOrCreateConversation,
  getUserConversations,
  saveEncryptedMessage,
  getConversationMessages,
  markMessagesDelivered,
  markMessagesRead
};
