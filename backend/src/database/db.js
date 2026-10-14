const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const config = require('../config');

let dbInstance = null;

/**
 * Get or initialize SQLite database connection
 * @param {string} [customPath]
 * @returns {DatabaseSync}
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

  const db = new DatabaseSync(dbFilePath);
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
  closeDb
};
