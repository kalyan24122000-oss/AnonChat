const express = require('express');
const router = express.Router();
const adminService = require('../services/adminService');
const licenseService = require('../services/licenseService');
const userService = require('../services/userService');
const { authenticateToken, requireAdmin } = require('../middleware/auth');
const { authLimiter } = require('../middleware/rateLimiter');

// Admin Login (Requires ID, Password, and verifies role === 'admin' strictly on the backend)
router.post('/login', authLimiter, async (req, res, next) => {
  try {
    const { id, password } = req.body;
    const clientIp = req.ip || req.connection.remoteAddress;
    const result = await userService.loginUser({ id, password, clientIp });

    // Strict role check: role MUST be 'admin' in database
    if (result.user.role !== 'admin') {
      return res.status(403).json({
        error: 'FORBIDDEN',
        message: 'Administrative privileges required. Role violation.'
      });
    }

    res.json(result);
  } catch (err) {
    next(err);
  }
});

// All following routes require valid authentication AND 'admin' role in DB
router.use(authenticateToken, requireAdmin);

// Dashboard overview statistics
router.get('/stats', (req, res, next) => {
  try {
    const stats = adminService.getDashboardStats();
    res.json(stats);
  } catch (err) {
    next(err);
  }
});

// List users with search, status, and role filters
router.get('/users', (req, res, next) => {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = parseInt(req.query.limit, 10) || 20;
    const { search, role, status, subStatus } = req.query;

    const data = adminService.listUsers({ page, limit, search, role, status, subStatus });
    res.json(data);
  } catch (err) {
    next(err);
  }
});

// Suspend a user
router.post('/users/:id/suspend', (req, res, next) => {
  try {
    const { reason } = req.body;
    const result = adminService.suspendUser(req.params.id, req.user.id, reason);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Reactivate a user
router.post('/users/:id/reactivate', (req, res, next) => {
  try {
    const result = adminService.reactivateUser(req.params.id, req.user.id);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// Secure password reset: generates a new temporary password (never reveals existing password)
router.post('/users/:id/reset-password', async (req, res, next) => {
  try {
    const result = await adminService.resetUserPassword(req.params.id, req.user.id);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// List licenses
router.get('/licenses', (req, res, next) => {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = parseInt(req.query.limit, 10) || 50;
    const { status, planId } = req.query;
    const data = adminService.listLicenses({ page, limit, status, planId });
    res.json(data);
  } catch (err) {
    next(err);
  }
});

// Generate new license key
router.post('/licenses/generate', (req, res, next) => {
  try {
    const { planId, notes } = req.body;
    if (!planId) {
      return res.status(400).json({ error: 'MISSING_PLAN', message: 'planId is required' });
    }
    const license = licenseService.createLicense({
      planId,
      adminId: req.user.id,
      notes
    });
    res.status(201).json({ license });
  } catch (err) {
    next(err);
  }
});

// Revoke license key
router.post('/licenses/:id/revoke', (req, res, next) => {
  try {
    const result = licenseService.revokeLicense(req.params.id, req.user.id);
    res.json(result);
  } catch (err) {
    next(err);
  }
});

// List the 4 configurable plans for admin editing
router.get('/plans', (req, res, next) => {
  try {
    const plans = licenseService.getPlans();
    res.json({ plans });
  } catch (err) {
    next(err);
  }
});

// Update plan configuration (name, duration, description, features)
router.put('/plans/:id', (req, res, next) => {
  try {
    const { name, duration_days, description, features, is_active } = req.body;
    const result = licenseService.updatePlan(
      req.params.id,
      { name, duration_days, description, features, is_active },
      req.user.id
    );
    res.json({ plan: result });
  } catch (err) {
    next(err);
  }
});

// Audit and security logs review
router.get('/audit-logs', (req, res, next) => {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = parseInt(req.query.limit, 10) || 50;
    const { action } = req.query;
    const data = adminService.listAuditLogs({ page, limit, action });
    res.json(data);
  } catch (err) {
    next(err);
  }
});

// Authorized database inspector (table schemas and record counts, no plaintext messages)
router.get('/database-inspector', (req, res, next) => {
  try {
    const data = adminService.getDatabaseInspection();
    res.json(data);
  } catch (err) {
    next(err);
  }
});

// Create additional administrator account
router.post('/admins', async (req, res, next) => {
  try {
    const { id, password, displayName, badgeColor } = req.body;
    const result = await adminService.createAdminAccount({
      id,
      password,
      displayName,
      badgeColor,
      actorId: req.user.id
    });
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
