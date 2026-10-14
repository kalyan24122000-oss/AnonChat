const crypto = require('node:crypto');

// Character set excluding easily confused characters (O, 0, I, 1, L)
const CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

/**
 * Generate a cryptographically secure random string of specified length
 * @param {number} length 
 * @returns {string}
 */
function generateRandomString(length) {
  const bytes = crypto.randomBytes(length);
  let result = '';
  for (let i = 0; i < length; i++) {
    result += CHARS[bytes[i] % CHARS.length];
  }
  return result;
}

/**
 * Generate a unique User ID starting with USR
 * Format: USR-XXXX-XXXX
 * @returns {string}
 */
function generateUserId() {
  const part1 = generateRandomString(4);
  const part2 = generateRandomString(4);
  return `USR-${part1}-${part2}`;
}

/**
 * Generate an Admin ID starting with ADM
 * Format: ADM-XXXX-XXXX
 * @returns {string}
 */
function generateAdminId() {
  const part1 = generateRandomString(4);
  const part2 = generateRandomString(4);
  return `ADM-${part1}-${part2}`;
}

/**
 * Generate a License Key
 * Format: ANON-XXXX-XXXX-XXXX
 * @returns {string}
 */
function generateLicenseKey() {
  const p1 = generateRandomString(4);
  const p2 = generateRandomString(4);
  const p3 = generateRandomString(4);
  return `ANON-${p1}-${p2}-${p3}`;
}

/**
 * Generate an Account Recovery Code
 * Format: RC-XXXX-XXXX-XXXX
 * @returns {string}
 */
function generateRecoveryCode() {
  const p1 = generateRandomString(4);
  const p2 = generateRandomString(4);
  const p3 = generateRandomString(4);
  return `RC-${p1}-${p2}-${p3}`;
}

module.exports = {
  generateUserId,
  generateAdminId,
  generateLicenseKey,
  generateRecoveryCode,
  generateRandomString
};
