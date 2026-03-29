# Critical Data Gaps Analysis - Team 1 Report

**Date:** 2026-03-28  
**Analyst:** Team 1 (Dirty Room)  
**Purpose:** Identify missing data that Team 2 will need for feature-complete implementation

---

## Executive Summary

After analyzing `SENSITIVELONGRemoteplay.har`, I've identified **7 critical gaps** that may block Team 2's implementation. While the HAR file contains excellent HTTP/WebSocket data, certain runtime behaviors and binary protocols are **not captured** by HAR format.

### Current Coverage: ✅ Good
- OAuth 2.0 authentication flow
- Xbox Live token chain
- WebRTC SDP/ICE exchange (HTTP signaling)
- WebSocket message formats (Chat/RTA)
- Session creation API
- Keepalive mechanism

### Critical Gaps: ⚠️ Need Additional Capture
See sections below for detailed breakdowns.

---

## Gap #1: Console Discovery & Listing API 🔴 CRITICAL

### What's Missing
The HAR shows the user navigating to `/play/consoles/launch/F4000F644EF581F9` (direct console launch), but **does NOT show**:
- How to get the list of user's consoles
- How to determine console status (Online, Standby, Off, Not Available)
- Console metadata (Name, Xbox One vs Series X/S, capabilities)
- Console IDs for the user's account

### Why Team 2 Needs This
**Cannot implement console selection screen without this data.** Users must be able to:
1. See all their registered consoles
2. See which consoles are currently available
3. Select a console to connect to

### What URL Shows in HAR
```
https://www.xbox.com/en-US/play/consoles
```
This page loads, but the HAR doesn't capture the **API call** that fetches console data. Likely an XHR/Fetch request from JavaScript.

### Recommended Capture Method
**Option A:** Browser DevTools Network tab while on `/play/consoles` page
- Look for XHR/Fetch requests to endpoints like:
  - `*.xboxlive.com/users/xuid(<XUID>)/consoles`
  - `*.xboxlive.com/devices`
  - `smartglass.xboxlive.com` (SmartGlass API)
  
**Option B:** Wireshark/tcpdump packet capture
- Capture all HTTPS traffic while navigating to console list
- May need to decrypt TLS (browser SSLKEYLOGFILE)

**What to capture:**
1. Navigate to https://www.xbox.com/en-US/play/consoles
2. Wait for console list to load
3. Save complete HAR file OR
4. Copy/paste the API request/response from DevTools

---

## Gap #2: Console Power Management 🔴 CRITICAL

### What's Missing
The HAR shows connecting to console `F4000F644EF581F9`, but assumes **console is already powered on**.

**Missing scenarios:**
1. **Console in Standby** - How to wake it remotely (Instant-On feature)
2. **Console Off** - Does the API return an error? Can it be powered on remotely?
3. **Wake-on-LAN** - Does Remote Play use WoL packets?
4. **Power commands** - Can the client send shutdown/restart commands?

### Why Team 2 Needs This
Xbox consoles support "Instant-On" mode for remote wake. **Team 2 needs to know:**
- What API endpoint wakes a console?
- What happens if console is fully powered off?
- Is there a timeout/polling mechanism while console boots?

### Recommended Capture Method
**Scenario Test:**
1. Put Xbox in Standby (Instant-On enabled)
2. Navigate to `play.xbox.com` and launch that console
3. Capture HAR file showing the wake request
4. Look for API calls before `/sessions/home/play` request

**Expected endpoints:**
- Possible SmartGlass API call
- Possible `/consoles/<ID>/power` or `/consoles/<ID>/wake`
- May use HTTPS or UDP (SmartGlass protocol)

---

## Gap #3: WebRTC DataChannel Protocols 🔴 CRITICAL

### What's Missing
HAR files capture **SDP/ICE signaling** but **NOT** the binary messages sent over WebRTC DataChannels after connection establishes.

**Missing protocols:**
- **Input Channel** - How gamepad/keyboard/mouse input is encoded and sent
- **Control Channel** - Power, volume, screenshot, menu button commands
- **Feedback Channel** - Vibration/rumble data from console to client
- **Chat Integration** - Voice chat data (if separate from media tracks)

### Why Team 2 Needs This
**Input is fundamental to streaming.** Without knowing the binary format:
- Cannot send button presses
- Cannot send analog stick positions
- Cannot send mouse movement (for games that support it)
- Cannot trigger rumble feedback

### What IS in the HAR (✅ Good)
```json
// Session creation request shows settings:
{
  "settings": {
    "nanoVersion": "V3;WebrtcTransport.dll",
    "enableTextToSpeech": false,
    "useIceConnection": false,
    "sdkType": "web",
    "osName": "linux"
  }
}
```

This tells us:
- Client identifies as "V3" nano version
- Uses `WebrtcTransport.dll` protocol
- NOT using ICE connection mode (using SDP long-polling instead)

### Recommended Capture Method
**⚠️ This requires tools beyond HAR files**

**Option A: Chrome WebRTC Internals** (Easiest)
1. Navigate to `chrome://webrtc-internals` in separate tab
2. Launch Xbox Remote Play session
3. WebRTC Internals shows:
   - All DataChannel messages (if text-based)
   - Stats about channels (but not binary payloads)
   - May be partially useful

**Option B: Wireshark with DTLS decryption** (Advanced)
1. Export browser's SSLKEYLOGFILE
2. Capture packets during streaming session
3. Decrypt DTLS (WebRTC encryption)
4. Analyze DataChannel payloads
5. **⚠️ Complex, but gives complete binary protocol**

**Option C: Reverse-engineer JavaScript** (Alternative)
1. Extract `web-rtc-stream.*.chunk.js` from HAR file
2. De-minify/beautify JavaScript
3. Search for DataChannel send/receive handlers
4. Document the encoding logic (CLEANROOM RULES APPLY)
5. Write functional spec for Team 2

**Best approach:** Combination of Option A + C

---

## Gap #4: Input Protocol Specification 🔴 CRITICAL

### What's Missing
Even if we capture DataChannel messages (Gap #3), we need to **document the protocol structure:**

**Missing specifications:**
- Button mapping (A, B, X, Y, LB, RB, LT, RT, etc.)
- Analog stick encoding (8-bit? 16-bit? Normalized float?)
- Trigger encoding (0-255? 0-1.0 float?)
- D-pad representation (buttons vs. axis?)
- Timestamp format for input events
- Batching/buffering strategy
- Special buttons (Xbox button, Menu, View)

### Why Team 2 Needs This
**Cannot implement gamepad support without protocol spec.** Even with DataChannel captures, Team 2 needs clean documentation of:
```
// Example of what Team 2 needs:
{
  "timestamp": <uint64_ms>,
  "buttons": <16-bit_bitmask>,
  "leftStick": {"x": <float>, "y": <float>},
  "rightStick": {"x": <float>, "y": <float>},
  "leftTrigger": <uint8>,
  "rightTrigger": <uint8>
}
```

### Recommended Capture Method
**Best approach: JavaScript analysis**
1. Extract input handling modules from HAR file JavaScript
2. Find gamepad polling logic
3. Document message structure in cleanroom format
4. Provide example messages with sanitized data

**Alternative: xbox-xcloud-player comparison**
- The open-source `xbox-xcloud-player` has input implementation
- Can compare against Microsoft's approach
- Document differences/improvements

---

## Gap #5: Video/Audio Codec Negotiation Details 🟡 MEDIUM PRIORITY

### What's Captured (✅ Good)
The HAR file contains SDP offers/answers showing:
- Supported video codecs (H.264, VP8, VP9, potentially HEVC)
- Supported audio codecs (Opus, PCMU, PCMA, G722)
- RTP payload types
- Codec parameters (profile-level-id, etc.)

### What's Missing
**Runtime negotiation results:**
- Which codec was actually selected?
- What resolution was negotiated?
- What frame rate?
- What bitrate?
- Does Microsoft prefer HEVC when available?
- Fallback behavior if HEVC not supported

### Why Team 2 Needs This
For implementing **codec preference logic** and **quality settings**:
- Should we prefer H.265 over H.264?
- What bitrate ranges are acceptable?
- How does quality adapt to network conditions?

### Recommended Capture Method
**Chrome WebRTC Internals:**
1. While streaming, check `chrome://webrtc-internals`
2. Look for "codec" field in stats
3. Document: actualCodec, resolution, fps, bitrate
4. Test different network conditions (throttle bandwidth)
5. Document how quality adapts

**Alternative: Extract from JavaScript**
- Search for codec preference logic in `web-rtc-stream.*.chunk.js`
- Document Microsoft's preferred codec order

---

## Gap #6: Error Codes & Recovery Procedures 🟡 MEDIUM PRIORITY

### What's Missing
The HAR file shows **successful** connection flow. Missing:

**Error scenarios:**
- What if authentication fails? (401, 403 errors)
- What if console is offline? (Connection timeout)
- What if session creation fails? (Server errors)
- What if WebRTC connection fails? (ICE failure, DTLS handshake failure)
- What if stream disconnects mid-session? (Network drop)

**Error codes:**
- HTTP status codes and their meanings
- JSON error responses from Xbox Live APIs
- WebSocket close codes
- Session state error values

### Why Team 2 Needs This
**Robust error handling and user-friendly messages** require knowing:
- All possible error states
- Error messages to show users
- Automatic retry logic
- When to give up vs. keep retrying

### Recommended Capture Method
**Intentional failure testing:**
1. **Test 1:** Start stream, then turn off Xbox console mid-stream → Capture disconnect behavior
2. **Test 2:** Use expired/invalid token → Capture 401 error response
3. **Test 3:** Try to connect to offline console → Capture timeout behavior
4. **Test 4:** Disconnect network mid-stream → Capture reconnection attempts
5. **Test 5:** Try to start second stream while one is active → Capture session conflict error

---

## Gap #7: Console Capabilities Discovery 🟢 LOW PRIORITY

### What's Missing
Different Xbox consoles have different capabilities:

**Xbox One vs. Xbox Series X/S differences:**
- Max resolution (1080p vs. 4K)
- Max frame rate (60fps vs. 120fps)
- HEVC support (Series X/S has hardware encoder)
- HDR support
- Quick Resume support

**Missing data:**
- How does client detect console model?
- How does client query console capabilities?
- Does server send capabilities in `/configuration` endpoint?

### Why Team 2 Needs This (Eventually)
For **optimal quality settings** and **UI features**:
- Show "4K Available" badge for Series X/S
- Auto-select HEVC for Series X/S
- Don't offer 4K option for Xbox One

### Recommended Capture Method
**Check session configuration response:**
```bash
# Look for this in HAR:
GET /v5/sessions/home/<SESSION_ID>/configuration
```

Response likely contains console model/capabilities. Document structure.

---

## Gap #8: Multi-Session Behavior 🟢 LOW PRIORITY

### What's Missing
- Can user have multiple streaming sessions simultaneously?
- Can user switch between consoles?
- What happens if user starts stream on second device?
- Does first session terminate or show error?

### Recommended Capture Method
**Multi-device test:**
1. Start stream on Device A (capture HAR)
2. While streaming, start stream on Device B (capture HAR)
3. Document behavior (error? force-disconnect?)

---

## Summary: What to Capture Next

### 🔴 MUST HAVE (Blockers)
1. **Console Discovery API** - Navigate to `/play/consoles`, capture API call
2. **Console Wake/Power** - Test wake from standby, capture API
3. **WebRTC DataChannel Messages** - Use Chrome WebRTC Internals + JS analysis
4. **Input Protocol** - Extract from JavaScript, document format

### 🟡 SHOULD HAVE (Important)
5. **Codec Negotiation Results** - Chrome WebRTC Internals during stream
6. **Error Scenarios** - Intentional failure testing (5 scenarios above)

### 🟢 NICE TO HAVE (Enhancement)
7. **Console Capabilities** - Check `/configuration` response
8. **Multi-Session** - Test concurrent streams

---

## Recommended Capture Session Plan

### Session 1: Console Discovery (30 minutes)
1. Open DevTools Network tab
2. Navigate to https://www.xbox.com/en-US/play/consoles
3. Filter XHR/Fetch requests
4. Find console list API call
5. Save HAR + copy/paste API response
6. **Goal:** Document console list endpoint and response format

### Session 2: Power Management (30 minutes)
1. Put Xbox in Standby mode
2. Open DevTools Network tab
3. Launch Remote Play session
4. Capture wake request
5. Save HAR file
6. **Goal:** Document console wake endpoint

### Session 3: Live Streaming Analysis (60 minutes)
1. Open `chrome://webrtc-internals` in second tab
2. Open DevTools Console in play.xbox.com tab
3. Start Remote Play stream
4. Press gamepad buttons while monitoring console
5. Look for DataChannel send events
6. Save WebRTC Internals dump
7. Extract JavaScript modules from HAR
8. **Goal:** Capture DataChannel activity + JS source for analysis

### Session 4: Error Testing (45 minutes)
1. Test 5 failure scenarios (see Gap #6)
2. Capture HAR for each scenario
3. Document error responses
4. **Goal:** Complete error handling documentation

---

## Notes for Team 1 (Internal)

### Data Sanitization Checklist
When documenting captured data, **ALWAYS redact:**
- ✅ XUID: `2535421847897820` → `<XUID>`
- ✅ Console IDs: `F4000F644EF581F9` → `<CONSOLE_ID>`
- ✅ OAuth tokens (JWT format)
- ✅ Session IDs, AuthKeys, Nonces
- ✅ IP addresses
- ✅ Gamertag (if appears in responses)
- ✅ MAC addresses (if in WoL packets)

### Cleanroom Compliance
When analyzing JavaScript:
- ❌ Do NOT copy code verbatim
- ❌ Do NOT copy variable names
- ❌ Do NOT copy algorithm implementations
- ✅ DO document: "Function X takes input Y and produces output Z"
- ✅ DO document: "Protocol uses message format: {field1, field2, field3}"
- ✅ DO provide example messages with sanitized data

---

## Status

**Current HAR Analysis:** 60% complete  
**Critical Gaps Identified:** 8  
**High Priority Gaps:** 4  
**Recommended Next Action:** Execute Capture Session 1 (Console Discovery)

**Estimated Time to Fill Gaps:** 3-4 hours of focused capture + analysis

Once gaps filled → Team 1 can produce **complete functional specifications** → Team 2 can implement **feature-complete client** without seeing Microsoft's code.

---

**END OF GAP ANALYSIS**
