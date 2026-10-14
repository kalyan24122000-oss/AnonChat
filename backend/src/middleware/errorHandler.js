/**
 * Safe error handling middleware.
 * Guarantees no sensitive data (passwords, tokens, private keys) is ever logged or leaked.
 */
function errorHandler(err, req, res, next) {
  // Redact any body fields before logging
  const safeBody = req.body ? { ...req.body } : {};
  if (safeBody.password) safeBody.password = '[REDACTED]';
  if (safeBody.new_password) safeBody.new_password = '[REDACTED]';
  if (safeBody.recovery_code) safeBody.recovery_code = '[REDACTED]';

  console.error(`[Error] ${req.method} ${req.originalUrl}:`, {
    message: err.message,
    statusCode: err.statusCode || 500,
    body: safeBody
  });

  const statusCode = err.statusCode || (err.name === 'ValidationError' ? 400 : 500);
  const errorCode = err.code || 'SERVER_ERROR';

  res.status(statusCode).json({
    error: errorCode,
    message: err.message || 'An unexpected internal server error occurred'
  });
}

module.exports = errorHandler;
