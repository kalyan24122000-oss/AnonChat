const express = require('express');
const path = require('node:path');
const cors = require('cors');
const helmet = require('helmet');
const config = require('./config');
const errorHandler = require('./middleware/errorHandler');
const { apiLimiter } = require('./middleware/rateLimiter');

const authRoutes = require('./routes/authRoutes');
const userRoutes = require('./routes/userRoutes');
const chatRoutes = require('./routes/chatRoutes');
const licenseRoutes = require('./routes/licenseRoutes');
const adminRoutes = require('./routes/adminRoutes');

const app = express();

// Security Headers with relaxed CSP for local admin panel and landing
app.use(
  helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
  })
);

// CORS configuration
app.use(
  cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
  })
);

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Apply rate limiting to all API endpoints
app.use('/api', apiLimiter);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    app: 'AnonChat Backend',
    timestamp: Date.now(),
    env: config.nodeEnv
  });
});

// API Routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/license', licenseRoutes);
app.use('/api/admin', adminRoutes);

// Serve Admin Panel static files
const fs = require('node:fs');
let adminWebPath = path.resolve(__dirname, '../../admin-web');
if (!fs.existsSync(adminWebPath)) {
  adminWebPath = path.resolve(__dirname, '../admin-web');
}
if (!fs.existsSync(adminWebPath)) {
  adminWebPath = path.resolve(__dirname, './admin-web');
}
app.use('/admin', express.static(adminWebPath));

// Serve Landing page static files
let landingPath = path.resolve(__dirname, '../../landing');
if (!fs.existsSync(landingPath)) {
  landingPath = path.resolve(__dirname, '../landing');
}
if (!fs.existsSync(landingPath)) {
  landingPath = path.resolve(__dirname, './landing');
}

// Direct APK download route
app.get('/PrimeChat-v1.0.apk', (req, res) => {
  const candidates = [
    path.resolve(__dirname, '../../PrimeChat-v1.0.apk'),
    path.resolve(landingPath, 'PrimeChat-v1.0.apk'),
    path.resolve(__dirname, '../PrimeChat-v1.0.apk'),
    path.resolve(__dirname, './PrimeChat-v1.0.apk')
  ];
  for (const apkPath of candidates) {
    if (fs.existsSync(apkPath)) {
      return res.download(apkPath, 'PrimeChat-v1.0.apk');
    }
  }
  res.status(404).send('PrimeChat APK not found on server');
});

app.use('/', express.static(landingPath));

// Global Error Handler
app.use(errorHandler);

module.exports = app;
