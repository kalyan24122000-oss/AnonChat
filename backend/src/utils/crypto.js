const crypto = require('node:crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const config = require('../config');

const BCRYPT_SALT_ROUNDS = 12;

/**
 * Hash a plain password using bcrypt (work factor 12)
 * @param {string} password 
 * @returns {Promise<string>}
 */
async function hashPassword(password) {
  if (!password || typeof password !== 'string' || password.length < 8) {
    throw new Error('Password must be at least 8 characters long');
  }
  return await bcrypt.hash(password, BCRYPT_SALT_ROUNDS);
}

/**
 * Verify a plain password against a bcrypt hash
 * @param {string} password 
 * @param {string} hash 
 * @returns {Promise<boolean>}
 */
async function verifyPassword(password, hash) {
  if (!password || !hash) return false;
  return await bcrypt.compare(password, hash);
}

/**
 * Hash a recovery code using SHA-256 with salt
 * Normalized without hyphens and case-insensitive
 * @param {string} code 
 * @returns {string}
 */
function hashRecoveryCode(code) {
  const normalized = code.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return crypto.createHash('sha256').update(`recovery_salt_${normalized}`).digest('hex');
}

/**
 * Verify recovery code against hash
 * @param {string} code 
 * @param {string} hash 
 * @returns {boolean}
 */
function verifyRecoveryCode(code, hash) {
  if (!code || !hash) return false;
  const computed = hashRecoveryCode(code);
  return crypto.timingSafeEqual(Buffer.from(computed), Buffer.from(hash));
}

/**
 * Sign a JWT token for a user
 * @param {object} payload 
 * @returns {string}
 */
function signToken(payload) {
  return jwt.sign(payload, config.jwtSecret, {
    expiresIn: config.jwtExpiresIn,
    algorithm: 'HS256'
  });
}

/**
 * Verify a JWT token
 * @param {string} token 
 * @returns {object}
 */
function verifyToken(token) {
  return jwt.verify(token, config.jwtSecret);
}

module.exports = {
  hashPassword,
  verifyPassword,
  hashRecoveryCode,
  verifyRecoveryCode,
  signToken,
  verifyToken
};
