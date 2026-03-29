# Xbox Remote Play WebRTC Session Protocol

**Document Version:** 1.0  
**Date:** 2026-03-29  
**Team:** Team 1 (Dirty Room)  
**Status:** Complete - Ready for Team 2 Implementation

---

## Executive Summary

This document specifies the WebRTC signaling, connection establishment, and session management protocol used by Xbox Remote Play. The protocol uses standard WebRTC with custom Microsoft endpoints for session creation, SDP offer/answer exchange, and ICE candidate gathering.

**Key characteristics:**
- REST API for session creation
- WebSocket for SDP/ICE signaling
- Peer-to-peer connection when on same LAN
- STUN/TURN fallback for NAT traversal
- Three DataChannels: input, control, message

---

## Architecture Overview

```
┌─────────────┐                                  ┌──────────────┐
│   Client    │                                  │     Xbox     │
│  (Browser)  │                                  │   Console    │
└──────┬──────┘                                  └──────┬───────┘
       │                                                │
       │ 1. Create Session (HTTP POST)                 │
       ├──────────────────────────────────────────────>│
       │                                                │
       │ 2. Session Details (JSON response)            │
       │<──────────────────────────────────────────────┤
       │                                                │
       │ 3. Connect WebSocket (wss://)                 │
       ├───────────────────────────────────────────────│
       │                                                │
       │ 4. Create Offer (SDP)                         │
       ├──────────────────────────────────────────────>│
       │                                                │
       │ 5. Send Answer (SDP)                          │
       │<──────────────────────────────────────────────┤
       │                                                │
       │ 6. Exchange ICE Candidates                    │
       ├<─────────────────────────────────────────────>│
       │                                                │
       │ 7. WebRTC Connection (P2P UDP)                │
       ├═══════════════════════════════════════════════│
       │   Video/Audio + DataChannels                  │
       │                                                │
```

---

## Session Creation

### Endpoint

**URL:** `https://wus2.core.gssv-play-prodxhome.xboxlive.com/v5/sessions/home/<CONSOLE_ID>`

**Method:** POST  
**Authentication:** Bearer token (XSTS token)  
**Content-Type:** `application/json`

### Request Headers

```
Authorization: XBL3.0 x=<USER_HASH>;<XSTS_TOKEN>
X-Correlation-Id: <GUID>
Content-Type: application/json
Accept: application/json
```

**Note:** See `01_REMOTE_PLAY_AUTHENTICATION_FLOW.md` for token acquisition.

### Request Body

```json
{
  "titleId": "",
  "systemUpdateGroup": "",
  "settings": {
    "nanoVersion": "V3;WebrtcTransport.dll",
    "enableTextInput": true,
    "uiVersion": 1500
  },
  "streaming": {
    "bitrate": {
      "client": {
        "maxBitrate": 10000,
        "minBitrate": 1000,
        "startBitrate": 5000
      }
    }
  }
}
```

**Field descriptions:**

- `titleId`: Empty for console streaming (dashboard); set to title ID for specific game
- `systemUpdateGroup`: Empty (reserved)
- `nanoVersion`: Client version identifier
- `enableTextInput`: Enable virtual keyboard support
- `uiVersion`: UI version for compatibility
- `maxBitrate`: Maximum video bitrate in kbps (10 Mbps)
- `minBitrate`: Minimum video bitrate in kbps (1 Mbps)
- `startBitrate`: Initial video bitrate in kbps (5 Mbps)

### Response (Success)

**Status:** 201 Created

```json
{
  "sessionId": "<SESSION_GUID>",
  "sessionPath": "/v5/sessions/home/<CONSOLE_ID>/<SESSION_GUID>",
  "state": "Created",
  "streamPath": "/v5/sessions/home/<CONSOLE_ID>/<SESSION_GUID>/stream",
  "serverId": "<SERVER_ID>",
  "consoleId": "<CONSOLE_ID>",
  "widevine": {
    "certificateUrl": "https://..."
  }
}
```

**Key fields:**
- `sessionId`: Unique session identifier (use in subsequent requests)
- `streamPath`: Path for WebSocket connection
- `state`: Session state (`Created`, `Ready`, `Streaming`, `Ended`)

### Response (Error)

**Status:** 4xx/5xx

```json
{
  "errorCode": "SessionLimitExceeded",
  "errorMessage": "Maximum concurrent sessions reached"
}
```

**Common error codes:**
- `SessionLimitExceeded`: Too many active sessions
- `ConsoleNotAvailable`: Console offline or unreachable
- `InvalidToken`: Authentication token expired/invalid

---

## WebRTC Signaling

### WebSocket Connection

**URL:** `wss://wus2.core.gssv-play-prodxhome.xboxlive.com/v5/sessions/home/<CONSOLE_ID>/<SESSION_ID>/stream`

**Protocol:** WebSocket  
**Subprotocol:** None (standard WebSocket)

**Connection headers:**
```
Authorization: XBL3.0 x=<USER_HASH>;<XSTS_TOKEN>
X-Correlation-Id: <GUID>
```

### Message Format

All messages are JSON objects with a `type` field:

```json
{
  "type": "offer|answer|candidate|...",
  "data": { ... }
}
```

---

## SDP Offer/Answer Exchange

### Client Offer

**Direction:** Client → Xbox  
**Timing:** Immediately after WebSocket connection

**Message structure:**
```json
{
  "type": "offer",
  "sdp": "<SDP_STRING>"
}
```

**SDP Example (abbreviated):**
```
v=0
o=- <SESSION_ID> 2 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0 1 2
a=msid-semantic: WMS

m=video 9 UDP/TLS/RTP/SAVPF 96 97 98
c=IN IP4 0.0.0.0
a=rtcp:9 IN IP4 0.0.0.0
a=ice-ufrag:<UFRAG>
a=ice-pwd:<PWD>
a=fingerprint:sha-256 <FINGERPRINT>
a=setup:actpass
a=mid:0
a=sendrecv
a=rtcp-mux
a=rtcp-rsize
a=rtpmap:96 H264/90000
a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f
a=rtpmap:97 rtx/90000
a=fmtp:97 apt=96

m=audio 9 UDP/TLS/RTP/SAVPF 111 110
c=IN IP4 0.0.0.0
a=rtcp:9 IN IP4 0.0.0.0
a=ice-ufrag:<UFRAG>
a=ice-pwd:<PWD>
a=fingerprint:sha-256 <FINGERPRINT>
a=setup:actpass
a=mid:1
a=sendrecv
a=rtcp-mux
a=rtpmap:111 opus/48000/2
a=fmtp:111 minptime=10;useinbandfec=1

m=application 9 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 0.0.0.0
a=ice-ufrag:<UFRAG>
a=ice-pwd:<PWD>
a=fingerprint:sha-256 <FINGERPRINT>
a=setup:actpass
a=mid:2
a=sctp-port:5000
a=max-message-size:262144
```

**Key SDP attributes:**

**Video (m=video):**
- Codec: H.264 (payload type 96)
- Profile: Baseline (42001f)
- Packetization: Mode 1 (non-interleaved)
- RTX: Enabled (payload type 97)

**Audio (m=audio):**
- Codec: Opus (payload type 111)
- Sample rate: 48 kHz
- Channels: 2 (stereo)
- Min packet time: 10ms
- FEC: Enabled (useinbandfec=1)

**DataChannels (m=application):**
- Protocol: SCTP over DTLS
- Port: 5000
- Max message size: 256 KB

### Xbox Answer

**Direction:** Xbox → Client  
**Timing:** ~500ms after receiving offer

**Message structure:**
```json
{
  "type": "answer",
  "sdp": "<SDP_STRING>"
}
```

**SDP structure:** Similar to offer, but with `a=setup:active` instead of `actpass`

---

## ICE Candidate Exchange

### ICE Gathering

Both client and Xbox gather ICE candidates for connection establishment.

**Candidate types:**
1. **host** - Local network interface
2. **srflx** - Server-reflexive (via STUN)
3. **relay** - Relayed (via TURN)

### Client → Xbox Candidates

**Message structure:**
```json
{
  "type": "candidate",
  "candidate": "candidate:<FOUNDATION> 1 udp <PRIORITY> <IP> <PORT> typ <TYPE> raddr <RADDR> rport <RPORT>",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

**Example candidates:**

**Host (local interface):**
```
candidate:1234567890 1 udp 2130706431 192.168.1.100 54321 typ host
```

**Server-reflexive (via STUN):**
```
candidate:1234567891 1 udp 1694498815 172.59.117.84 54321 typ srflx raddr 192.168.1.100 rport 54321
```

### Xbox → Client Candidates

**Same message format**, Xbox sends its candidates:

**Host (Xbox local IP):**
```
candidate:1234567892 1 udp 2130706431 192.168.1.14 9002 typ host
```

**Server-reflexive (via STUN):**
```
candidate:1234567893 1 udp 1694498815 203.0.113.5 9002 typ srflx raddr 192.168.1.14 rport 9002
```

### STUN/TURN Servers

**STUN Server:**
```
stun:relay.communication.microsoft.com:3478
```

**TURN Servers (if P2P fails):**
```
turn:relay.communication.microsoft.com:3478?transport=udp
turn:relay.communication.microsoft.com:3478?transport=tcp
```

**TURN Credentials:**
- Provided in session creation response
- Temporary credentials (expire after session)

---

## Connection Establishment

### ICE Pairing

WebRTC engine selects best candidate pair based on:
1. Connectivity (can route packets?)
2. Latency (lowest RTT)
3. Candidate type priority (host > srflx > relay)

**Preferred path (same LAN):**
```
Client local → Xbox host
Client: 192.168.1.100:54321
Xbox:   192.168.1.14:9002
Type:   UDP direct (no STUN/TURN)
RTT:    ~7ms
```

**Fallback path (different networks):**
```
Client srflx → Xbox srflx (via STUN)
or
Client relay → Xbox relay (via TURN)
RTT: 20-100ms+
```

### DTLS Handshake

After ICE connection:
1. DTLS handshake (certificate exchange)
2. SRTP keys derived
3. SCTP association established
4. DataChannels opened

**Timeline:**
```
T+0ms:    ICE connected
T+100ms:  DTLS handshake complete
T+200ms:  SCTP association ready
T+300ms:  DataChannels open
T+400ms:  Media streaming begins
```

---

## DataChannel Configuration

### Channel 1: "input"

**Label:** `input`  
**ID:** 4 (observed)  
**Direction:** Client → Xbox  
**Purpose:** Gamepad/keyboard/mouse input

**Configuration:**
- Protocol: SCTP over DTLS
- Ordered: Yes
- Reliable: No (recommended for low latency)
- Binary messages: 43 bytes (see `05_INPUT_PROTOCOL_SPECIFICATION.md`)

### Channel 2: "control"

**Label:** `control`  
**ID:** 8 (observed)  
**Direction:** Bidirectional  
**Purpose:** Video quality control, keyframe requests

**Configuration:**
- Protocol: SCTP over DTLS
- Ordered: Yes
- Reliable: Yes
- Text messages: JSON strings

**Message examples:**
```json
{"message":"videoKeyframeRequested","ifrRequested":false}
```

Sent every ~50 seconds or when video quality degrades.

### Channel 3: "message"

**Label:** `message`  
**ID:** 10 (observed)  
**Direction:** Bidirectional  
**Purpose:** System messages, transactions, game state

**Configuration:**
- Protocol: SCTP over DTLS
- Ordered: Yes
- Reliable: Yes
- Text messages: JSON objects

**Message example:**
```json
{
  "transactionId": "12345-67890",
  "type": "TransactionStart",
  "path": "/streaming/properties/titleinfo",
  "cv": "mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.18"
}
```

**Correlation vectors (`cv`):** Used for distributed tracing, increment per message

---

## Media Streaming

### Video Stream

**Codec:** H.264 Baseline Profile  
**Profile-level-id:** `42001f` (Baseline Level 3.1)  
**Resolution:** 720p (1280×720) typical; negotiable up to 1080p  
**Framerate:** 60 fps target  
**Bitrate:** 1-10 Mbps (adaptive)

**RTP Configuration:**
- Payload type: 96 (H.264)
- Payload type: 97 (RTX for 96)
- Clock rate: 90000 Hz
- Packetization mode: 1 (non-interleaved)

**Decoding:**
- Hardware decoder preferred (VaapiVideoDecoder on Linux)
- Fallback to software decoder (FFmpeg, libavcodec)

### Audio Stream

**Codec:** Opus  
**Sample rate:** 48 kHz  
**Channels:** 2 (stereo)  
**Bitrate:** ~64-128 kbps  

**RTP Configuration:**
- Payload type: 111 (Opus)
- Clock rate: 48000 Hz
- Min ptime: 10ms
- FEC: Enabled (useinbandfec=1)

**Decoding:**
- libopus or browser native decoder

---

## Session Lifecycle

### State Transitions

```
Created → Ready → Streaming → Ended
```

**Created:**
- Session allocated on server
- WebSocket not yet connected
- Duration: < 1 second

**Ready:**
- WebSocket connected
- SDP offer sent
- ICE gathering in progress
- Duration: 1-5 seconds

**Streaming:**
- WebRTC connected (ICE + DTLS complete)
- Media flowing
- DataChannels open
- Duration: Entire gameplay session

**Ended:**
- Session terminated (user disconnect, error, timeout)
- Resources freed

### Heartbeat / Keepalive

**Method:** WebSocket ping/pong  
**Interval:** 30 seconds  
**Timeout:** 60 seconds without response = disconnect

**Alternative:** Send periodic input messages (acts as keepalive)

### Session Termination

**Graceful shutdown:**
1. Client closes DataChannels
2. Client closes WebRTC connection
3. Client closes WebSocket
4. Server frees resources

**Forced shutdown:**
- Close WebSocket (server cleans up WebRTC)
- HTTP DELETE to session endpoint

**Endpoint:**
```
DELETE /v5/sessions/home/<CONSOLE_ID>/<SESSION_ID>
Authorization: XBL3.0 x=<USER_HASH>;<XSTS_TOKEN>
```

---

## Error Handling

### Connection Errors

**Symptom:** ICE connection fails  
**Causes:**
- Firewall blocking UDP
- STUN/TURN unreachable
- Network instability

**Mitigation:**
- Retry with TCP TURN
- Fallback to relay candidates
- Display error to user

### Media Errors

**Symptom:** Video freezes, stutters  
**Causes:**
- Packet loss
- Bandwidth congestion
- Decoder errors

**Mitigation:**
- Request keyframe (send control message)
- Reduce bitrate
- Switch to lower resolution

### Session Errors

**Symptom:** Session creation fails  
**Causes:**
- Console offline
- Token expired
- Session limit reached

**Mitigation:**
- Wake console (WoL or Xbox wake API)
- Refresh token
- End existing sessions

---

## Implementation Checklist for Team 2

### Phase 1: Session Management

- [ ] Implement session creation (HTTP POST)
- [ ] Parse session response (sessionId, streamPath)
- [ ] Handle error responses
- [ ] Store session credentials

### Phase 2: WebSocket Signaling

- [ ] Connect WebSocket with auth headers
- [ ] Implement message send/receive
- [ ] Handle connection errors
- [ ] Implement reconnection logic

### Phase 3: WebRTC Setup

- [ ] Create RTCPeerConnection
- [ ] Set STUN/TURN servers
- [ ] Create SDP offer
- [ ] Send offer via WebSocket
- [ ] Receive and set answer
- [ ] Handle ICE candidates
- [ ] Monitor connection state

### Phase 4: DataChannels

- [ ] Open three DataChannels (input, control, message)
- [ ] Implement binary send for input channel
- [ ] Implement JSON send/receive for control
- [ ] Implement JSON send/receive for message
- [ ] Handle DataChannel open/close events

### Phase 5: Media Handling

- [ ] Attach media streams to video/audio elements
- [ ] Implement hardware decoder selection
- [ ] Handle video resolution changes
- [ ] Implement audio output routing

### Phase 6: Error Handling & Recovery

- [ ] Handle ICE failure (retry, TURN fallback)
- [ ] Handle session timeout (reconnect)
- [ ] Handle media errors (keyframe request)
- [ ] Display user-friendly error messages

---

## Testing Strategy

### Unit Tests

- Session creation with mock responses
- SDP parsing and generation
- ICE candidate parsing
- DataChannel message formatting

### Integration Tests

- Full signaling flow (mock Xbox)
- WebRTC connection (loopback)
- Media streaming (test video)
- DataChannel communication

### End-to-End Tests

- Connect to real Xbox console
- Verify P2P connection on LAN
- Verify TURN fallback on different networks
- Test session termination
- Test error scenarios (offline console, expired token)

---

## Security Considerations

### Authentication

- All requests require valid XSTS token
- Tokens expire (refresh required)
- Correlation IDs for request tracking

### Encryption

- WebRTC media: SRTP (encrypted RTP)
- DataChannels: DTLS (TLS over UDP)
- Signaling: WSS (WebSocket over TLS)

**No plaintext transmission of media or input data**

### Privacy

- Session IDs are ephemeral (not logged long-term)
- No telemetry in this protocol spec
- Local network discovery reveals LAN topology

---

## Performance Optimization

### Latency Reduction

1. **P2P connection:** Use host candidates (skip STUN/TURN)
2. **Unreliable DataChannels:** Set maxRetransmits=0 for input
3. **Low jitter buffer:** Configure for low latency mode
4. **Hardware decode:** Use GPU decoding (VaapiVideoDecoder, NVDEC, VideoToolbox)

### Bandwidth Optimization

1. **Adaptive bitrate:** Monitor packet loss, adjust bitrate
2. **Resolution scaling:** 720p for < 5 Mbps, 1080p for > 8 Mbps
3. **FEC:** Forward error correction (Opus audio)
4. **RTX:** Retransmission for video (if bandwidth allows)

---

## Comparison with Open Source

**Reference:** `xbox-xcloud-player` (MIT Licensed)

**Expected similarities:**
- REST API for session creation
- WebSocket signaling
- Standard WebRTC (SDP/ICE)
- H.264 + Opus codecs
- DataChannels for input/control

**Document differences:** Team 2 should compare this spec with open-source implementation and note any differences in:
- Endpoint URLs
- Session creation request format
- SDP attribute differences
- ICE candidate handling
- DataChannel configuration

**Use open-source code as reference, but implement independently based on this specification.**

---

## Document Metadata

**Source:** 79 MB HAR file (2,217 network requests) + WebRTC internals  
**Capture date:** 2026-03-28  
**Analysis method:** Network trace analysis, SDP parsing, protocol reverse-engineering  
**Cleanroom compliance:** ✅ Yes - No Microsoft code copied, only protocol behavior documented

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-29 | Initial specification from capture analysis |

---

**Status:** Ready for Team 2 implementation.
