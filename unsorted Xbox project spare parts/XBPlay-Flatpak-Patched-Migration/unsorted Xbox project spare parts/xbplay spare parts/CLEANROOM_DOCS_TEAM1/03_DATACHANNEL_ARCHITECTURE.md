# Xbox Remote Play DataChannel Architecture

**Document Version:** 1.0  
**Date:** 2026-03-29  
**Team:** Team 1 (Dirty Room)  
**Status:** Complete - Ready for Team 2 Implementation

---

## Executive Summary

Xbox Remote Play uses three WebRTC DataChannels for bidirectional communication beyond audio/video streaming. Each channel serves a distinct purpose and uses different message formats, reliability requirements, and communication patterns.

**The three channels:**
1. **input** (ID: 4) - Client → Xbox gamepad/keyboard input (binary, 43 bytes)
2. **control** (ID: 8) - Bidirectional video quality control (JSON strings)
3. **message** (ID: 10) - Bidirectional system messages (JSON objects)

---

## DataChannel Overview

### WebRTC DataChannel Basics

DataChannels provide application-level messaging over WebRTC connections:
- Protocol: SCTP over DTLS over UDP
- Encryption: DTLS (same as media streams)
- Configuration: Ordered/unordered, reliable/unreliable
- Message types: Text (UTF-8) or Binary

### SDP Configuration

All three channels are negotiated in the SDP offer/answer:

```
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

**Key attributes:**
- SCTP port: 5000
- Max message size: 256 KB (262144 bytes)
- Bundled with video/audio (same DTLS connection)

---

## Channel 1: "input"

### Purpose

Transmits user input (gamepad, keyboard, mouse) from client to Xbox console in real-time.

### Configuration

**Label:** `input`  
**Channel ID:** 4 (may vary, use label for identification)  
**Direction:** Unidirectional (Client → Xbox)  
**Message type:** Binary  
**Ordered:** Yes (recommended to preserve input sequence)  
**Reliable:** No (maxRetransmits: 0 for lowest latency)

**JavaScript configuration:**
```javascript
const inputChannel = peerConnection.createDataChannel('input', {
  ordered: true,
  maxRetransmits: 0,  // Unreliable for low latency
  id: 4  // Optional: suggest ID
});
```

### Message Format

**Binary protocol:** Fixed 43-byte messages  
**Frequency:** 60-100 messages per second (16-10ms interval)  
**Encoding:** Little-endian

**See:** `05_INPUT_PROTOCOL_SPECIFICATION.md` for complete binary format

**Message structure summary:**
```
Bytes 0-3:   Header (type, flags, sequence, marker)
Bytes 4-7:   Button state
Bytes 8-11:  Timestamp (float32)
Bytes 12-17: Metadata
Byte 18:     Marker (0x8a)
Bytes 19-42: Analog values (sticks, triggers) with 0xae separators
```

### Timing & Flow Control

**Sending strategy:**
```
Option A: Periodic (60 Hz)
  - Send every 16.67ms regardless of input changes
  - Pro: Consistent timing, simple implementation
  - Con: More bandwidth (~25 KB/s)

Option B: On-change (with heartbeat)
  - Send immediately when input changes
  - Send heartbeat every 100ms if no changes
  - Pro: Lower bandwidth when idle
  - Con: Variable timing, more complex

Recommended: Option A (periodic 60 Hz)
```

**Bandwidth calculation:**
```
43 bytes/message × 60 Hz = 2,580 bytes/sec = ~20 kbit/s
```

Negligible compared to video bitrate (1-10 Mbps).

### Error Handling

**Channel closure:**
- If input channel closes: Stop sending, attempt to reopen
- Xbox should ignore missing input (graceful degradation)

**Message loss:**
- Unreliable channel = messages may be dropped
- Sequence numbers allow Xbox to detect drops
- Xbox should interpolate missing inputs or use last known state

### Testing Strategy

**Unit tests:**
- Message construction (valid 43-byte format)
- Sequence number increment
- Button encoding/decoding

**Integration tests:**
- Send test messages at 60 Hz
- Verify no memory leaks (continuous sending)
- Test with varying input rates

**End-to-end tests:**
- Connect to Xbox, send input
- Verify Xbox responds to buttons/sticks
- Test rapid input (button mashing)
- Test sustained input (hold button 60+ seconds)

---

## Channel 2: "control"

### Purpose

Manages video quality, codec parameters, and streaming control between client and Xbox.

### Configuration

**Label:** `control`  
**Channel ID:** 8 (may vary, use label for identification)  
**Direction:** Bidirectional  
**Message type:** Text (JSON strings)  
**Ordered:** Yes  
**Reliable:** Yes (maxRetransmits: unlimited)

**JavaScript configuration:**
```javascript
const controlChannel = peerConnection.createDataChannel('control', {
  ordered: true,
  maxRetransmits: undefined,  // Reliable
  id: 8  // Optional: suggest ID
});
```

### Message Format

**Text-based JSON strings** (not JSON objects, but stringified)

**Example messages:**

#### Client → Xbox: Keyframe Request
```json
{
  "message": "videoKeyframeRequested",
  "ifrRequested": false
}
```

Sent when:
- Video quality degrades (packet loss, corruption)
- Client decoder error
- Periodic (~50 seconds for refresh)

**Parameters:**
- `ifrRequested`: `false` = request any keyframe, `true` = request IDR frame

#### Xbox → Client: Quality Update (Hypothetical)
```json
{
  "message": "videoQualityUpdate",
  "resolution": "1280x720",
  "bitrate": 5000,
  "framerate": 60
}
```

(Not observed in captures, but expected message type)

### Timing & Flow Control

**Sending strategy:**
- Event-driven (send only when needed)
- Typical frequency: ~1 message per 50 seconds (keyframe requests)
- Low bandwidth: < 1 KB/hour

**Bandwidth:** Negligible (< 1 kbit/s)

### Error Handling

**Channel closure:**
- If control channel closes: Video continues, but no quality control
- Should attempt to reopen immediately

**Message errors:**
- Reliable channel = no message loss
- If invalid JSON received: Log error, ignore message

### Testing Strategy

**Unit tests:**
- JSON parsing/serialization
- Message validation

**Integration tests:**
- Send keyframe request, verify new keyframe received
- Test with simulated packet loss

**End-to-end tests:**
- Request keyframe during streaming
- Verify video recovers after corruption

---

## Channel 3: "message"

### Purpose

Handles system-level communication: game state, transactions, title information, and Xbox system messages.

### Configuration

**Label:** `message`  
**Channel ID:** 10 (may vary, use label for identification)  
**Direction:** Bidirectional  
**Message type:** Text (JSON objects)  
**Ordered:** Yes  
**Reliable:** Yes (maxRetransmits: unlimited)

**JavaScript configuration:**
```javascript
const messageChannel = peerConnection.createDataChannel('message', {
  ordered: true,
  maxRetransmits: undefined,  // Reliable
  id: 10  // Optional: suggest ID
});
```

### Message Format

**JSON objects** with transaction-based pattern

#### Transaction Start
```json
{
  "transactionId": "abc123-456def",
  "type": "TransactionStart",
  "path": "/streaming/properties/titleinfo",
  "cv": "mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.18",
  "data": {
    // Request-specific data
  }
}
```

#### Transaction Complete
```json
{
  "transactionId": "abc123-456def",
  "type": "TransactionComplete",
  "path": "/streaming/properties/titleinfo",
  "cv": "mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.19",
  "result": {
    // Response data
  }
}
```

### Common Message Paths

Based on captured traffic, observed paths include:

1. **`/streaming/properties/titleinfo`**
   - Get current game/title information
   - Example response: Title ID, name, artwork URLs

2. **`/streaming/title/constrain`**
   - Set title constraints (resolution, framerate)
   - Example request: Lock to 60fps, prefer 1080p

3. **`/streaming/input/enable`** (Hypothetical)
   - Enable/disable specific input types
   - Example: Enable keyboard, disable mouse

4. **`/streaming/audio/config`** (Hypothetical)
   - Configure audio output
   - Example: Set volume, enable/disable chat mix

### Correlation Vectors

Each message includes a `cv` (correlation vector) for distributed tracing:

**Format:** `<BASE_CV>.<INCREMENT>.<DEPTH>.<FLAGS>`

**Example progression:**
```
mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.18
mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.19
mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.20
```

**Increment logic:**
- Increment counter for each message sent
- Reset counter on new session

**Implementation:**
```javascript
let cvCounter = 0;
const cvBase = generateRandomCvBase(); // Random base per session

function getNextCv() {
  return `${cvBase}.13.7.0.0.${cvCounter++}`;
}
```

### Timing & Flow Control

**Sending strategy:**
- Event-driven (on user action, game state change)
- Typical frequency: 5-10 messages per minute
- Low bandwidth: < 10 KB/minute

**Bandwidth:** Negligible (< 10 kbit/s)

### Error Handling

**Channel closure:**
- If message channel closes: Transactions fail, but streaming continues
- Should attempt to reopen immediately

**Transaction errors:**
- Timeout: 30 seconds without TransactionComplete = failure
- Retry: Resend TransactionStart with same ID
- Fallback: If critical transaction fails, show error to user

**Invalid messages:**
- If invalid JSON: Log error, ignore message
- If unknown path: Log warning, send error response (if applicable)

### Testing Strategy

**Unit tests:**
- JSON parsing/serialization
- Transaction ID generation
- Correlation vector increment

**Integration tests:**
- Send transaction, verify completion
- Test timeout handling
- Test invalid message handling

**End-to-end tests:**
- Query title info during streaming
- Change settings (resolution, framerate)
- Verify game state synchronization

---

## DataChannel Lifecycle

### Initialization Sequence

```
1. RTCPeerConnection created
2. Client creates DataChannels (input, control, message)
3. SDP offer includes DataChannel configuration
4. WebRTC connection established (ICE + DTLS)
5. SCTP association established
6. DataChannels transition to "open" state
7. Application begins sending/receiving messages
```

**Timeline:**
```
T+0ms:    createDataChannel() called
T+100ms:  ICE connected
T+200ms:  DTLS handshake complete
T+300ms:  SCTP association ready
T+400ms:  DataChannels fire "open" event
T+500ms:  Application can send messages
```

### State Transitions

**DataChannel states:**
- `connecting` - Negotiation in progress
- `open` - Ready for send/receive
- `closing` - Close initiated
- `closed` - Channel closed

**Event handlers:**
```javascript
inputChannel.onopen = () => {
  console.log('Input channel open, can send input now');
};

inputChannel.onclose = () => {
  console.log('Input channel closed, attempt reopen');
};

inputChannel.onerror = (error) => {
  console.error('Input channel error:', error);
};
```

### Graceful Shutdown

**Recommended sequence:**
```
1. Stop sending input messages
2. Send any pending control/message transactions
3. Wait for transaction completions (max 5 seconds)
4. Close DataChannels: inputChannel.close()
5. Close RTCPeerConnection: peerConnection.close()
6. Close WebSocket signaling connection
```

**Forced shutdown:**
```
1. Close RTCPeerConnection immediately
   (automatically closes all DataChannels)
2. Close WebSocket
```

---

## Performance Considerations

### Buffering

**BufferedAmount tracking:**
```javascript
function sendInputMessage(data) {
  if (inputChannel.bufferedAmount < 32768) { // 32 KB threshold
    inputChannel.send(data);
  } else {
    console.warn('Input channel buffer full, dropping message');
    // Option: Queue and send later, or skip (unreliable channel)
  }
}
```

**Recommended thresholds:**
- Input channel: 32 KB max buffer (avoid queuing too many messages)
- Control/message channels: 256 KB max buffer (reliable, can buffer more)

### SCTP Configuration

**SCTP parameters (set via browser API or native library):**
```
sctp-port: 5000
max-message-size: 262144 (256 KB)
number-of-streams: 16 (default)
```

**For Electron/Native:**
- Can tune SCTP parameters for lower latency
- Consider disabling Nagle algorithm for real-time input

### CPU & Memory

**Input channel (60 Hz):**
- CPU: < 1% (message serialization is cheap)
- Memory: < 1 MB (small message size, no buffering)

**Control & message channels:**
- CPU: < 0.1% (infrequent messages)
- Memory: < 1 MB (text messages, small size)

**Total DataChannel overhead:** < 2% CPU, < 2 MB RAM

---

## Security Considerations

### Encryption

All DataChannel messages are encrypted via DTLS:
- Same security level as TLS 1.2+
- Ephemeral keys (new per session)
- Perfect forward secrecy

### Message Validation

**Input channel:**
- Validate message length (must be exactly 43 bytes)
- Validate header (type, marker bytes)
- Clamp analog values to valid ranges

**Control & message channels:**
- Validate JSON structure
- Sanitize string inputs (prevent injection)
- Whitelist allowed paths/message types

### Rate Limiting (Xbox side)

**Expected rate limits:**
- Input: 100 Hz max (drop messages above rate)
- Control: 10 messages/second max
- Message: 100 messages/minute max

**Client should respect these limits to avoid disconnection**

---

## Debugging & Monitoring

### Browser DevTools

**Chrome/Edge:**
```
chrome://webrtc-internals
```

Shows:
- DataChannel state (open, closed)
- Message counts (sent, received)
- Bytes sent/received
- Buffer size

**Firefox:**
```
about:webrtc
```

Similar information, different UI.

### Logging Strategy

**Recommended logging:**
```javascript
// Input channel: Log only errors and state changes
inputChannel.onerror = (e) => console.error('Input error:', e);
inputChannel.onclose = () => console.warn('Input closed');

// Control channel: Log all messages (low frequency)
controlChannel.onmessage = (e) => console.log('Control RX:', e.data);

// Message channel: Log all messages
messageChannel.onmessage = (e) => console.log('Message RX:', e.data);
```

**Do NOT log:**
- Individual input messages (too verbose, 60/sec)
- Binary message contents (large dumps)

### Metrics to Monitor

**Performance metrics:**
- Input channel: Messages sent per second (expect ~60-100)
- Control channel: Keyframe request frequency (expect ~1/50sec)
- Message channel: Transaction latency (expect < 100ms)
- Buffered amount: Should stay near 0 (< 10 KB)

**Error metrics:**
- Channel reopen count (expect 0 in healthy session)
- Message send failures (expect 0)
- Invalid message count (expect 0)

---

## Implementation Checklist for Team 2

### Phase 1: Basic Setup

- [ ] Create three DataChannels with correct labels
- [ ] Set ordered/reliable properties correctly
- [ ] Implement onopen, onclose, onerror handlers
- [ ] Wait for "open" event before sending

### Phase 2: Input Channel

- [ ] Implement 43-byte binary message construction
- [ ] Implement 60 Hz periodic sending
- [ ] Add bufferedAmount check
- [ ] Test with Xbox (verify input is recognized)

### Phase 3: Control Channel

- [ ] Implement JSON message construction
- [ ] Implement keyframe request sending
- [ ] Handle incoming quality updates (if applicable)
- [ ] Test keyframe request (verify new frame received)

### Phase 4: Message Channel

- [ ] Implement transaction message construction
- [ ] Implement correlation vector increment
- [ ] Handle TransactionStart/Complete pattern
- [ ] Parse incoming messages (title info, etc.)
- [ ] Implement transaction timeout (30 seconds)

### Phase 5: Error Handling

- [ ] Implement channel reopen on close
- [ ] Handle invalid messages gracefully
- [ ] Implement rate limiting (client-side)
- [ ] Add comprehensive error logging

### Phase 6: Optimization

- [ ] Tune buffer thresholds
- [ ] Implement on-change input sending (optional)
- [ ] Add performance metrics logging
- [ ] Profile CPU/memory usage

---

## Testing Strategy

### Unit Tests

- DataChannel message construction (all three channels)
- JSON parsing/serialization (control, message)
- Binary protocol encoding (input)
- Correlation vector generation

### Integration Tests

- DataChannel open/close lifecycle
- Message send/receive (mock peer)
- Buffer management
- Error handling (simulated errors)

### End-to-End Tests

- Connect to real Xbox console
- Send input, verify recognized
- Send control messages, verify effect
- Send message transactions, verify responses
- Test all error scenarios

---

## Comparison with Open Source

**Reference:** `xbox-xcloud-player` (MIT Licensed)

**Expected similarities:**
- Three DataChannels (input, control, message)
- Binary input protocol
- JSON control/message protocols
- Transaction pattern for messages

**Document differences:**
- Channel IDs (may differ)
- Message formats (may have evolved)
- Transaction paths (may differ between xCloud vs Remote Play)
- Correlation vector implementation

**Team 2 action:** Compare this spec with open-source implementation, note differences, implement independently.

---

## Document Metadata

**Source:** 4.2 MB DataChannel capture (21,716 messages) + WebRTC internals  
**Capture date:** 2026-03-28  
**Analysis method:** Message frequency analysis, pattern recognition, protocol reverse-engineering  
**Cleanroom compliance:** ✅ Yes - No Microsoft code copied, only protocol behavior documented

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-29 | Initial specification from capture analysis |

---

**Status:** Ready for Team 2 implementation.
