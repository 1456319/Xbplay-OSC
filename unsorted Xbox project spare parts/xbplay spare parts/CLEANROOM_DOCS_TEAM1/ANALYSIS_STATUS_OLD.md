# Cleanroom Documentation - Analysis Status

**Date:** March 28, 2026  
**Analyst:** Team 1 (Dirty Room)  
**Source:** HAR files from /home/deck/dump/Remoteplay/

---

## Files Analyzed

✅ **SENSITIVELONGRemoteplay.har** (79 MB, 2217 entries)
- Primary source for Remote Play protocol
- Contains complete auth flow
- Contains WebRTC session establishment
- Contains console discovery and streaming

⏳ **LONGRemoteplay.har** (78 MB) - Not yet analyzed  
⏳ **Remoteplay.har** (36 MB) - Not yet analyzed  
⏳ **Cloud Play HAR files** - Not yet analyzed

---

## Documents Created

### 01_REMOTE_PLAY_AUTHENTICATION_FLOW.md ✅
**Status:** COMPLETE - Ready for Team 2 review  
**Coverage:**
- OAuth 2.0 with PKCE flow
- Xbox Live User Authentication
- XSTS Authorization
- Chat Authentication (CORS issue documented)
- WebSocket connections (Chat + RTA)
- Token lifecycle management
- Security considerations

**Personal Data Sanitization:** ✅ Complete
- All tokens redacted
- All XUIDs replaced with `<XUID>` placeholder
- All session IDs sanitized

---

## Key Discoveries

### Critical Endpoints Identified

**Authentication:**
- `login.microsoftonline.com` - OAuth 2.0
- `user.auth.xboxlive.com` - User Token
- `xsts.auth.xboxlive.com` - XSTS Token
- `chat.xboxlive.com` - Chat AuthKey (CORS BLOCKED)

**Streaming Session:**
- `wus2.core.gssv-play-prodxhome.xboxlive.com` - Game Streaming Service (Home)
  - `/v5/sessions/home/play` - Create session
  - `/v5/sessions/home/<SESSION_ID>/configuration` - Get config
  - `/v5/sessions/home/<SESSION_ID>/sdp` - SDP offer/answer
  - `/v5/sessions/home/<SESSION_ID>/ice` - ICE candidates
  - `/v5/sessions/home/<SESSION_ID>/keepalive` - Keep session alive
  - `/v5/sessions/home/<SESSION_ID>/state` - Session state

**WebSockets:**
- `wss://chat.xboxlive.com` - Chat messaging
- `wss://rta.xboxlive.com` - Real-time activities

### Console ID Format
- Example: `F4000F644EF581F9`
- 16 hexadecimal characters
- Used in launch URLs: `/play/consoles/launch/<CONSOLE_ID>`

### Remote Play vs Cloud Play
- **Remote Play:** `gssv-play-prodxhome.xboxlive.com` (prod**home**)
- **Cloud Gaming:** Different domain (to be analyzed)
- **XSTS RelyingParty:** Different for each service

---

## Next Documents to Create

### 02_CONSOLE_DISCOVERY_API.md ⏳
**Purpose:** How to discover user's consoles  
**Content:**
- Console list endpoint
- Console status (Online, Standby, Off)
- Console metadata (Name, Type, Capabilities)
- Wake-on-LAN functionality?

### 03_STREAMING_SESSION_PROTOCOL.md ⏳
**Purpose:** WebRTC session establishment  
**Content:**
- Session creation flow
- SDP offer/answer exchange
- ICE candidate trickle
- Keepalive mechanism
- Session state machine

### 04_WEBRTC_DATA_CHANNELS.md ⏳
**Purpose:** DataChannels for input/control  
**Content:**
- Input channel (gamepad, keyboard, mouse)
- Control channel (power, volume, etc.)
- Chat channel integration
- Message formats

### 05_INPUT_PROTOCOL.md ⏳
**Purpose:** How to send controller/keyboard/mouse input  
**Content:**
- Gamepad button mapping
- Analog stick encoding
- Mouse input (absolute vs relative)
- Keyboard input
- Vibration feedback

### 06_VIDEO_AUDIO_CODECS.md ⏳
**Purpose:** Media capabilities and negotiation  
**Content:**
- Supported codecs (H.264, H.265/HEVC)
- Resolution capabilities
- Frame rate options
- Audio codec (Opus?)
- Quality negotiation

### 07_ERROR_HANDLING.md ⏳
**Purpose:** Error codes and recovery  
**Content:**
- Authentication errors
- Session errors
- Network errors
- Recovery procedures

### 08_COMPARISON_CLOUD_VS_REMOTE.md ⏳
**Purpose:** Differences between services  
**Content:**
- Endpoint differences
- Authentication differences
- Capability differences

---

## Personal Data Found (DO NOT INCLUDE IN DOCS)

⚠️ **SENSITIVE - Team 1 eyes only**

- XUID: `2535421847897820` (user's Xbox ID)
- Multiple OAuth tokens (JWT format)
- Chat AuthKeys (UUID format)
- Session IDs
- Console IDs
- IP addresses
- Correlation vectors
- Browser fingerprints

**Status:** All sanitized in documentation

---

## JavaScript Modules Identified

From `assets.play.xbox.com/playxbox/static/js/`:

**Key Modules:**
- `web-rtc-stream.*.chunk.js` - WebRTC stream handling
- `game-stream.*.chunk.js` - Game streaming UI/logic
- `client.*.js` - Main client bundle
- `stream-page.*.chunk.js` - Stream page components

**Note:** These contain Microsoft's implementation. We document WHAT they do, not HOW. No code copying.

---

## Questions for Team 2 Review

1. Is authentication flow documentation clear enough to implement?
2. Do you need more detail on any specific endpoint?
3. Are the diagrams helpful or should we add more?
4. Any ambiguities in the token lifecycle?

---

## Next Steps

1. ✅ Complete authentication documentation
2. ✅ Identify critical data gaps (Gap Analysis complete)
3. ⏳ **USER ACTION REQUIRED** - Capture missing data (see QUICK_CAPTURE_GUIDE.txt)
4. ⏳ Analyze console discovery endpoints (after Capture #1)
5. ⏳ Analyze WebRTC DataChannel protocol (after Capture #3)
6. ⏳ Document input protocol (after Capture #4)
7. ⏳ Document error handling (after Capture #6)
8. ⏳ Compare with Cloud Play HAR files
9. ⏳ Team 2 review meeting
10. ⏳ Revise documentation based on feedback

---

## Current Blockers

🔴 **CRITICAL:** Need additional captures to proceed
- Console Discovery API (10 min capture)
- Console Power/Wake API (10 min capture)
- WebRTC DataChannel messages (30 min capture)
- Input protocol details (30 min analysis)

**See:** `QUICK_CAPTURE_GUIDE.txt` for step-by-step instructions

---

**Estimated Completion:** 40% of Remote Play protocol documented  
**Remaining Work:** Console discovery, WebRTC DataChannels, input protocol, error handling, codec details

**Once gaps filled:** Can complete 90%+ of documentation for Team 2

