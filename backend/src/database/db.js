const fs = require('node:fs');
const path = require('node:path');
// Dual-driver SQLite adapter: prefer built-in node:sqlite (Node >= 22.5.0) for zero native C++
// ABI compilation issues and rock-solid stability, with fallback to better-sqlite3 on older runtimes.
let SqliteDriver = null;
let driverType = null;

try {
  const nodeSqlite = require('node:sqlite');
  if (nodeSqlite && nodeSqlite.DatabaseSync) {
    SqliteDriver = nodeSqlite.DatabaseSync;
    driverType = 'node:sqlite';
  }
} catch (e1) {
  // Not Node >= 22.5.0, fallback below
}

if (!SqliteDriver) {
  try {
    SqliteDriver = require('better-sqlite3');
    driverType = 'better-sqlite3';
  } catch (e2) {
    throw new Error(
      'No SQLite driver found. Please ensure Node >= 22.5.0 or that better-sqlite3 is installed.'
    );
  }
}

const config = require('../config');

let dbInstance = null;

/**
 * Get or initialize SQLite database connection
 * @param {string} [customPath]
 * @returns {object}
 */
function getDb(customPath = null) {
  if (dbInstance && !customPath) {
    return dbInstance;
  }

  const dbFilePath = customPath || config.dbPath;
  if (dbFilePath !== ':memory:') {
    const dir = path.dirname(dbFilePath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  }

  const db = new SqliteDriver(dbFilePath);
  db.exec('PRAGMA foreign_keys = ON;');
  db.exec('PRAGMA journal_mode = WAL;');

  if (!customPath) {
    dbInstance = db;
  }
  return db;
}

/**
 * Run a callback within a database transaction
 * @param {Function} callback 
 * @param {DatabaseSync} [db]
 * @returns {*}
 */
function runTransaction(callback, db = null) {
  const conn = db || getDb();
  conn.exec('BEGIN TRANSACTION;');
  try {
    const result = callback(conn);
    conn.exec('COMMIT;');
    return result;
  } catch (err) {
    conn.exec('ROLLBACK;');
    throw err;
  }
}

/**
 * Close database connection
 */
function closeDb() {
  if (dbInstance) {
    dbInstance.close();
    dbInstance = null;
  }
}

module.exports = {
  getDb,
  runTransaction,
  closeDb,
  driverType
};
