const fs = require('node:fs');
const path = require('node:path');

// Try loading .env from parent directories or local directory
const envPaths = [
  path.resolve(__dirname, '../../.env'),
  path.resolve(__dirname, '../.env'),
  path.resolve(process.cwd(), '.env'),
  path.resolve(process.cwd(), 'backend/.env')
];
for (const envPath of envPaths) {
  if (fs.existsSync(envPath)) {
    require('dotenv').config({ path: envPath });
    break;
  }
}

const config = {
  port: parseInt(process.env.PORT, 10) || 4000,
  host: process.env.HOST || '0.0.0.0',
  nodeEnv: process.env.NODE_ENV || 'development',
  jwtSecret: process.env.JWT_SECRET || 'anonchat_super_secure_jwt_secret_dev_key_change_in_prod_2026',
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '30d',
  dbPath: process.env.DB_PATH || path.resolve(__dirname, '../../data/anonchat.db'),
  databaseUrl: process.env.DATABASE_URL || 'postgresql://ananochat_user:xeNeLdlP7T8JaldH7WjovEvbdF16GLkv@dpg-db7q0tqd0e5s73eokg70-a.virginia-postgres.render.com/ananochat',
  trialDurationDays: parseInt(process.env.TRIAL_DURATION_DAYS, 10) || 7,
  adminInitialPassword: process.env.ADMIN_INITIAL_PASSWORD || 'AnonAdminSecure2026!#',
  adminInitialId: process.env.ADMIN_INITIAL_ID || 'ADM-MASTER-01',
  corsOrigins: process.env.CORS_ORIGINS ? process.env.CORS_ORIGINS.split(',') : ['*'],
  rateLimitWindowMs: 15 * 60 * 1000, // 15 minutes
  rateLimitMaxRequests: 300, // max 300 requests per 15 min per IP
  supabase: {
    url: process.env.SUPABASE_URL || '',
    publishableKey: process.env.SUPABASE_PUBLISHABLE_KEY || '',
    secretKey: process.env.SUPABASE_SECRET_KEY || '',
    jwksUrl: process.env.SUPABASE_JWKS_URL || ''
  }
};

module.exports = config;
