# Xbox Remote Play - Authentication Flow Documentation
## Team 1 (Dirty Room) - Functional Specification

**Document Purpose:** Describe the authentication flow for Xbox Remote Play (console streaming) for Team 2 to implement cleanroom code.  
**Source Data:** HAR files from xbox.com/play/consoles  
**Date:** March 28, 2026  
**Status:** DRAFT - Awaiting Team 2 Review

---

## ⚠️ CRITICAL: Personal Data Sanitization

This document has been sanitized to remove all user-specific data:
- ✅ All tokens are described structurally, not provided literally
- ✅ All XUIDs are represented as `<XUID>` placeholder
- ✅ All session IDs are represented as `<SESSION_ID>`
- ✅ All personal identifiers have been redacted

**Team 2 must NEVER see actual tokens or personal data from these HAR files.**

---

## Authentication Flow Overview

Xbox Remote Play uses a **multi-stage OAuth 2.0 flow** with Microsoft Account (MSA) authentication, followed by Xbox Live token acquisition, and finally XSTS (Xbox Secure Token Service) authorization.

### High-Level Flow Diagram

```
┌──────────┐    OAuth 2.0    ┌──────────────┐    User Token    ┌──────────────┐
│          │  Authorization  │   Microsoft  │   Request (JWT)  │  Xbox Live   │
│  Client  │───────────────> │   OAuth 2.0  │────────────────> │    User      │
│          │                 │   Endpoint   │                  │     Auth     │
└──────────┘                 └──────────────┘                  └──────────────┘
     │                              │                                   │
     │                              │  Authorization Code               │ User Token (JWT)
     │                              <───────────────────────────────────┤
     │                                                                   │
     │  XSTS Token Request                                              │
     │─────────────────────────────────────────────────────────────────> ┌──────────────┐
     │                                                                     │     XSTS     │
     │  <────────────────────────────────────────────────────────────────│  Authorize   │
     │  XSTS Token (JWT)                                                  └──────────────┘
     │
     │  Chat Auth Token Request
     │─────────────────────────────────────────────────────────────────> ┌──────────────┐
     │                                                                     │ Chat Service │
     │  <────────────────────────────────────────────────────────────────│     Auth     │
     │  Chat AuthKey (GUID)                                               └──────────────┘
     │
     │  WebSocket Connect (with AuthKey)
     │─────────────────────────────────────────────────────────────────> ┌──────────────┐
     │                                                                     │  Real-Time   │
     │  <────────────────────────────────────────────────────────────────│  Activities  │
     │  Bidirectional WebSocket Communication                             │   (RTA) +    │
                                                                           │  Chat        │
                                                                           └──────────────┘
```

---

## Stage 1: Microsoft OAuth 2.0 Authorization

### Endpoint
```
POST https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize
```

### Purpose
Authenticate user with their Microsoft Account and obtain an authorization code.

### Request Parameters (Query String)

| Parameter | Value | Description |
|-----------|-------|-------------|
| `client_id` | `1f907974-e22b-4810-a9de-d9647380c97e` | Xbox application client ID (constant) |
| `scope` | `xboxlive.signin openid profile offline_access` | Requested OAuth scopes |
| `redirect_uri` | `https://www.xbox.com/auth/msa?action=loggedIn&sandboxId=RETAIL&locale_hint=en-US` | Where to redirect after auth |
| `response_mode` | `fragment` | Return code in URL fragment |
| `response_type` | `code` | Request authorization code |
| `x-client-SKU` | `msal.js.browser` | Microsoft Authentication Library identifier |
| `x-client-VER` | `3.20.0` | MSAL version |
| `client_info` | `1` | Request client information |
| `code_challenge` | `<BASE64_STRING>` | PKCE code challenge (SHA-256 of verifier) |
| `code_challenge_method` | `S256` | PKCE challenge method |
| `prompt` | `none` | Silent auth (if session exists) |
| `login_hint` | `<MSA_ENCODED_HINT>` | User's Microsoft Account hint (base64) |
| `X-AnchorMailbox` | `Oid:<USER_OID>@<TENANT_ID>` | Mailbox routing hint |
| `nonce` | `<UUID>` | Unique request identifier |
| `state` | `<BASE64_JSON>` | OAuth state parameter (includes return URL) |

### Key Observations

1. **PKCE Flow:** Uses Proof Key for Code Exchange (RFC 7636)
   - `code_challenge` = BASE64URL(SHA256(`code_verifier`))
   - Client must store `code_verifier` to exchange code for token

2. **Silent Authentication:** `prompt=none` attempts SSO without user interaction

3. **Sandbox Identifier:** `sandboxId=RETAIL` indicates production environment

### Response
- **Success:** Redirect to `redirect_uri` with authorization `code` in fragment
- **Failure:** Error parameters in fragment (`error`, `error_description`)

---

## Stage 2: Xbox Live User Authentication

### Endpoint
```
POST https://user.auth.xboxlive.com/user/authenticate
```

### Purpose
Exchange Microsoft authorization code for Xbox Live User Token.

### Request Headers (Required)

| Header | Value | Description |
|--------|-------|-------------|
| `Content-Type` | `application/json` | JSON request body |
| `x-xbl-contract-version` | `1` | API contract version |
| `ms-cv` | `<CORRELATION_VECTOR>` | Microsoft correlation vector for tracing |

### Request Body Structure

```json
{
  "Properties": {
    "AuthMethod": "RPS",
    "SiteName": "user.auth.xboxlive.com",
    "RpsTicket": "<MICROSOFT_OAUTH_TOKEN>"
  },
  "RelyingParty": "http://auth.xboxlive.com",
  "TokenType": "JWT"
}
```

**Field Descriptions:**
- `AuthMethod`: Always `"RPS"` (Robust Protected Session - Microsoft's auth system)
- `SiteName`: Service identifier
- `RpsTicket`: The OAuth access token from Stage 1 (prefixed with `d=` or `t=`)
- `RelyingParty`: Target service URI
- `TokenType`: Requested token format (always JWT)

### Response Structure

```json
{
  "IssueInstant": "2026-03-28T00:00:00.0000000Z",
  "NotAfter": "2026-03-28T16:00:00.0000000Z",
  "Token": "<ENCRYPTED_JWT_TOKEN>",
  "DisplayClaims": {
    "xui": [
      {
        "uhs": "<USER_HASH>",
        "xid": "<XUID>"
      }
    ]
  }
}
```

**Field Descriptions:**
- `IssueInstant`: Token creation timestamp (ISO 8601)
- `NotAfter`: Token expiration (16 hours from issue)
- `Token`: Encrypted JWT containing user claims
- `DisplayClaims.xui[0].xid`: Xbox User ID (XUID) - unique identifier
- `DisplayClaims.xui[0].uhs`: User Hash - derived identifier for auth chaining

### CORS Headers
- `Access-Control-Allow-Origin`: `https://www.xbox.com`
- Preflight required (OPTIONS request before POST)

---

## Stage 3: XSTS Authorization

### Endpoint
```
POST https://xsts.auth.xboxlive.com/xsts/authorize
```

### Purpose
Exchange Xbox Live User Token for XSTS Token authorized for specific service (Remote Play).

### Request Headers (Required)

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json` |
| `x-xbl-contract-version` | `1` |

### Request Body Structure

```json
{
  "Properties": {
    "SandboxId": "RETAIL",
    "UserTokens": [
      "<USER_TOKEN_FROM_STAGE_2>"
    ]
  },
  "RelyingParty": "rp://gswp.xboxlive.com/",
  "TokenType": "JWT"
}
```

**Critical Fields:**
- `SandboxId`: Environment identifier (`"RETAIL"` for production)
- `UserTokens`: Array containing the User Token from Stage 2
- `RelyingParty`: **Service-specific identifier**
  - For Remote Play: `"rp://gswp.xboxlive.com/"`
  - For Cloud Gaming: `"rp://cloudsdk.xboxlive.com/"` (DIFFERENT!)

### Response Structure

```json
{
  "IssueInstant": "2026-03-28T00:10:00.0000000Z",
  "NotAfter": "2026-03-28T16:10:00.0000000Z",
  "Token": "<ENCRYPTED_JWT_TOKEN>",
  "DisplayClaims": {
    "xui": [
      {
        "gtg": "<GAMERTAG>",
        "xid": "<XUID>",
        "uhs": "<USER_HASH>",
        "agg": "<AGE_GROUP>",
        "usr": "<USER_PRIVILEGES>",
        "utr": "<USER_TITLE_RESTRICTIONS>",
        "prv": "<PRIVILEGES_STRING>"
      }
    ]
  }
}
```

**Key Observations:**
1. XSTS token duration: 16 hours (same as User Token)
2. Token is **service-scoped** - different RelyingParty = different privileges
3. `DisplayClaims` include user profile and permission data

### Error Responses

| Error Code | Meaning | User Action Required |
|------------|---------|----------------------|
| `2148916233` | Account does not have Xbox profile | Create Xbox profile |
| `2148916235` | Account is child without parental consent | Parent must approve |
| `2148916236` | Account is from country where Xbox Live is unavailable | VPN or different account |
| `2148916238` | Account is child and attempting adult content | Parental controls block |

---

## Stage 4: Chat Authentication

### Endpoint
```
GET https://chat.xboxlive.com/users/xuid(<XUID>)/chat/auth
```

### Purpose
Obtain a short-lived AuthKey for WebSocket chat connection.

### Request Headers (Required)

| Header | Value |
|--------|-------|
| `Authorization` | `XBL3.0 x=<USER_HASH>;{XSTS_TOKEN}` |

**Authorization Header Format:**
- Prefix: `XBL3.0 x=`
- User Hash: From XSTS token `DisplayClaims.xui[0].uhs`
- Separator: `;`
- XSTS Token: Full token from Stage 3

### Response Structure

```json
{
  "authKey": "<UUID_FORMAT_GUID>"
}
```

**AuthKey Characteristics:**
- Format: UUID (e.g., `f0ee821f-6992-4ab3-b59d-4bbf5e7b6781`)
- Lifetime: Short-lived (exact duration unknown, estimated 5-10 minutes)
- Single-use: Each WebSocket connection requires new AuthKey
- Purpose: Authorize WebSocket connection to chat service

### CORS Requirements
- **CRITICAL:** This endpoint is CORS-blocked from browser `play.xbox.com`
- Error: `Access-Control-Allow-Origin` header not present
- **Solution:** Must be proxied through backend/main process (Electron)

---

## Stage 5: WebSocket Connections

Remote Play uses TWO separate WebSocket connections:

### 5A. Chat WebSocket

#### Endpoint
```
wss://chat.xboxlive.com/users/xuid(<XUID>)/chat/connect?AuthKey=<AUTH_KEY_FROM_STAGE_4>
```

#### Purpose
Real-time chat messaging and notifications.

#### Connection Requirements
- `AuthKey` query parameter (from Stage 4)
- Sec-WebSocket-Protocol: (none specified)
- Origin: `https://www.xbox.com`

#### Message Format
Unknown (not captured in HAR). Likely JSON-based protocol.

---

### 5B. Real-Time Activities (RTA) WebSocket

#### Endpoint
```
wss://rta.xboxlive.com/connect?nonce=<NONCE>
```

#### Purpose
Real-time presence, activity feeds, and system notifications.

#### Connection Requirements
- `nonce` query parameter: Base64-encoded cryptographic nonce
  - Format: 43 characters, URL-safe base64
  - Example: `lkoQjDa6BVJ04N1x2XVsju4xCRhlcHnQdKSfYbORY-k.`
- Sec-WebSocket-Protocol: (none specified)

#### Observed Behavior
- Multiple concurrent connections (up to 4 observed)
- Each connection has unique nonce
- Purpose: Load balancing or redundancy?

---

## Token Lifecycle Management

### Token Expiration Timeline

```
Time 0:    OAuth Authorization
Time +0s:  User Token issued (expires T+16h)
Time +5s:  XSTS Token issued (expires T+16h)
Time +10s: Chat AuthKey issued (expires T+~5min)
Time +15s: WebSocket connected

Time +5min:  Chat AuthKey expires → reconnect with new key
Time +16h:   User/XSTS tokens expire → full re-authentication required
```

### Refresh Strategy

**Option 1: Proactive Refresh**
- Refresh XSTS token at T+15h (1 hour before expiry)
- Refresh Chat AuthKey every 4 minutes

**Option 2: Reactive Refresh**
- Wait for WebSocket disconnect
- Check token expiration
- Refresh as needed

**Recommendation:** Proactive refresh to avoid connection drops during gameplay.

---

## Security Considerations

### 1. PKCE Implementation
- Must generate cryptographically secure `code_verifier` (43-128 chars)
- Hash with SHA-256 before sending challenge
- Store verifier securely until token exchange

### 2. Token Storage
- User Token: Memory only (16h lifetime)
- XSTS Token: Memory only (16h lifetime)
- Chat AuthKey: Memory only (5min lifetime)
- **NEVER** store tokens in localStorage (XSS risk)
- Consider encrypted storage for offline capability

### 3. CORS Proxy Requirement
- Chat auth endpoint MUST be proxied
- Proxy should validate origin
- Log all proxy requests for security audit

### 4. User Hash Sensitivity
- User Hash (`uhs`) is personally identifiable
- Required for auth chaining
- Do NOT log or expose in client UI

---

## Implementation Checklist for Team 2

- [ ] OAuth 2.0 client with PKCE support
- [ ] HTTP client with CORS proxy capability
- [ ] User Token acquisition flow
- [ ] XSTS Token acquisition flow (with correct RelyingParty)
- [ ] Chat AuthKey acquisition (via proxy)
- [ ] WebSocket client for Chat
- [ ] WebSocket client for RTA
- [ ] Token refresh logic (proactive)
- [ ] Error handling for all auth stages
- [ ] Secure token storage (memory-only)

---

## Differences: Remote Play vs Cloud Gaming

| Aspect | Remote Play | Cloud Gaming |
|--------|-------------|--------------|
| **XSTS RelyingParty** | `rp://gswp.xboxlive.com/` | `rp://cloudsdk.xboxlive.com/` |
| **Target** | User's console | Microsoft servers |
| **Network** | LAN or WAN P2P | Microsoft data centers |
| **Subscription** | Xbox Game Pass Ultimate (optional) | Xbox Game Pass Ultimate (required) |
| **Quality** | Based on console/network | Based on subscription tier |

**CRITICAL:** Using wrong RelyingParty will result in authorization failure or incorrect privileges.

---

## Open Questions for Team 2 Review

1. What is exact lifetime of Chat AuthKey? (not documented)
2. Are there rate limits on auth endpoints?
3. What is RTA WebSocket message protocol?
4. How to handle concurrent auth flows (multiple devices)?
5. What happens if user changes password mid-session?

---

## Appendix: Correlation Vectors

Microsoft uses **Correlation Vectors** (`ms-cv` header) for distributed tracing.

**Format:** `<BASE_VECTOR>.<INCREMENT>`
- Example: `jtfGRsyFFDG76LBgAQeWqz.15`
- Increments with each hop in request chain
- Used for debugging across services

**Implementation:** 
- Generate base vector (random base64 string)
- Increment counter for each outbound request
- Include in all Xbox Live API calls

---

**End of Document**

**Next Steps:**
1. Team 2 reviews for clarity and completeness
2. Team 2 asks clarifying questions (NO access to HAR files)
3. Team 1 revises documentation as needed
4. Team 2 implements cleanroom code based solely on this spec
