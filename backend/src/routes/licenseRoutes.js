const express = require('express');
const router = express.Router();
const licenseService = require('../services/licenseService');
const { authenticateToken } = require('../middleware/auth');
const { licenseLimiter } = require('../middleware/rateLimiter');

// Get current subscription and trial status
router.get('/status', authenticateToken, (req, res, next) => {
  try {
    const status = licenseService.getSubscriptionStatus(req.user.id);
    res.json(status);
  } catch (err) {
    next(err);
  }
});

// Get available public plans
router.get('/plans', (req, res, next) => {
  try {
    const plans = licenseService.getPlans();
    res.json({ plans });
  } catch (err) {
    next(err);
  }
});

// Redeem a license key (Strictly rate-limited against brute force)
router.post('/redeem', authenticateToken, licenseLimiter, (req, res, next) => {
  try {
    const { key } = req.body;
    const result = licenseService.redeemLicense({ key, userId: req.user.id });
    res.json(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
