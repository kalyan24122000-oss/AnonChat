const { verifyToken } = require('../utils/crypto');
const { getDb } = require('../database/db');
const chatService = require('../services/chatService');
const { userTrialExpired, userPremiumExpired } = require('../middleware/auth');

/**
 * Setup Socket.IO real-time messaging server
 * @param {import('socket.io').Server} io
 */
function setupSocketIO(io) {
  // Authentication Middleware for Socket.IO
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token || socket.handshake.headers?.authorization?.replace('Bearer ', '');

    if (!token) {
      return next(new Error('Authentication error: Missing token'));
    }

    try {
      const payload = verifyToken(token);
      const db = getDb();
      const user = db.prepare(`
        SELECT id, display_name, role, status, trial_expires_at, subscription_status, premium_expires_at
        FROM users WHERE id = ?
      `).get(payload.id);

      if (!user || user.status !== 'active') {
        return next(new Error('Authentication error: Account inactive or suspended'));
      }

      socket.user = user;
      next();
    } catch (err) {
      return next(new Error('Authentication error: Invalid or expired token'));
    }
  });

  io.on('connection', (socket) => {
    const userId = socket.user.id;
    // Join personal user notification room
    socket.join(`user:${userId}`);

    // Mark pending messages as delivered
    try {
      chatService.markMessagesDelivered(userId);
    } catch (e) {
      // Ignore initial delivery mark error
    }

    // 1. Join Conversation Room
    socket.on('join_conversation', ({ conversationId }) => {
      if (!conversationId) return;
      socket.join(`conv:${conversationId}`);
    });

    // 2. Leave Conversation Room
    socket.on('leave_conversation', ({ conversationId }) => {
      if (!conversationId) return;
      socket.leave(`conv:${conversationId}`);
    });

    // 3. Send Encrypted Message
    socket.on('send_message', async (data, callback) => {
      try {
        const { conversationId, recipientId, encryptedContent, iv, authTag, senderEphemeralKey } = data;

        if (!conversationId || !recipientId || !encryptedContent || !iv || !authTag) {
          if (callback) callback({ error: 'INVALID_PAYLOAD', message: 'Missing required encryption fields' });
          return;
        }

        // Server-side Trial & Subscription enforcement
        if (socket.user.role !== 'admin') {
          const now = Date.now();
          const trialExpired = userTrialExpired(socket.user, now);
          const premiumExpired = userPremiumExpired(socket.user, now);

          if (trialExpired && premiumExpired) {
            if (callback) {
              callback({
                error: 'SUBSCRIPTION_EXPIRED',
                message: 'Contact the Owner to Get Your License'
              });
            }
            socket.emit('subscription_expired', { message: 'Contact the Owner to Get Your License' });
            return;
          }
        }

        // Save E2EE message
        const message = chatService.saveEncryptedMessage({
          conversationId,
          senderId: userId,
          recipientId,
          encryptedContent,
          iv,
          authTag,
          senderEphemeralKey
        });

        // Broadcast to recipient user room
        io.to(`user:${recipientId}`).emit('new_message', message);

        // Broadcast to conversation room (for active view)
        io.to(`conv:${conversationId}`).emit('conversation_message', message);

        if (callback) {
          callback({ success: true, message });
        }
      } catch (err) {
        if (callback) {
          callback({ error: err.code || 'MESSAGE_SEND_ERROR', message: err.message });
        }
      }
    });

    // 4. Message Delivered Ack
    socket.on('message_delivered', ({ messageId, conversationId, senderId }) => {
      try {
        const db = getDb();
        const now = Date.now();
        db.prepare("UPDATE messages SET status = 'delivered', delivered_at = ? WHERE id = ?").run(now, messageId);

        // Notify sender
        io.to(`user:${senderId}`).emit('message_status_update', {
          messageId,
          conversationId,
          status: 'delivered',
          deliveredAt: now
        });
      } catch (e) {
        // Safe ignore
      }
    });

    // 5. Message Read Ack
    socket.on('message_read', ({ conversationId, senderId }) => {
      try {
        chatService.markMessagesRead(userId, conversationId);
        const now = Date.now();

        // Notify sender that messages in this conversation have been read
        io.to(`user:${senderId}`).emit('message_status_update', {
          conversationId,
          status: 'read',
          readAt: now
        });
      } catch (e) {
        // Safe ignore
      }
    });

    // 6. Typing Indicators
    socket.on('typing_start', ({ conversationId, recipientId }) => {
      io.to(`user:${recipientId}`).emit('user_typing', {
        conversationId,
        userId,
        isTyping: true
      });
    });

    socket.on('typing_stop', ({ conversationId, recipientId }) => {
      io.to(`user:${recipientId}`).emit('user_typing', {
        conversationId,
        userId,
        isTyping: false
      });
    });

    socket.on('disconnect', () => {
      // Clean disconnect
    });
  });
}

module.exports = setupSocketIO;
