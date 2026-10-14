const rateLimit = require('express-rate-limit');
const config = require('../config');

// Standard API limiter
const apiLimiter = rateLimit({
  windowMs: config.rateLimitWindowMs,
  max: config.rateLimitMaxRequests,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: 'RATE_LIMIT_EXCEEDED',
    message: 'Too many requests from this IP, please try again later'
  }
});

// Strict limiter for sensitive auth actions (login, register, recovery)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 mins
  max: 20, // max 20 login attempts per 15 mins
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: 'AUTH_RATE_LIMIT_EXCEEDED',
    message: 'Too many authentication attempts, please wait 15 minutes before trying again'
  }
});

// License redemption limiter to prevent key guessing
const licenseLimiter = rateLimit({
  windowMs: 10 * 60 * 1000, // 10 mins
  max: 10, // max 10 key redemption attempts
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: 'LICENSE_RATE_LIMIT_EXCEEDED',
    message: 'Too many license activation attempts, please try again in 10 minutes'
  }
});

module.exports = {
  apiLimiter,
  authLimiter,
  licenseLimiter
};
