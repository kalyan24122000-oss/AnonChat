const express = require('express');
const router = express.Router();
const userService = require('../services/userService');
const { authenticateToken } = require('../middleware/auth');
const { authLimiter } = require('../middleware/rateLimiter');

// User Registration
router.post('/register', authLimiter, async (req, res, next) => {
  try {
    const { displayName, password, publicKey } = req.body;
    const result = await userService.registerUser({
      displayName,
      password,
      publicKey
    });
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});

// User Login
router.post('/login', authLimiter, async (req, res, next) => {
  try {
    const { id, password } = req.body;
    const clientIp = req.ip || req.connection.remoteAddress;
    const result = await userService.loginUser({ id, password, clientIp });
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Account Recovery using Recovery Code
router.post('/recover', authLimiter, async (req, res, next) => {
  try {
    const { id, recoveryCode, newPassword } = req.body;
    const result = await userService.recoverAccount({ id, recoveryCode, newPassword });
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Delete Account
router.post('/delete-account', authenticateToken, async (req, res, next) => {
  try {
    const result = await userService.deleteAccount(req.user.id);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
