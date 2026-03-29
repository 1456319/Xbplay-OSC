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
2. Look for "codec"
