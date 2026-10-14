# AnonChat — System Architecture & Component Design

This document details the software architecture, component relationships, data flow diagrams, and database schema for the AnonChat platform.

---

## 1. High-Level Architecture Overview

AnonChat consists of four primary subsystems:
1. **Native Android Application (Java):** Client interface providing zero-identity authentication, client-side E2EE key generation and encryption, `FLAG_SECURE` screen capture defense, and real-time messaging.
2. **Backend Services & API Gateway (Node.js):** REST API and Socket.IO real-time relay service handling identity registration, key discovery, blinded ciphertext relay, trial/licensing enforcement, and admin operations.
3. **Storage Engine (SQLite/PostgreSQL):** High-performance relational database with WAL mode and atomic transaction guarantees.
4. **Web Administrator Panel (HTML5/CSS3/Vanilla JS):** Separate responsive web dashboard for platform administrators to monitor statistics, manage users, generate licenses, configure plans, and inspect audit logs.

```
       +-------------------------------------------------------------+
       |                   AnonChat Architecture                     |
       +-------------------------------------------------------------+

   +-----------------------+                    +--------------------+
   |   Native Android App  |                    | Web Admin Console  |
   |   (Java / Material)   |                    | (Responsive SPA)   |
   | - FLAG_SECURE Window  |                    | - System Stats     |
   | - ECDH + AES-256-GCM  |                    | - User Management  |
   | - Client Key Storage  |                    | - License Gen      |
   +-----------+-----------+                    +---------+----------+
               |                                          |
   (HTTPS / WSS JSON Relays)                 (HTTPS Admin JWT API)
               |                                          |
               +--------------------+---------------------+
                                    |
                        +-----------v-----------+
                        |  AnonChat Gateway     |
                        |  - Express REST APIs  |
                        |  - Socket.IO Server   |
                        |  - Rate Limiting      |
                        |  - Trial/License Svc  |
                        |  - Audit Log Engine   |
                        +-----------+-----------+
                                    |
                               (WAL Engine)
                                    |
                        +-----------v-----------+
                        |   Encrypted Database  |
                        |   - users             |
                        |   - conversations     |
                        |   - messages (cipher) |
                        |   - licenses          |
                        |   - plans (4 tiers)   |
                        |   - audit_logs        |
                        +-----------------------+
```

---

## 2. Core Interaction Flows

### 2.1 User Registration & E2EE Key Provisioning
```mermaid
sequenceDiagram
    autonumber
    actor User as Android Client
    participant App as Android CryptoManager
    participant API as Backend Auth API
    participant DB as Database

    User->>App: Input Display Name (Optional) & Password
    App->>App: Generate ECDH Identity Keypair (NIST P-256)
    App->>App: Save PrivateKey in Local Secure Storage
    App->>API: POST /api/auth/register (PublicKey, PasswordHash)
    API->>API: Generate Unique USR-XXXX-XXXX ID
    API->>API: Generate Recovery Code (RC-XXXX-XXXX-XXXX)
    API->>API: Compute Bcrypt(Password) & SHA256(RecoveryCode)
    API->>API: Calculate 7-Day Trial Window (trial_expires_at)
    API->>DB: INSERT INTO users & audit_logs
    API-->>App: Return User ID, JWT Token, One-Time Recovery Code
    App-->>User: Display Credentials Modal (Prompt to Save Recovery Code)
```

### 2.2 End-to-End Encrypted Message Relay
```mermaid
sequenceDiagram
    autonumber
    actor Alice as Alice (Client A)
    participant Socket as Socket.IO Gateway
    participant DB as Server DB
    actor Bob as Bob (Client B)

    Alice->>Alice: Enter message text
    Alice->>Alice: Compute SharedSecret = ECDH(Alice_PrivKey, Bob_PubKey)
    Alice->>Alice: Derive AES-Key = HKDF(SharedSecret)
    Alice->>Alice: Generate 12-byte random IV
    Alice->>Alice: Encrypt Ciphertext + 16-byte AuthTag = AES-GCM(Text, AES-Key, IV)
    Alice->>Socket: emit("send_message", {convId, recipientId, ciphertext, iv, authTag})
    Socket->>Socket: Verify Alice trial/license is active (server clock)
    Socket->>DB: INSERT INTO messages (status: 'sent') [NO PLAINTEXT]
    Socket-->>Alice: ACK message sent (status: sent)
    Socket->>Bob: emit("new_message", payload)
    Bob->>Bob: Compute SharedSecret = ECDH(Bob_PrivKey, Alice_PubKey)
    Bob->>Bob: Derive AES-Key = HKDF(SharedSecret)
    Bob->>Bob: Decrypt Plaintext = AES-GCM(Ciphertext, AES-Key, IV, AuthTag)
    Bob->>Socket: emit("message_delivered", {messageId})
    Socket->>DB: UPDATE messages SET status = 'delivered'
    Socket-->>Alice: emit("message_status_update", {messageId, status: 'delivered'})
```

### 2.3 Trial Expiry & License Key Redemption
```mermaid
sequenceDiagram
    autonumber
    actor User as User (Android App)
    participant API as Backend License API
    participant Admin as Platform Owner (Admin Web)
    participant DB as Database

    Admin->>API: POST /api/admin/licenses/generate (Plan: Pro 90d)
    API->>DB: INSERT INTO licenses (key: ANON-XXXX-XXXX-XXXX, status: unused)
    Admin-->>User: Issues License Key via Secure Channel (email/direct)
    User->>API: POST /api/license/redeem (Key: ANON-XXXX-XXXX-XXXX)
    API->>DB: BEGIN TRANSACTION
    API->>DB: Check key exists, status == 'unused'
    API->>DB: UPDATE licenses SET status = 'redeemed', redeemed_by = user.id
    API->>DB: UPDATE users SET premium_expires_at = now + 90 days, status = 'active'
    API->>DB: INSERT INTO audit_logs (LICENSE_REDEEMED)
    API->>DB: COMMIT TRANSACTION
    API-->>User: Success (Plan: Pro Confidential, 90 Days Added)
```

---

## 3. Database Schema Specification

```mermaid
erDiagram
    users ||--o{ conversation_members : participates
    users ||--o{ messages : sends
    users ||--o{ messages : receives
    users ||--o{ licenses : redeems
    users ||--o{ blocked_users : blocks
    conversations ||--|{ conversation_members : contains
    conversations ||--o{ messages : stores
    plans ||--o{ licenses : categorizes

    users {
        string id PK "USR-XXXX-XXXX / ADM-XXXX-XXXX"
        string display_name
        string password_hash "Bcrypt (cost 12)"
        string role "user | admin"
        string public_key "ECDH X.509 Base64"
        string recovery_code_hash "SHA-256 Hash"
        string status "active | suspended | deleted"
        int64 trial_started_at
        int64 trial_expires_at
        string subscription_status "trial | active | expired | suspended"
        int64 premium_expires_at
        int64 created_at
        int64 updated_at
    }

    plans {
        string id PK "starter | pro | elite | annual"
        string name "Configurable display name"
        int duration_days "30 | 90 | 180 | 365"
        string description
        string features "JSON Array"
        int is_active "1 | 0"
        int64 created_at
        int64 updated_at
    }

    licenses {
        string id PK "UUID"
        string license_key UK "ANON-XXXX-XXXX-XXXX"
        string plan_id FK
        string status "unused | redeemed | revoked"
        string created_by_admin_id FK
        string redeemed_by_user_id FK
        int64 redeemed_at
        int64 revoked_at
        string notes
        int64 created_at
    }

    conversations {
        string id PK "conv_xxxx"
        string type "direct"
        int64 created_at
        int64 updated_at
    }

    conversation_members {
        string id PK
        string conversation_id FK
        string user_id FK
        int64 joined_at
        int64 last_read_at
    }

    messages {
        string id PK "msg_xxxx"
        string conversation_id FK
        string sender_id FK
        string recipient_id FK
        string encrypted_content "AES-256 Ciphertext (Base64)"
        string iv "12-Byte IV (Base64)"
        string auth_tag "16-Byte Auth Tag (Base64)"
        string sender_ephemeral_key
        string status "sent | delivered | read"
        int64 delivered_at
        int64 read_at
        int64 created_at
    }

    blocked_users {
        string id PK
        string blocker_id FK
        string blocked_id FK
        int64 created_at
    }

    audit_logs {
        string id PK
        string actor_id
        string action "ADMIN_LOGIN | LICENSE_GENERATED | etc"
        string target_id
        string details "JSON summary"
        string ip_address
        int64 created_at
    }
```
