# 🎉 BREAKTHROUGH: Complete Input Protocol Captured!

**Date:** 2026-03-29  
**File:** `data_channel2_20260328_2100_26_5.log` (4.2 MB)  
**Format:** JSON Lines (NDJSON)  
**Status:** ✅ COMPLETE SUCCESS

---

## Summary

**Total messages:** 21,716  
**Input messages:** 21,659 (99.7%)  
**Control messages:** ~50  
**Message channel:** ~7

**Data captured:**
- ✅ Input protocol (binary, base64-encoded)
- ✅ Control protocol (JSON strings)
- ✅ Message channel protocol (JSON strings)
- ✅ Timestamps for all messages
- ✅ DataChannel IDs and labels

---

## DataChannel Mapping (CONFIRMED)

| ID | Label | Direction | Data Type | Purpose | Message Count |
|----|-------|-----------|-----------|---------|---------------|
| 4 | input | send | binary | Gamepad/keyboard/mouse | 21,659 |
| 8 | control | send | string (JSON) | Video control, keyframes | ~50 |
| 10 | message | bidirectional | string (JSON) | System messages, state | ~7 |

---

## Input Protocol Structure (Binary Message Format)

### Message Header (4 bytes)

```
Byte 0: 0x01 - Message type (constant = 1 for input)
Byte 1: 0x00 - Flags (appears to be 0 for standard input)
Byte 2: varies - Sequence number (15, 16, 17, ... incrementing)
Byte 3: 0x46 - Header marker (constant = 70 decimal, 'F' ASCII)
```

### Message Body (39 bytes after header)

**Total message size:** 43 bytes

**Decoding observations:**
- Bytes 4-7: Appears to be button state or timestamp
- Remaining bytes: Likely analog stick and trigger values
- Message sent ~60 times per second (frame rate of input polling)

**Sample decoded message:**
```
Hex: 01 00 0f 46 00 00 c0 99 99 99 7b 74 31 41 ...
     ^  ^  ^  ^  ^--- Body starts here
     |  |  |  +------ Header marker (0x46)
     |  |  +--------- Sequence (0x0F = 15)
     |  +------------ Flags (0x00)
     +--------------- Type (0x01)
```

---

## Control Channel Protocol (JSON)

### Message Structure

```json
{
  "message": "<message_type>",
  "<additional_fields>": "<values>"
}
```

### Observed Control Messages

#### 1. Video Keyframe Request
```json
{
  "message": "videoKeyframeRequested",
  "ifrRequested": false
}
```

**Purpose:** Request I-frame from Xbox encoder  
**Frequency:** Periodic (every ~50 seconds or on quality change)  
**Direction:** Client → Xbox

---

## Message Channel Protocol (JSON Transactions)

### Transaction Pattern

**Request (TransactionStart):**
```json
{
  "content": "<JSON_payload>",
  "id": "<UUID>",
  "target": "<endpoint>",
  "type": "TransactionStart",
  "cv": "<correlation_vector>"
}
```

**Response (TransactionComplete):**
```json
{
  "content": "<JSON_response>",
  "cv": "<correlation_vector>",
  "id": "<same_UUID>",
  "type": "TransactionComplete"
}
```

### Observed Transactions

#### 1. Stream Constrain Control
**Target:** `/streaming/title/constrain`  
**Request:**
```json
{"constrain": false}
```
**Response:**
```json
{"status": "Success"}
```
**Purpose:** Toggle stream quality constraints

#### 2. Title Info Notification
**Target:** `/streaming/properties/titleinfo`  
**Type:** `Message` (not a transaction)
**Content:**
```json
{
  "focused": false,
  "state": 4,
  "titleaumid": "",
  "titleid": "00000000"
}
```
**Purpose:** Notify client of running app/game state

#### 3. Touch Controls Layout
**Target:** `/streaming/touchcontrols/showlayoutv2`  
**Content:**
```json
{"layoutId": ""}
```
**Purpose:** Mobile touch overlay control (not used on desktop)

#### 4. Recording Permission
**Target:** `/streaming/title/canRecordChanged`  
**Content:**
```json
{"isRecordingAllowed": true}
```
**Purpose:** Notify if game allows recording/screenshots

---

## Input Message Frequency Analysis

**Approximate timing:**
- Messages sent every ~10-30ms
- Average: ~60-100 messages per second
- Consistent with 60 FPS gameplay input polling

**Example timestamps (ms):**
```
1774746450903  (first input)
1774746451195  (+292ms)
1774746451207  (+12ms)
1774746451217  (+10ms)
1774746451239  (+22ms)
```

**Pattern:** Variable timing, likely sent on input change or periodic polling

---

## Correlation Vector System

All message channel transactions include a "cv" field:
```
"cv": "mcuMB8mW3O/+vkmqImfHrW.13.7.0.0.18"
```

**Format:** `<base>.<version>.<incremental_counter>`

**Purpose:**
- Distributed tracing across Xbox services
- Request/response correlation
- Debugging aid for Microsoft

**Increments per message:**
```
cv: ...13.7.0.0.18  (TransactionStart)
cv: ...13.7.0.0.19  (TransactionComplete)
```

---

## Input Protocol Next Steps (Reverse Engineering)

To fully decode the 43-byte input messages, we need to:

### Step 1: Parse Multiple Messages

Extract 10-20 sequential messages and compare byte-by-byte:
- Identify static bytes (header)
- Identify changing bytes (inputs)
- Correlate changes with known button presses

### Step 2: Button State Decoding

**Hypothesis:** Buttons encoded as bitmask

**Expected format:**
```
Bit 0: A button
Bit 1: B button
Bit 2: X button
Bit 3: Y button
Bit 4: LB (Left Bumper)
Bit 5: RB (Right Bumper)
Bit 6: Menu
Bit 7: View
Bit 8: Left Stick Click
Bit 9: Right Stick Click
Bit 10-13: D-Pad (Up, Down, Left, Right)
```

**Test:** Press A button only → Identify which byte/bit changes

### Step 3: Analog Values Decoding

**Hypothesis:** 16-bit signed integers for sticks, 8-bit unsigned for triggers

**Expected layout:**
```
Bytes 4-7: Timestamp or frame counter?
Bytes 8-9: Left Stick X (-32768 to 32767)
Bytes 10-11: Left Stick Y (-32768 to 32767)
Bytes 12-13: Right Stick X (-32768 to 32767)
Bytes 14-15: Right Stick Y (-32768 to 32767)
Bytes 16: Left Trigger (0-255)
Bytes 17: Right Trigger (0-255)
Bytes 18-19: Button bitmask (16 bits)
... remaining bytes TBD
```

### Step 4: Validate with Open Source

Compare against `xbox-xcloud-player` implementation:
- Does their input protocol match?
- Are there differences in message structure?
- Document similarities and differences

---

## Deliverables for Team 2

### Document 1: Input Protocol Specification

**Content:**
- Complete 43-byte message structure
- Button bitmask mapping
- Analog stick encoding (range, endianness)
- Trigger encoding
- Sequence number behavior
- Timing requirements (frequency, batching)

**Format:** Cleanroom specification with NO code from Microsoft

**Example specification:**
```
Xbox Remote Play Input Protocol v1

Message Structure (43 bytes):
- Header (4 bytes):
  - Byte 0: Type (0x01 = input message)
  - Byte 1: Flags
  - Byte 2: Sequence number (incremental)
  - Byte 3: Marker (0x46 constant)
  
- Body (39 bytes):
  - <fields to be determined from analysis>
```

### Document 2: Control Protocol Specification

**Content:**
- JSON message structure
- All known control message types
- Request/response patterns
- Frequency and timing requirements

### Document 3: Message Channel Protocol

**Content:**
- Transaction pattern (Start/Complete)
- All known endpoints
- Correlation vector format
- Error handling

---

## Analysis Tools to Create

### Tool 1: Input Message Parser

**Python script to:**
1. Read NDJSON file
2. Extract all input messages
3. Decode base64 to binary
4. Parse 43-byte structure
5. Output CSV with decoded fields
6. Correlate with timestamps

### Tool 2: Message Visualizer

**Python script to:**
1. Parse decoded input
2. Visualize button presses as timeline
3. Graph analog stick movements
4. Identify patterns

### Tool 3: Protocol Comparator

**Python script to:**
1. Compare our decoded protocol
2. Against xbox-xcloud-player
3. Identify differences
4. Generate compatibility report

---

## Security Considerations (Team 1 Only)

**Personal data in captured file:**
- Correlation vectors (unique to session)
- Transaction IDs (UUIDs, session-specific)
- No usernames, XUIDs, or tokens

**Status:** ✅ Safe to analyze, no PII in DataChannel messages
