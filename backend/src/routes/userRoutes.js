const express = require('express');
const router = express.Router();
const userService = require('../services/userService');
const licenseService = require('../services/licenseService');
const { authenticateToken } = require('../middleware/auth');

// Get current user profile and subscription details
router.get('/me', authenticateToken, (req, res) => {
  const subStatus = licenseService.getSubscriptionStatus(req.user.id);
  res.json({
    user: req.user,
    subscription: subStatus
  });
});

// Update E2EE public key
router.put('/public-key', authenticateToken, (req, res, next) => {
  try {
    const { publicKey } = req.body;
    if (!publicKey) {
      return res.status(400).json({ error: 'INVALID_PUBLIC_KEY', message: 'Public key is required' });
    }
    const result = userService.updatePublicKey(req.user.id, publicKey);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Search user by User ID
router.get('/search', authenticateToken, (req, res, next) => {
  try {
    const query = req.query.id;
    if (!query) {
      return res.status(400).json({ error: 'MISSING_QUERY', message: 'Query parameter "id" is required' });
    }
    const results = userService.searchUserById(query, req.user.id);
    res.json({ users: results });
  } catch (err) {
    next(err);
  }
});

// Block a user
router.post('/block', authenticateToken, (req, res, next) => {
  try {
    const { blockedId } = req.body;
    if (!blockedId) {
      return res.status(400).json({ error: 'MISSING_ID', message: 'blockedId is required' });
    }
    const result = userService.blockUser(req.user.id, blockedId);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Unblock a user
router.post('/unblock', authenticateToken, (req, res, next) => {
  try {
    const { blockedId } = req.body;
    if (!blockedId) {
      return res.status(400).json({ error: 'MISSING_ID', message: 'blockedId is required' });
    }
    const result = userService.unblockUser(req.user.id, blockedId);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Get list of blocked users
router.get('/blocked', authenticateToken, (req, res, next) => {
  try {
    const blockedList = userService.getBlockedUsers(req.user.id);
    res.json({ blocked: blockedList });
  } catch (err) {
    next(err);
  }
});

// Report a user
router.post('/report', authenticateToken, (req, res, next) => {
  try {
    const { reportedId, reason } = req.body;
    if (!reportedId) {
      return res.status(400).json({ error: 'MISSING_ID', message: 'reportedId is required' });
    }
    const result = userService.reportUser(req.user.id, reportedId, reason);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
