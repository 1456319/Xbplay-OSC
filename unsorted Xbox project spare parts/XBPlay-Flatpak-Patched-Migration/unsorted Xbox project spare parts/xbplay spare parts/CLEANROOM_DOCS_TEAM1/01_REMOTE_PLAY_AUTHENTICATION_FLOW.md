# Remote Play Authentication Flow Specification

**Version:** 1.0  
**Date:** 2026-03-27  

---

## 1. Overview

Xbox Remote Play uses a multi-stage authentication process involving Microsoft Account (MSA) and Xbox Live (XBL) services. The final goal is to obtain a `SessionToken` used for WebRTC signaling.

---

## 2. Authentication Steps

### Stage 1: MSA Authorization
- **Endpoint:** `https://login.live.com/oauth20_authorize.srf`
- **Input:** Client ID, Redirect URI, Scopes.
- **Output:** `AuthCode`

### Stage 2: XBL User Token
- **Endpoint:** `https://user.auth.xboxlive.com/user/authenticate`
- **Input:** MSA Token
- **Output:** `UserToken`, `XHS` (User Hash)

### Stage 3: XSTS Token
- **Endpoint:** `https://xsts.auth.xboxlive.com/xsts/authorize`
- **Input:** UserToken
- **Relying Party:** `http://gssv.xboxlive.com/`
- **Output:** `XSTSToken`

### Stage 4: Session Token
- **Endpoint:** `https://v5.core.gssv-play-prod.xboxlive.com/v5/sessions/home/play`
- **Input:** XSTSToken, ConsoleID
- **Output:** `SessionToken`, `SignalingURL`

---

## 3. Token Format

Tokens are usually JWT or proprietary binary blobs passed in the `Authorization` header:
`Authorization: XBL3.0 x=<UserHash>;<Token>`

---

## 4. Troubleshooting

- **Error 401:** Token expired or XSTS relaying party mismatch.
- **Error 403:** Console not registered to user.
