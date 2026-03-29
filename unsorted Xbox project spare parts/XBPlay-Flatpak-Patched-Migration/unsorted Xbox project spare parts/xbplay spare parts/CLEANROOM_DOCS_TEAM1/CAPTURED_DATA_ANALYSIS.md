# Captured Data Analysis - March 2026

**Project:** Xbox Remote Play (Linux/Flatpak)  
**Status:** Research Phase (Team 1)

---

## Executive Summary

Current captures focus on the **WebRTC-based streaming protocol** and **authentication handshake**. We have successfully identified the core signaling flow but lack binary message specifications for input and control channels.

---

## Data Inventory

### 1. HAR Files (Network Traffic)
- `Remoteplay.har`: Initial login and session setup.
- `SENSITIVELONGRemoteplay.har`: Long-running session with multiple signaling exchanges.
- `play.xbox.com.har`: Cloud Play specific negotiation.

### 2. Binary Logs
- `rtcstats_dump.gz`: WebRTC statistics showing packet loss, RTT, and bandwidth.
- `event_log2_*.log`: RTP/RTCP packet headers and timing.

### 3. Audio Dumps
- `audio_debug2.output.5.wav`: Decoded game audio (48kHz stereo Opus).

---

## Protocol Breakdown

### Authentication
Uses OAuth 2.0 with the following scopes:
- `XboxLive.signin`
- `XboxLive.offline_access`

Token chain: `MSA → RPS → UserToken → XSTS → SessionToken`

### WebRTC Signaling
Signaling is done via HTTP POST to `/sdp` and `/ice` endpoints. 
**Note:** Standard WebSockets are NOT used for WebRTC signaling in the current Microsoft implementation.

### Codecs
- **Audio:** Opus (111)
- **Video:** H.264 Baseline (103) - negotiated fallback from HEVC.

---

## Implementation Roadblock: The "Input" Channel

We have identified the DataChannel labeled `input` (ID: D9). This channel is responsible for all gamepad events.

**What we know:**
- It is a reliable, ordered SCTP channel.
- It stays open throughout the session.

**What we don't know:**
- The binary structure of the input message.
- The frequency of stick updates.
- The mapping of special buttons (Guide button).

---

## Recommendations for Team 2

1. **Don't use Standard SDP:** Implement the polling-based signaling flow captured in the HAR.
2. **Hardware Decode:** Prioritize VA-API/NVDEC for H.264 to keep latency low.
3. **DataChannel Priority:** Treat the `input` channel with `high` priority in the WebRTC PeerConnection settings.
