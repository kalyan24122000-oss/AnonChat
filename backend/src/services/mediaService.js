const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { getDb } = require('../database/db');

const UPLOADS_DIR = path.join(__dirname, '../../uploads');

if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Save an uploaded media file (image or video) with 7-day expiration
 */
function saveMedia({ senderId, fileName, mimeType, base64Data }) {
  if (!base64Data || !fileName || !mimeType) {
    const error = new Error('fileName, mimeType, and base64Data are required');
    error.statusCode = 400;
    throw error;
  }

  // Basic security sanitize fileName
  const sanitizedName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_');
  const mediaId = `med_${Date.now()}_${crypto.randomBytes(8).toString('hex')}`;
  const diskFileName = `${mediaId}_${sanitizedName}`;
  const diskPath = path.join(UPLOADS_DIR, diskFileName);

  // Strip possible base64 data URL prefix (e.g. data:image/png;base64,...)
  const pureBase64 = base64Data.replace(/^data:[^;]+;base64,/, '');
  const buffer = Buffer.from(pureBase64, 'base64');
  const fileSize = buffer.length;

  fs.writeFileSync(diskPath, buffer);

  const now = Date.now();
  const expiresAt = now + SEVEN_DAYS_MS;

  const db = getDb();
  db.prepare(`
    INSERT INTO media_files (id, sender_id, file_name, mime_type, file_size, file_path, created_at, expires_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(mediaId, senderId, sanitizedName, mimeType, fileSize, diskPath, now, expiresAt);

  return {
    id: mediaId,
    mediaId,
    fileName: sanitizedName,
    mimeType,
    fileSize,
    url: `/api/chat/media/${mediaId}`,
    downloadUrl: `/api/chat/media/${mediaId}/download`,
    createdAt: now,
    expiresAt
  };
}

/**
 * Retrieve media file details and verify 7-day expiration
 */
function getMedia(mediaId) {
  const db = getDb();
  const record = db.prepare('SELECT * FROM media_files WHERE id = ?').get(mediaId);

  if (!record) {
    const error = new Error('Media not found');
    error.statusCode = 404;
    throw error;
  }

  const now = Date.now();
  if (record.expires_at <= now) {
    // Expired! Delete file and record
    try {
      if (fs.existsSync(record.file_path)) {
        fs.unlinkSync(record.file_path);
      }
    } catch (e) {
      console.error(`[Media] Failed to delete expired file ${record.file_path}:`, e.message);
    }
    db.prepare('DELETE FROM media_files WHERE id = ?').run(mediaId);

    const error = new Error('This media file has expired (7-day retention limit) and was permanently deleted from the database.');
    error.statusCode = 410;
    error.code = 'MEDIA_EXPIRED';
    throw error;
  }

  if (!fs.existsSync(record.file_path)) {
    const error = new Error('Media file missing from storage');
    error.statusCode = 404;
    throw error;
  }

  return record;
}

/**
 * Cleanup all media files expired after 7 days
 */
function cleanupExpiredMedia() {
  try {
    const db = getDb();
    const now = Date.now();
    const expiredList = db.prepare('SELECT id, file_path FROM media_files WHERE expires_at <= ?').all(now);

    for (const item of expiredList) {
      try {
        if (fs.existsSync(item.file_path)) {
          fs.unlinkSync(item.file_path);
        }
      } catch (err) {
        console.error(`[Media Cleanup] Error unlinking ${item.file_path}:`, err.message);
      }
    }

    if (expiredList.length > 0) {
      db.prepare('DELETE FROM media_files WHERE expires_at <= ?').run(now);
      console.log(`[Media Cleanup] Purged ${expiredList.length} expired 7-day media files.`);
    }
  } catch (err) {
    console.error('[Media Cleanup] Failed to run expired media cleanup:', err.message);
  }
}

// Auto-run cleanup hourly (unref so tests and processes can exit cleanly)
const cleanupTimer = setInterval(cleanupExpiredMedia, 60 * 60 * 1000);
if (cleanupTimer.unref) cleanupTimer.unref();

module.exports = {
  saveMedia,
  getMedia,
  cleanupExpiredMedia,
  UPLOADS_DIR
};
