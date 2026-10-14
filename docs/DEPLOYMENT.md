# AnonChat — Deployment & Operations Guide

This guide describes how to deploy, configure, and maintain the **AnonChat** platform in production environments.

---

## 1. System Requirements

### Backend & Database Server
- **Operating System:** Linux (Ubuntu 22.04 LTS / 24.04 LTS recommended) or Windows Server
- **Node.js:** v20.x or v22.x LTS / v24.x
- **Memory:** Minimum 1 GB RAM (2 GB recommended)
- **Disk:** 10 GB SSD storage
- **Ports:** Port 80 (HTTP redirect), Port 443 (HTTPS & WSS), Port 4000 (Internal Node daemon)

### Android Build Environment
- **JDK:** Java 17 or Java 21 LTS
- **Android SDK:** Platforms 34/35 (`compileSdk 34`)
- **Android Studio:** Android Studio Hedgehog, Iguana, Jellyfish, Koala, Ladybug, or CLI build tools

---

## 2. Backend Deployment

### 2.1 Clone Repository & Install Dependencies
```bash
git clone https://github.com/your-org/anonchat.git
cd anonchat/backend
npm ci --omit=dev
```

### 2.2 Configure Production Environment Variables
Create a production `.env` file from the provided `.env.example`:
```bash
cp .env.example .env
nano .env
```

Ensure the following variables are configured with high-entropy cryptographic values:
```env
NODE_ENV=production
PORT=4000
HOST=127.0.0.1

# Generate with: openssl rand -hex 64
JWT_SECRET=8f4c2e9a1b3d5c7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f
JWT_EXPIRES_IN=30d

# SQLite Persistent Database
DB_PATH=/var/lib/anonchat/data/anonchat.db

# 7-Day Free Trial
TRIAL_DURATION_DAYS=7

# Initial Bootstrap Administrator Account
ADMIN_INITIAL_ID=ADM-MASTER-01
ADMIN_INITIAL_PASSWORD=ReplaceWithStrongMasterSecret2026!

# Allowed Web Admin & Landing Origins
CORS_ORIGINS=https://admin.yourdomain.com,https://yourdomain.com
```

### 2.3 Run Migrations & Database Seeds
```bash
node -e "const { runMigrations } = require('./src/database/migrations'); const { runSeeds } = require('./src/database/seeds'); runMigrations(); runSeeds();"
```

### 2.4 Process Management with PM2
Install PM2 globally and configure the service to run on boot:
```bash
npm install -g pm2
pm2 start src/server.js --name "anonchat-backend"
pm2 save
pm2 startup
```

---

## 3. Reverse Proxy Configuration (Nginx with SSL & WebSocket WSS)

Create `/etc/nginx/sites-available/anonchat`:
```nginx
# HTTP -> HTTPS Redirect
server {
    listen 80;
    listen [::]:80;
    server_name api.yourdomain.com admin.yourdomain.com yourdomain.com;
    return 301 https://$host$request_uri;
}

# HTTPS API Gateway & WebSockets
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name api.yourdomain.com admin.yourdomain.com yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Security Headers
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Socket.IO Real-Time Messaging Gateway
    location /socket.io/ {
        proxy_pass http://127.0.0.1:4000/socket.io/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    # REST APIs & Web Admin
    location / {
        proxy_pass http://127.0.0.1:4000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the site and obtain TLS certificates:
```bash
ln -s /etc/nginx/sites-available/anonchat /etc/nginx/sites-enabled/
certbot --nginx -d api.yourdomain.com -d admin.yourdomain.com -d yourdomain.com
nginx -t && systemctl reload nginx
```

---

## 4. Automated Database Backups & Retention

SQLite in WAL mode supports hot backups without stopping the service. Create `/usr/local/bin/backup-anonchat.sh`:
```bash
#!/bin/bash
BACKUP_DIR="/var/backups/anonchat"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"

# Safe hot SQLite backup
sqlite3 /var/lib/anonchat/data/anonchat.db ".backup '${BACKUP_DIR}/anonchat_${TIMESTAMP}.db'"

# Encrypt backup with GPG
gpg --symmetric --batch --passphrase-file /etc/anonchat-backup.key "${BACKUP_DIR}/anonchat_${TIMESTAMP}.db"
rm "${BACKUP_DIR}/anonchat_${TIMESTAMP}.db"

# Retain backups for 30 days
find "$BACKUP_DIR" -type f -name "anonchat_*.db.gpg" -mtime +30 -delete
```

Add to cron:
```bash
0 2 * * * /usr/local/bin/backup-anonchat.sh
```

---

## 5. Android Application Compilation & Signing

### 5.1 Configure Server URL
In `android/app/src/main/java/com/anonchat/app/utils/Constants.java`, set the production endpoint:
```java
public static final String DEFAULT_BASE_URL = "https://api.yourdomain.com/";
```

### 5.2 Build Signed Release APK / AAB
Generate a keystore (if not already existing):
```bash
keytool -genkey -v -keystore anonchat-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias anonchat
```

Compile release APK using Gradle:
```bash
cd android
./gradlew assembleRelease
```
The resulting APK is located at:
`android/app/build/outputs/apk/release/app-release.apk`
