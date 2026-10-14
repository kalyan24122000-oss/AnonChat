const express = require('express');
const router = express.Router();
const chatService = require('../services/chatService');
const { authenticateToken, requireActiveSubscription } = require('../middleware/auth');

// Get all conversations for current user
router.get('/conversations', authenticateToken, (req, res, next) => {
  try {
    const list = chatService.getUserConversations(req.user.id);
    res.json({ conversations: list });
  } catch (err) {
    next(err);
  }
});

// Create or get direct conversation with recipient
router.post('/conversations', authenticateToken, (req, res, next) => {
  try {
    const recipientId = req.body.recipientId || req.body.recipient_id;
    if (!recipientId) {
      return res.status(400).json({ error: 'MISSING_RECIPIENT', message: 'recipientId is required' });
    }
    const conv = chatService.getOrCreateConversation(req.user.id, recipientId);
    res.json(conv);
  } catch (err) {
    next(err);
  }
});

// Get messages for a specific conversation
router.get('/conversations/:id/messages', authenticateToken, (req, res, next) => {
  try {
    const limit = parseInt(req.query.limit, 10) || 50;
    const beforeTimestamp = req.query.before ? parseInt(req.query.before, 10) : null;
    const messages = chatService.getConversationMessages(req.params.id, req.user.id, limit, beforeTimestamp);
    res.json({ messages });
  } catch (err) {
    next(err);
  }
});

// Send an E2EE encrypted message via REST (enforces active trial / premium subscription!)
router.post('/conversations/:id/messages', authenticateToken, requireActiveSubscription, (req, res, next) => {
  try {
    const conversationId = req.params.id;
    const recipientId = req.body.recipientId || req.body.recipient_id;
    const encryptedContent = req.body.encryptedContent || req.body.encrypted_content;
    const iv = req.body.iv;
    const authTag = req.body.authTag || req.body.auth_tag;
    const senderEphemeralKey = req.body.senderEphemeralKey || req.body.sender_ephemeral_key;

    if (!recipientId || !encryptedContent || !iv || !authTag) {
      return res.status(400).json({
        error: 'INVALID_ENCRYPTION_PAYLOAD',
        message: 'recipientId, encryptedContent, iv, and authTag are required'
      });
    }

    const message = chatService.saveEncryptedMessage({
      conversationId,
      senderId: req.user.id,
      recipientId,
      encryptedContent,
      iv,
      authTag,
      senderEphemeralKey
    });

    // Notify via Socket.IO if instance attached to app
    const io = req.app.get('io');
    if (io) {
      io.to(`user:${recipientId}`).emit('new_message', message);
      io.to(`conv:${conversationId}`).emit('conversation_message', message);
    }

    res.status(201).json({ message });
  } catch (err) {
    next(err);
  }
});

// Mark messages in conversation as read
router.post('/conversations/:id/read', authenticateToken, (req, res, next) => {
  try {
    chatService.markMessagesRead(req.user.id, req.params.id);
    res.json({ success: true });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
