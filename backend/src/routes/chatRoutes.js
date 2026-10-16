const express = require('express');
const router = express.Router();
const chatService = require('../services/chatService');
const mediaService = require('../services/mediaService');
const { authenticateToken, requireActiveSubscription } = require('../middleware/auth');

// Upload media (image or video) with 7-day auto-expiry
router.post('/media/upload', authenticateToken, requireActiveSubscription, (req, res, next) => {
  try {
    const { fileName, mimeType, base64Data } = req.body;
    if (!fileName || !mimeType || !base64Data) {
      return res.status(400).json({ error: 'MISSING_DATA', message: 'fileName, mimeType, and base64Data are required' });
    }
    const media = mediaService.saveMedia({
      senderId: req.user.id,
      fileName,
      mimeType,
      base64Data
    });
    res.status(201).json({ media });
  } catch (err) {
    next(err);
  }
});

// Stream or view media
router.get('/media/:id', (req, res, next) => {
  try {
    const record = mediaService.getMedia(req.params.id);
    res.setHeader('Content-Type', record.mime_type);
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.sendFile(record.file_path);
  } catch (err) {
    next(err);
  }
});

// Direct user download endpoint for storage saving
router.get('/media/:id/download', (req, res, next) => {
  try {
    const record = mediaService.getMedia(req.params.id);
    res.download(record.file_path, record.file_name);
  } catch (err) {
    next(err);
  }
});

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

const userService = require('../services/userService');
const adminService = require('../services/adminService');

function isSuspensionTrigger(text, explicitFlag) {
  if (explicitFlag) return true;
  if (!text || typeof text !== 'string') return false;
  const clean = text.toLowerCase().replace(/[^a-z0-9\u0900-\u097F\s]/gi, ' ').replace(/\s+/g, ' ').trim();
  if (clean.includes('please suspend my account admin') ||
      clean.includes('suspend my account admin') ||
      clean.includes('please suspend my account') ||
      clean === 'suspend my account') {
    return true;
  }
  if (clean.includes('प्लीज सस्पेंड माय अकाउंट एडमिन') ||
      clean.includes('सस्पेंड माय अकाउंट एडमिन') ||
      clean.includes('प्लीज सस्पेंड माय अकाउंट') ||
      clean.includes('मेरा अकाउंट सस्पेंड कर दो एडमिन') ||
      clean.includes('मेरा अकाउंट सस्पेंड करो एडमिन')) {
    return true;
  }
  return false;
}

// Send an E2EE encrypted message via REST (enforces active trial / premium subscription!)
router.post('/conversations/:id/messages', authenticateToken, requireActiveSubscription, async (req, res, next) => {
  try {
    const conversationId = req.params.id;
    const recipientId = req.body.recipientId || req.body.recipient_id;
    const encryptedContent = req.body.encryptedContent || req.body.encrypted_content;
    const iv = req.body.iv;
    const authTag = req.body.authTag || req.body.auth_tag;
    const senderEphemeralKey = req.body.senderEphemeralKey || req.body.sender_ephemeral_key;
    const plainCommand = req.body.plainCommand || req.body.textSnippet || '';
    const isSuspendRequest = req.body.isSuspendRequest === true || isSuspensionTrigger(plainCommand);

    if (!recipientId || !encryptedContent || !iv || !authTag) {
      return res.status(400).json({
        error: 'INVALID_ENCRYPTION_PAYLOAD',
        message: 'recipientId, encryptedContent, iv, and authTag are required'
      });
    }

    const recipient = userService.getUserById(recipientId);
    const isAdminRecipient = (recipient && (recipient.role === 'admin' || recipient.id.startsWith('ADM-'))) ||
                             (recipientId && recipientId.startsWith('ADM-'));

    // Check special feature: if user messages admin requesting account suspension
    if (isAdminRecipient && isSuspendRequest) {
      const message = chatService.saveEncryptedMessage({
        conversationId,
        senderId: req.user.id,
        recipientId,
        encryptedContent,
        iv,
        authTag,
        senderEphemeralKey
      });

      // Suspend account immediately at the time the message arrived
      adminService.suspendUser(
        req.user.id,
        'system',
        `User auto-suspended upon message to admin: ${plainCommand || 'please suspend my account admin'}`
      );

      const io = req.app.get('io');
      if (io) {
        io.to(`user:${recipientId}`).emit('new_message', message);
        io.to(`conv:${conversationId}`).emit('conversation_message', message);
        io.to(`user:${req.user.id}`).emit('account_suspended', {
          message: 'Account Suspended / Not Found'
        });
        try {
          io.in(`user:${req.user.id}`).disconnectSockets(true);
        } catch (ignored) {}
      }

      return res.status(403).json({
        error: 'ACCOUNT_SUSPENDED',
        message: 'Account Suspended / Not Found'
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

// Clear all messages in a conversation
router.delete('/conversations/:id/messages', authenticateToken, (req, res, next) => {
  try {
    const result = chatService.clearConversationMessages(req.params.id, req.user.id);
    const io = req.app.get('io');
    if (io) {
      io.to(`conv:${req.params.id}`).emit('conversation_cleared', {
        conversationId: req.params.id,
        clearedBy: req.user.id
      });
      if (result.memberIds && Array.isArray(result.memberIds)) {
        result.memberIds.forEach((uid) => {
          io.to(`user:${uid}`).emit('conversation_cleared', {
            conversationId: req.params.id,
            clearedBy: req.user.id
          });
        });
      }
    }
    res.json({ success: true, message: 'Chat cleared successfully' });
  } catch (err) {
    next(err);
  }
});

// Delete entire conversation and its messages
router.delete('/conversations/:id', authenticateToken, (req, res, next) => {
  try {
    const result = chatService.deleteConversation(req.params.id, req.user.id);
    const io = req.app.get('io');
    if (io) {
      io.to(`conv:${req.params.id}`).emit('conversation_deleted', {
        conversationId: req.params.id,
        deletedBy: req.user.id
      });
      if (result.memberIds && Array.isArray(result.memberIds)) {
        result.memberIds.forEach((uid) => {
          io.to(`user:${uid}`).emit('conversation_deleted', {
            conversationId: req.params.id,
            deletedBy: req.user.id
          });
        });
      }
    }
    res.json({ success: true, message: 'Conversation deleted successfully' });
  } catch (err) {
    next(err);
  }
});

// Delete a single message
router.delete('/messages/:id', authenticateToken, (req, res, next) => {
  try {
    const result = chatService.deleteSingleMessage(req.params.id, req.user.id);
    const io = req.app.get('io');
    if (io) {
      if (result.conversationId) {
        io.to(`conv:${result.conversationId}`).emit('message_deleted', {
          messageId: req.params.id,
          conversationId: result.conversationId,
          deletedBy: req.user.id
        });
      }
      if (result.recipientId) {
        io.to(`user:${result.recipientId}`).emit('message_deleted', {
          messageId: req.params.id,
          conversationId: result.conversationId,
          deletedBy: req.user.id
        });
      }
      io.to(`user:${req.user.id}`).emit('message_deleted', {
        messageId: req.params.id,
        conversationId: result.conversationId,
        deletedBy: req.user.id
      });
    }
    res.json({ success: true, message: 'Message deleted successfully' });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
