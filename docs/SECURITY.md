# AnonChat — Security, Cryptographic & Privacy Architecture

This document provides a comprehensive security specification, threat model, cryptographic architecture, and verification checklist for the **AnonChat** platform.

---

## 1. Threat Model & Security Principles

AnonChat is engineered around four non-negotiable security requirements:

1. **Zero Real-World Identity Association:**
   - No phone numbers, emails, or hardware identifiers (IMEI/MAC) are required or collected.
   - Accounts are identified solely by randomly generated cryptographic identifiers (`USR-XXXX-XXXX` for regular users, `ADM-XXXX-XXXX` for administrators).
   - Address book harvesting is strictly eliminated.

2. **Zero Knowledge Server Architecture:**
   - The backend operates purely as an encrypted payload router.
   - Plaintext message contents are never transmitted across the network, stored in databases, or logged in server telemetry.
   - Private encryption keys never leave the client's device.

3. **Client-Side Visual Protection (`FLAG_SECURE`):**
   - Sensitive screens on Android enforce `WindowManager.LayoutParams.FLAG_SECURE`.
   - Screenshots and screen capture recordings are technically blocked by the Android SurfaceFlinger.
   - The OS recent task switcher displays an obscured/blank window.
   - **Explicit Boundary Disclosure:** As technically disclosed in the application, software-level window flags cannot prevent physical observation or optical recording by external cameras.

4. **Tamper-Proof Server-Authoritative Licensing:**
   - 7-day trials and license expirations are computed strictly using server-side timestamps (`Date.now()`).
   - Modifying device clocks, re-installing the APK, or clearing local data cannot bypass server-side eligibility rules.
   - Role permissions (`role === 'admin'`) are enforced in the database on every privileged request, never inferred from ID prefixes alone.

---

## 2. Cryptographic Protocol Specification

### 2.1 Identity & Key Agreement
- **Algorithm:** Elliptic Curve Diffie-Hellman (ECDH) over NIST P-256 (`secp256r1`).
- **Key Generation:** Generated client-side using `KeyPairGenerator.getInstance("EC")` with `SecureRandom`.
- **Public Key Distribution:** The public key (encoded in standard X.509 ASN.1 Base64) is published to the AnonChat directory during registration or key rotation.
- **Private Key Storage:** The private key (encoded in PKCS#8 ASN.1) is stored in protected private application storage.

### 2.2 Key Derivation Function (HKDF)
When User A sends a message to User B:
1. User A computes ECDH shared secret:
   $$\text{SharedSecret} = \text{ECDH}(\text{PrivateKey}_A, \text{PublicKey}_B)$$
2. A 256-bit symmetric AES key is derived using RFC 5869 HKDF-SHA256:
   $$\text{PRK} = \text{HMAC-SHA256}(\text{Salt}, \text{SharedSecret})$$
   $$\text{AES-Key} = \text{HMAC-SHA256}(\text{PRK}, \text{Info} \parallel 0x01)$$
   where:
   - `Salt`: `"AnonChat_E2EE_v1_Salt"`
   - `Info`: `"AnonChat_AES256GCM_KeyDerivation"`

### 2.3 Authenticated Encryption (AEAD)
- **Algorithm:** `AES/GCM/NoPadding` (AES-256 in Galois/Counter Mode).
- **Initialization Vector (IV):** 12-byte (96-bit) cryptographically secure random bytes generated uniquely for every message via `SecureRandom`.
- **Authentication Tag:** 16-byte (128-bit) tag appended to the ciphertext to guarantee integrity and authenticity.
- **Relay Payload Structure:**
  ```json
  {
    "conversationId": "conv_8f4c2e9a1b3d5c7f",
    "senderId": "USR-A7K9-2M4P",
    "recipientId": "USR-B3M8-9K2L",
    "encryptedContent": "dGVzdCBjaXBoZXJ0ZXh0...",
    "iv": "gH4jK8mN2pQ=",
    "authTag": "a1b2c3d4e5f6g7h8"
  }
  ```

### 2.4 Decryption & Integrity Verification
1. Recipient receives the ciphertext, IV, and auth tag.
2. Recipient computes:
   $$\text{SharedSecret} = \text{ECDH}(\text{PrivateKey}_B, \text{PublicKey}_A)$$
3. Derives the identical AES-256 key via HKDF.
4. Initializes `Cipher` with `GCMParameterSpec(128, IV)`.
5. Executes `cipher.doFinal(ciphertext || authTag)`.
6. If any bit of the ciphertext, IV, or tag has been tampered with by an active network adversary, `AEADBadTagException` is thrown and the message is rejected.

---

## 3. Account Security & Recovery Flow

### 3.1 Password Storage
- Passwords are never stored in plaintext.
- Hashed using `bcrypt` with a cost factor (salt rounds) of 12.
- Minimum password length: 8 characters.

### 3.2 Single-Use Recovery Code Flow
To ensure privacy without email or SMS verification:
1. Upon registration, the server generates a high-entropy 128-bit grouped recovery code (`RC-XXXX-XXXX-XXXX`).
2. The recovery code is displayed to the user **once** with an urgent advisory to save it offline.
3. The server hashes the recovery code using SHA-256 with a unique server salt before saving:
   $$\text{RecoveryHash} = \text{SHA256}(\text{"recovery\_salt\_"} \parallel \text{NormalizedCode})$$
4. When performing recovery (`POST /api/auth/recover`):
   - The user provides their `USR` ID, recovery code, and new password.
   - The recovery hash is verified using constant-time comparison (`crypto.timingSafeEqual`) to protect against timing attacks.
   - Upon successful recovery, the old recovery code is immediately invalidated, the password hash is updated, and a **fresh new recovery code** is issued.

---

## 4. Licensing & Anti-Abuse Controls

### 4.1 7-Day Free Trial Enforcement
- `trial_started_at` and `trial_expires_at` are calculated upon registration on the backend.
- The backend verifies `trial_expires_at > Date.now()` on every message transmission.
- Altering the local device clock on Android has zero effect because the server clock validates all requests.

### 4.2 License Key Generation & Validation
- License format: `ANON-XXXX-XXXX-XXXX`.
- Keys are generated strictly by authenticated administrators with `role === 'admin'`.
- Redemption is executed within an atomic database transaction (`runTransaction`):
  - Validates key exists, status is `unused`, and not `revoked`.
  - Extends user's `premium_expires_at` by the plan duration in days (e.g. 30, 90, 180, 365 days).
  - Updates license state to `redeemed` and records `redeemed_by_user_id`.
  - Prevents race conditions and duplicate redemptions.

### 4.3 Rate Limiting & Abuse Defense
- **API Limiter:** 300 requests per 15 minutes per IP address.
- **Auth Limiter:** 20 login/register attempts per 15 minutes per IP to prevent credential stuffing.
- **License Limiter:** 10 redemption attempts per 10 minutes per IP to prevent key guessing.
- **Block & Report Engine:** Users can block any sender immediately; blocked senders are blocked at the database query level from relaying further packets.

---

## 5. Security Audit Checklist

| Item | Requirement | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| **SEC-01** | Zero real-world identity collected | Inspect `users` table schema: No email/phone fields exist | Verified |
| **SEC-02** | Cryptographic user ID generation | Uniqueness test: `USR-` and `ADM-` random alphanumeric | Verified |
| **SEC-03** | Server-side role enforcement | API checks `req.user.role === 'admin'`, never ID prefix alone | Verified |
| **SEC-04** | No plaintext passwords stored | Passwords hashed with bcrypt (rounds = 12) | Verified |
| **SEC-05** | Single-use recovery codes | SHA-256 hashed on server; new code issued on recovery | Verified |
| **SEC-06** | E2EE payload integrity | Server stores only `encrypted_content`, `iv`, `auth_tag` | Verified |
| **SEC-07** | Client-side key control | Private keys stored only in private device storage | Verified |
| **SEC-08** | FLAG_SECURE window shield | `setFlags(FLAG_SECURE)` in `BaseSecureActivity` | Verified |
| **SEC-09** | External camera disclosure | Explicit dialog informing user of physical capture limits | Verified |
| **SEC-10** | Clock-tampering protection | Server clock (`Date.now()`) dictates trial and license expiry | Verified |
| **SEC-11** | Duplicate license prevention | Atomic DB transactions with `UNIQUE` index on license key | Verified |
| **SEC-12** | Sensitive log redaction | Passwords, tokens, and payloads redacted in logger | Verified |
| **SEC-13** | Brute force rate limiting | Tiered express-rate-limit on auth and license redemption | Verified |
| **SEC-14** | Blocking enforcement | Messages from blocked users rejected with HTTP 403 | Verified |
