const http = require('node:http');
const { Server } = require('socket.io');
const app = require('./app');
const config = require('./config');
const { runMigrations } = require('./database/migrations');
const { runSeeds } = require('./database/seeds');
const { closeDb, driverType } = require('./database/db');
const setupSocketIO = require('./socket/socketHandler');

async function startServer() {
  try {
    console.log(`[AnonChat] SQLite Driver active: ${driverType}`);
    console.log('[AnonChat] Initializing database migrations...');
    runMigrations();

    // Initialize and restore persistent data from Render PostgreSQL Cloud (survives Render restarts)
    try {
      const { initPostgresSchema } = require('./database/initPostgres');
      await initPostgresSchema();
      const { pullFromCloud } = require('./database/cloudSync');
      const { getDb } = require('./database/db');
      await pullFromCloud(getDb());
    } catch (syncErr) {
      console.warn('[AnonChat] Render PostgreSQL initial sync warning:', syncErr.message);
    }

    console.log('[AnonChat] Running database seeds for any missing defaults...');
    await runSeeds();

    const server = http.createServer(app);

    // Socket.IO configuration
    const io = new Server(server, {
      cors: {
        origin: '*',
        methods: ['GET', 'POST']
      },
      transports: ['websocket', 'polling']
    });

    // Make io accessible to Express routes if needed
    app.set('io', io);

    // Initialize real-time messaging logic
    setupSocketIO(io);

    server.listen(config.port, config.host, () => {
      console.log(`=======================================================`);
      console.log(` AnonChat Privacy-Focused Messaging Server`);
      console.log(` Status: Running`);
      console.log(` Port: ${config.port}`);
      console.log(` Host: ${config.host}`);
      console.log(` API Endpoint:  http://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}/api`);
      console.log(` Admin Panel:   http://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}/admin`);
      console.log(` Landing Page:  http://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}/`);
      console.log(`=======================================================`);
    });

    // Graceful shutdown handling
    const handleShutdown = (signal) => {
      console.log(`\n[AnonChat] Received ${signal}. Shutting down gracefully...`);
      io.close(() => {
        console.log('[AnonChat] Socket.IO closed.');
      });
      server.close(() => {
        console.log('[AnonChat] HTTP server closed.');
        closeDb();
        console.log('[AnonChat] Database connection closed.');
        process.exit(0);
      });
    };

    process.on('SIGINT', () => handleShutdown('SIGINT'));
    process.on('SIGTERM', () => handleShutdown('SIGTERM'));

    return { server, io };
  } catch (err) {
    console.error('[AnonChat Fatal Error]', err);
    process.exit(1);
  }
}

if (require.main === module) {
  startServer();
}

module.exports = { startServer };
