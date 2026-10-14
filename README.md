# AnonChat — Privacy-Focused ID-Based Messaging Platform

AnonChat is an end-to-end encrypted messaging platform operating purely on cryptographic identifiers (`USR-XXXX-XXXX`). It completely eliminates personal identity requirements (no phone numbers, no email addresses, no address-book scraping).

The platform consists of:
1. **Native Android Application (Java)** with `FLAG_SECURE` screen capture defense, NIST P-256 (secp256r1) ECDH key exchange, AES-256-GCM encryption, and a clean messaging interface with Material Design.
2. **Secure Real-Time Backend & Database** with Node.js, Express, Socket.IO, native SQLite/WAL, role-based authorization, rate limiting, and 7-day trial & license management.
3. **Web Administrator Panel** for real-time analytics, user account management (suspension/reactivation, secure password reset), license generation, 4 configurable plans editing, and audit logging.
4. **Static HTML/CSS Landing Page** explaining privacy capabilities and owner license acquisition.
5. **Full Test Suite & Documentation** including unit/integration tests, API specifications, and security audits.

---

## Deliverables Summary

| Deliverable | Location | Description |
| :--- | :--- | :--- |
| **Android Studio Project** | [`android/`](file:///android/) | Complete native Java Android application with Material Design |
| **Backend Service** | [`backend/`](file:///backend/) | Express + Socket.IO server with zero plaintext storage |
| **Database & Migrations** | [`backend/src/database/`](file:///backend/src/database/) | Complete 9-table schema, migrations runner, and seeder |
| **Web Administrator Panel** | [`admin-web/`](file:///admin-web/) | Responsive administrative website for dashboard, users, and licenses |
| **Static Landing Page** | [`landing/`](file:///landing/) | Static HTML5/CSS3 landing page with interactive license modal |
| **API Specification** | [`docs/API.md`](file:///docs/API.md) | Full REST & WebSocket event documentation |
| **Security & E2EE Specs** | [`docs/SECURITY.md`](file:///docs/SECURITY.md) | Cryptographic protocol, threat model, and security checklist |
| **Architecture Reference** | [`docs/ARCHITECTURE.md`](file:///docs/ARCHITECTURE.md) | Sequence diagrams, data flows, and database ER diagram |
| **Deployment Manual** | [`docs/DEPLOYMENT.md`](file:///docs/DEPLOYMENT.md) | Production setup with Nginx reverse proxy and SSL |

---

## 1. Quick Start Guide

### Prerequisites
- Node.js v20+ / v22+ / v24+
- Java JDK 17 or 21 LTS
- Android Studio or Android SDK 34+

---

### 2. Running the Backend & Web Services

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the automated test suite (verifies ID generation, 7-day trial calculations, E2EE ciphertext-only storage, blocking, and license redemption):
   ```bash
   npm test
   ```
4. Start the server:
   ```bash
   npm start
   ```
5. Access the web interfaces:
   - **REST API:** `http://localhost:4000/api`
   - **Administrator Web Panel:** `http://localhost:4000/admin`
   - **Landing Page:** `http://localhost:4000/`

#### Default Bootstrap Credentials:
- **Admin ID:** `ADM-MASTER-01`
- **Master Password:** `AnonAdminSecure2026!#`
- *(Role is strictly enforced in the database as `role: 'admin'`, never based on ID prefix alone).*

---

### 3. Administrator Dashboard Features

Open `http://localhost:4000/admin`:
- **Dashboard Overview:** Displays total registered accounts, active 7-day trials, active premium licenses, total relayed encrypted messages, and real-time security events.
- **User Accounts:** Search users by ID, inspect subscription status, suspend or reactivate users, and generate secure temporary passwords (never exposes plaintext passwords).
- **License Inventory:** Generate new cryptographically unique keys (`ANON-XXXX-XXXX-XXXX`), select from the 4 plans, copy to clipboard, and revoke keys.
- **Configurable Plans:** Customize the 4 default plans:
  1. *Starter Anonymous* (30 Days)
  2. *Pro Confidential* (90 Days)
  3. *Elite Sovereign* (180 Days)
  4. *Annual Fortress* (365 Days)
- **Audit Logs:** Immutable audit log tracking administrative logins, user suspensions, password resets, and license activations.
- **Database Inspector:** Authorized view of database tables and record counts without exposing private messages or password hashes.

---

### 4. Android Native Application

#### Opening in Android Studio:
1. Open Android Studio.
2. Select **Open** and choose the `android` folder.
3. Allow Gradle to sync dependencies.
4. Run on an Android Emulator or physical device (Android 7.0 / API 24+).

#### Key Android Features Implemented:
- **Zero Identity Registration:** Generates unique `USR-XXXX-XXXX` ID. Displays a single-use Recovery Code (`RC-XXXX-XXXX-XXXX`) with a one-time copy confirmation.
- **FLAG_SECURE Active Protection:** Implemented in `BaseSecureActivity`. Blocks Android screenshots, screen recording apps, and obscures recent-task previews in Android's app switcher.
- **Explicit Privacy Disclosure Dialog:** Displays clear technical disclosure informing users that while software screen captures are blocked, external physical cameras cannot be prevented.
- **Client-Side End-to-End Encryption (E2EE):**
  - Generates NIST P-256 ECDH keypair on device.
  - Derives 256-bit AES key via HKDF (HMAC-SHA256).
  - Encrypts messages using authenticated `AES/GCM/NoPadding` with 12-byte random IV and 128-bit authentication tag.
  - Server stores only ciphertext, IV, and auth tag; plaintext is never transmitted or logged.
- **Trial & Premium Licensing:**
  - 7-Day trial status banner with remaining days.
  - After trial expiry: input bar is locked and replaced with "Contact the Owner to Get Your License" prompt.
  - License activation screen under **Settings → Activate License**: users enter their `ANON-XXXX-XXXX-XXXX` key for server-side validation.
- **User Actions:** Search users by ID, real-time Socket.IO chat, delivery status receipts (sent ✓, delivered ✓✓, read ✓✓), block user, report user, logout, and account deletion.

---

## 5. Security & Cryptographic Verifications

AnonChat adheres to strict cryptographic standards:
- **Password Security:** Salted and hashed with `bcrypt` (work factor 12).
- **Recovery Credentials:** Salted and hashed with `SHA-256`; single-use and rotated on password reset.
- **E2EE:** Elliptic Curve Diffie-Hellman (ECDH) + HKDF-SHA256 + AES-256-GCM.
- **Clock Tampering Protection:** All trial and license checks rely on server-side time (`Date.now()`); device clock alterations cannot bypass eligibility.
- **Rate Limiting:** Protects `/api/auth/*` (20 req / 15 min) and `/api/license/redeem` (10 req / 10 min) against brute-force attacks.

For detailed security specifications and threat models, review [`docs/SECURITY.md`](file:///docs/SECURITY.md).
