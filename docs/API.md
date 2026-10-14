# AnonChat API & WebSocket Protocol Documentation

This document describes the REST API and Socket.IO real-time event protocols for the AnonChat platform.

## Base URL
```
Development (Localhost): http://localhost:4000
Android Emulator Loopback: http://10.0.2.2:4000
Production: https://api.yourdomain.com
```

## Authentication & Headers
- All protected endpoints require a valid JWT Bearer token in the `Authorization` header:
  ```http
  Authorization: Bearer <jwt_token>
  ```
- All request payloads and responses use `Content-Type: application/json`.

---

## 1. Authentication Endpoints (`/api/auth`)

### 1.1 User Registration
Creates a brand-new anonymous user account with zero real-world identity requirements (no phone number or email address). Generates an assigned `USR-XXXX-XXXX` ID and a one-time recovery code `RC-XXXX-XXXX-XXXX`.

- **Endpoint:** `POST /api/auth/register`
- **Rate Limit:** 20 requests per 15 minutes
- **Request Body:**
  ```json
  {
    "displayName": "GhostRunner",      // Optional string (max 50 chars)
    "password": "SecurePassword123!",  // Required string (min 8 chars)
    "publicKey": "04a1b2c3..."         // Optional Base64/Hex ECDH public key
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "user": {
      "id": "USR-A7K9-2M4P",
      "display_name": "GhostRunner",
      "role": "user",
      "status": "active",
      "public_key": "04a1b2c3...",
      "trial_started_at": 1791972000000,
      "trial_expires_at": 1792576800000,
      "subscription_status": "trial",
      "premium_expires_at": null,
      "created_at": 1791972000000
    },
    "token": "eyJhbGciOi...",
    "recoveryCode": "RC-7K9M-2P4A-8F4C"
  }
  ```

### 1.2 User Login
Authenticates an existing user account using their assigned ID and password.

- **Endpoint:** `POST /api/auth/login`
- **Rate Limit:** 20 requests per 15 minutes
- **Request Body:**
  ```json
  {
    "id": "USR-A7K9-2M4P",
    "password": "SecurePassword123!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "user": {
      "id": "USR-A7K9-2M4P",
      "display_name": "GhostRunner",
      "role": "user",
      "status": "active",
      "public_key": "04a1b2c3...",
      "trial_started_at": 1791972000000,
      "trial_expires_at": 1792576800000,
      "subscription_status": "trial",
      "premium_expires_at": null,
      "created_at": 1791972000000
    },
    "token": "eyJhbGciOi..."
  }
  ```

### 1.3 Account Recovery
Restores account access when a password is lost using the single-use recovery code. Issues a fresh new recovery code.

- **Endpoint:** `POST /api/auth/recover`
- **Rate Limit:** 20 requests per 15 minutes
- **Request Body:**
  ```json
  {
    "id": "USR-A7K9-2M4P",
    "recoveryCode": "RC-7K9M-2P4A-8F4C",
    "newPassword": "NewStrongPassword456!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Account recovered successfully. Please save your new recovery code.",
    "newRecoveryCode": "RC-9B3D-4X1L-6P7Z"
  }
  ```

### 1.4 Delete Account
Permanently deletes and anonymizes the caller's account record and clears public keys.

- **Endpoint:** `POST /api/auth/delete-account`
- **Authentication:** Required (User Token)
- **Response (200 OK):**
  ```json
  {
    "success": true
  }
  ```

---

## 2. User & Directory Endpoints (`/api/users`)

### 2.1 Get Current Profile & Subscription
- **Endpoint:** `GET /api/users/me`
- **Authentication:** Required
- **Response (200 OK):**
  ```json
  {
    "user": {
      "id": "USR-A7K9-2M4P",
      "display_name": "GhostRunner",
      "role": "user",
      "status": "active"
    },
    "subscription": {
      "status": "trial",
      "isTrial": true,
      "isPremium": false,
      "canChat": true,
      "trialExpiresAt": 1792576800000,
      "premiumExpiresAt": null,
      "daysRemaining": 7,
      "notice": "7 days remaining in trial"
    }
  }
  ```

### 2.2 Update E2EE Public Key
- **Endpoint:** `PUT /api/users/public-key`
- **Authentication:** Required
- **Request Body:**
  ```json
  {
    "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE..."
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE..."
  }
  ```

### 2.3 Search User by ID
- **Endpoint:** `GET /api/users/search?id=USR-`
- **Authentication:** Required
- **Response (200 OK):**
  ```json
  {
    "users": [
      {
        "id": "USR-B3M8-9K2L",
        "display_name": "CipherGuard",
        "public_key": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
        "status": "active"
      }
    ]
  }
  ```

### 2.4 Block & Unblock User
- **Block:** `POST /api/users/block` with body `{"blockedId": "USR-B3M8-9K2L"}`
- **Unblock:** `POST /api/users/unblock` with body `{"blockedId": "USR-B3M8-9K2L"}`
- **List Blocked:** `GET /api/users/blocked`

### 2.5 Report User
- **Endpoint:** `POST /api/users/report`
- **Request Body:** `{"reportedId": "USR-B3M8-9K2L", "reason": "Spam"}`

---

## 3. Messaging & Conversations (`/api/chat`)

### 3.1 List Conversations
- **Endpoint:** `GET /api/chat/conversations`
- **Authentication:** Required
- **Response (200 OK):**
  ```json
  {
    "conversations": [
      {
        "id": "conv_8f4c2e9a1b3d5c7f",
        "recipient": {
          "id": "USR-B3M8-9K2L",
          "displayName": "CipherGuard",
          "publicKey": "MFkw...",
          "status": "active"
        },
        "lastMessage": {
          "id": "msg_99a8b7c6d5e4",
          "senderId": "USR-B3M8-9K2L",
          "status": "delivered",
          "timestamp": 1791973000000
        },
        "unreadCount": 1,
        "updatedAt": 1791973000000
      }
    ]
  }
  ```

### 3.2 Get or Create Conversation
- **Endpoint:** `POST /api/chat/conversations`
- **Authentication:** Required
- **Request Body:** `{"recipientId": "USR-B3M8-9K2L"}`
- **Response (200 OK):**
  ```json
  {
    "id": "conv_8f4c2e9a1b3d5c7f",
    "recipient": { ... },
    "isNew": false
  }
  ```

### 3.3 Get Conversation Message History
- **Endpoint:** `GET /api/chat/conversations/:id/messages?limit=50`
- **Authentication:** Required
- **Response (200 OK):**
  ```json
  {
    "messages": [
      {
        "id": "msg_99a8b7c6d5e4",
        "conversationId": "conv_8f4c2e9a1b3d5c7f",
        "senderId": "USR-A7K9-2M4P",
        "recipientId": "USR-B3M8-9K2L",
        "encryptedContent": "dGVzdCBjaXBoZXJ0ZXh0",
        "iv": "gH4jK8mN2pQ=",
        "authTag": "a1b2c3d4e5f6g7h8",
        "status": "read",
        "deliveredAt": 1791973010000,
        "readAt": 1791973020000,
        "createdAt": 1791973000000
      }
    ]
  }
  ```

### 3.4 Send Encrypted Message (REST Fallback)
Enforces server-side trial & license validity. If trial is expired, returns HTTP 403 `SUBSCRIPTION_EXPIRED`.
- **Endpoint:** `POST /api/chat/conversations/:id/messages`
- **Authentication:** Required
- **Request Body:**
  ```json
  {
    "recipientId": "USR-B3M8-9K2L",
    "encryptedContent": "dGVzdCBjaXBoZXJ0ZXh0",
    "iv": "gH4jK8mN2pQ=",
    "authTag": "a1b2c3d4e5f6g7h8"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "message": {
      "id": "msg_99a8b7c6d5e4",
      "conversationId": "conv_8f4c2e9a1b3d5c7f",
      "senderId": "USR-A7K9-2M4P",
      "recipientId": "USR-B3M8-9K2L",
      "encryptedContent": "dGVzdCBjaXBoZXJ0ZXh0",
      "iv": "gH4jK8mN2pQ=",
      "authTag": "a1b2c3d4e5f6g7h8",
      "status": "sent",
      "createdAt": 1791973050000
    }
  }
  ```

---

## 4. Licensing & Plans (`/api/license`)

### 4.1 Get Subscription Status
- **Endpoint:** `GET /api/license/status`
- **Authentication:** Required

### 4.2 List Available Plans
- **Endpoint:** `GET /api/license/plans`
- **Authentication:** Public
- **Response (200 OK):**
  ```json
  {
    "plans": [
      {
        "id": "starter",
        "name": "Starter Anonymous",
        "duration_days": 30,
        "description": "Standard anonymous private messaging channel for 30 days",
        "features": ["E2EE Messaging", "FLAG_SECURE Shield"]
      },
      { "id": "pro", "name": "Pro Confidential", "duration_days": 90, ... },
      { "id": "elite", "name": "Elite Sovereign", "duration_days": 180, ... },
      { "id": "annual", "name": "Annual Fortress", "duration_days": 365, ... }
    ]
  }
  ```

### 4.3 Redeem License Key
- **Endpoint:** `POST /api/license/redeem`
- **Authentication:** Required
- **Rate Limit:** 10 attempts per 10 minutes (anti-brute-force)
- **Request Body:** `{"key": "ANON-7K9M-2P4A-8F4C"}`
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Successfully activated Pro Confidential (90 days added)",
    "plan_id": "pro",
    "plan_name": "Pro Confidential",
    "duration_days": 90,
    "premium_expires_at": 1799752800000,
    "days_remaining": 97
  }
  ```

---

## 5. Administrator Management (`/api/admin`)
All endpoints strictly require `role === 'admin'` in database.

- `POST /api/admin/login`: Admin sign in.
- `GET /api/admin/stats`: Real-time platform metrics.
- `GET /api/admin/users`: List users with search and filter parameters (`?search=USR&status=active&page=1`).
- `POST /api/admin/users/:id/suspend`: Suspend user account.
- `POST /api/admin/users/:id/reactivate`: Reactivate account.
- `POST /api/admin/users/:id/reset-password`: Generate single-use temporary password.
- `GET /api/admin/licenses`: List license inventory.
- `POST /api/admin/licenses/generate`: Generate new license key (`{"planId": "pro", "notes": "Customer A"}`).
- `POST /api/admin/licenses/:id/revoke`: Revoke license key.
- `GET /api/admin/plans`: List 4 configurable plans.
- `PUT /api/admin/plans/:id`: Update plan name, duration, description, features.
- `GET /api/admin/audit-logs`: Review security and admin actions.
- `GET /api/admin/database-inspector`: Authorized database record counts and schemas.

---

## 6. Real-Time WebSocket Protocol (Socket.IO)

### Connection
- **URL:** `ws://localhost:4000` or `wss://api.yourdomain.com`
- **Auth Handshake:**
  ```javascript
  const socket = io('https://api.yourdomain.com', {
    auth: { token: 'jwt_bearer_token' }
  });
  ```

### Client -> Server Events
1. `join_conversation`: `{"conversationId": "conv_xxx"}`
2. `leave_conversation`: `{"conversationId": "conv_xxx"}`
3. `send_message`:
   ```json
   {
     "conversationId": "conv_xxx",
     "recipientId": "USR-B3M8-9K2L",
     "encryptedContent": "dGVzdA==",
     "iv": "iv==",
     "authTag": "tag=="
   }
   ```
4. `message_delivered`: `{"messageId": "msg_xxx", "conversationId": "conv_xxx", "senderId": "USR-xxx"}`
5. `message_read`: `{"conversationId": "conv_xxx", "senderId": "USR-xxx"}`
6. `typing_start` / `typing_stop`: `{"conversationId": "conv_xxx", "recipientId": "USR-xxx"}`

### Server -> Client Events
1. `new_message`: Incoming encrypted payload routed to `user:<userId>`.
2. `conversation_message`: Broadcast to conversation room `conv:<convId>`.
3. `message_status_update`: `{"messageId": "msg_xxx", "status": "delivered" | "read", "timestamp": 123456}`
4. `user_typing`: `{"conversationId": "conv_xxx", "userId": "USR-xxx", "isTyping": true}`
5. `subscription_expired`: Pushed when trial window expires.
