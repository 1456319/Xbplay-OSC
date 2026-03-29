# DataChannel Message Capture - Quick Fix Guide

**Problem:** Your `data_channel2_20260328_2100_26_5.log` file is 0 bytes (empty)

**Solution:** Recapture with correct timing

---

## Why It Failed

The DataChannel recording feature must be enabled **BEFORE** the WebRTC connection is established. Your file name format is correct, but no messages were captured because either:

1. Recording was enabled after DataChannels already opened
2. No gamepad input was sent during recording
3. Messages are sent on a channel that wasn't recorded

---

## Correct Capture Procedure

### Step 1: Prepare (Before Opening Xbox Site)

1. **Close all Xbox Play tabs**
2. **Open NEW tab:** Navigate to `chrome://webrtc-internals`
3. **Scroll down** to "Create DataChannel message recordings"
4. **Check the box:** "Enable DataChannel message recordings"
5. **Enter filename:** `datachannel_input_test`
6. **Important:** Leave this tab open - do NOT close it

### Step 2: Start Remote Play (In New Tab)

1. **Open NEW tab** (keep webrtc-internals tab open!)
2. Navigate to: `https://www.xbox.com/en-US/play`
3. Click your console
4. **Wait** for streaming to fully connect
5. **Look for:** "Stream connected" or gameplay visible

### Step 3: Send Input (Critical!)

**Connect gamepad** to your PC (wired or wireless)

**Press each button slowly** (3 seconds between presses):
```
1. Press A button → Hold 2 seconds → Release
   (Wait 3 seconds)

2. Press B button → Hold 2 seconds → Release
   (Wait 3 seconds)

3. Press X button → Hold 2 seconds → Release
   (Wait 3 seconds)

4. Press Y button → Hold 2 seconds → Release
   (Wait 3 seconds)

5. Move Left Stick:
   - Push UP (max) → Hold 2 seconds
   - Center
   - Push DOWN (max) → Hold 2 seconds
   - Center
   - Push LEFT (max) → Hold 2 seconds
   - Center
   - Push RIGHT (max) → Hold 2 seconds
   - Center
   (Wait 3 seconds)

6. Move Right Stick:
   - Rotate slowly in full circle (clockwise)
   - Complete one rotation in ~10 seconds
   (Wait 3 seconds)

7. Press Left Trigger:
   - Press halfway → Hold 2 seconds
   - Press fully → Hold 2 seconds
   - Release
   (Wait 3 seconds)

8. Press Right Trigger:
   - Press halfway → Hold 2 seconds
   - Press fully → Hold 2 seconds
   - Release
   (Wait 3 seconds)

9. Press D-Pad:
   - UP → Hold 1 second
   - DOWN → Hold 1 second
   - LEFT → Hold 1 second
   - RIGHT → Hold 1 second
   (Wait 3 seconds)

10. Press Menu button
    (Wait 3 seconds)

11. Press View button
    (Wait 3 seconds)

12. Press Xbox button (if accessible in stream)
```

**Total time:** ~3 minutes of button pressing

### Step 4: Stop and Collect

1. Go back to `chrome://webrtc-internals` tab
2. **Uncheck** "Enable DataChannel message recordings"
3. **Check file location:**
   ```bash
   ls -lh ~/Downloads/datachannel_input_test_*
   ```
4. **Move to dump folder:**
   ```bash
   mv ~/Downloads/datachannel_input_test_* "/home/deck/dump/xbplay spare parts/Dirt Room  Team 2/"
   ```

---

## Expected Results

### If Successful:
- **File size:** 50 KB to 5 MB (depends on message frequency)
- **Content:** Text and/or base64-encoded binary messages
- **Format Example:**
  ```
  [SEND] DataChannel[D9]: <binary data in base64>
  [SEND] DataChannel[D9]: <binary data in base64>
  [RECV] DataChannel[D11]: {"type":"control","action":"screenshot_ack"}
  ```

### If Still 0 Bytes:
**Possible issues:**
1. Gamepad not detected by browser
   - **Test:** Open https://html5gamepad.com/ and verify buttons register
2. Input channel uses binary messages Chrome doesn't log
   - **Alternative:** Use JavaScript console injection (advanced)
3. Messages sent before recording started
   - **Solution:** Enable recording earlier

---

## Alternative: Console JavaScript Injection

If file capture doesn't work, we can hook the DataChannel directly:

### Step 1: Open DevTools Console

1. On Xbox Play page (while NOT streaming yet)
2. Press F12 → Console tab
3. Paste this script:

```javascript
// Hook DataChannel send before connection
(function() {
    const originalSend = RTCDataChannel.prototype.send;
    const messages = [];
    
    RTCDataChannel.prototype.send = function(data) {
        const timestamp = Date.now();
        const label = this.label;
        const id = this.id;
        
        let logData;
        if (typeof data === 'string') {
            logData = {type: 'text', data: data};
        } else if (data instanceof ArrayBuffer) {
            // Convert binary to hex
            const bytes = new Uint8Array(data);
            const hex = Array.from(bytes)
                .map(b => b.toString(16).padStart(2, '0'))
                .join(' ');
            logData = {type: 'binary', length: data.byteLength, hex: hex.substring(0, 200)};
        }
        
        const entry = {
            timestamp,
            label,
            id,
            direction: 'send',
            ...logData
        };
        
        messages.push(entry);
        console.log('[DC_SEND]', label, entry);
        
        return originalSend.call(this, data);
    };
    
    // Save function to download log
    window.saveDCLog = function() {
        const json = JSON.stringify(messages, null, 2);
        const blob = new Blob([json], {type: 'application/json'});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'datachannel_messages.json';
        a.click();
    };
    
    console.log('DataChannel hook installed. Use saveDCLog() to download captured messages.');
})();
```

### Step 2: Start Streaming

1. Connect to Xbox
2. Press gamepad buttons (same sequence as above)
3. Watch Console for `[DC_SEND]` messages

### Step 3: Save Log

In console, type:
```javascript
saveDCLog()
```

File downloads as `datachannel_messages.json`

---

## What We're Looking For

From the captured messages, we need to identify:

### Input Message Structure:
```
{
  "timestamp": <milliseconds>,
  "buttons": <bitmask or array>,
  "axes": {
    "leftStickX": <value>,
    "leftStickY": <value>,
    "rightStickX": <value>,
    "rightStickY": <value>,
    "leftTrigger": <value>,
    "rightTrigger": <value>
  }
}
```

OR (if binary):
```
Byte 0-1: Message type/header
Byte 2-3: Button bitmask
Byte 4-5: Left stick X (int16)
Byte 6-7: Left stick Y (int16)
Byte 8-9: Right stick X (int16)
Byte 10-11: Right stick Y (int16)
Byte 12: Left trigger (uint8)
Byte 13: Right trigger (uint8)
```

---

## Troubleshooting

### Gamepad Not Detected

**Test gamepad:**
1. Open https://html5gamepad.com/
2. Press buttons
3. If nothing happens → gamepad not working in browser

**Fix:**
- Use different browser (Chrome vs Firefox)
- Reconnect gamepad
- Use different USB port
- Check `chrome://device-log/` for errors

### Still 0 Bytes

If recording still produces 0-byte file:
1. Check Chrome version (update if old)
2. Try Incognito mode
3. Try different browser (Edge, Chrome, Ungoogled Chromium)
4. Use JavaScript injection method instead

---

## File Location

Save all captures to:
```
/home/deck/dump/xbplay spare parts/Dirt Room  Team 2/
```

**Naming convention:**
- `datachannel_input_test_YYYYMMDD_HHMM_PID_ID.log` (Chrome auto-generated)
- `datachannel_messages.json` (JavaScript injection method)

---

## After Successful Capture

Once you have a non-empty DataChannel log:

1. **Don't modify** the original file
2. **Copy** to dump folder
3. **Let me analyze** the message format
4. I'll create the input protocol specification for Team 2

**ETA after capture:** 1 hour to fully document input protocol

---

## Status

- ❌ Previous attempt: 0 bytes (timing issue)
- ⏳ Next attempt: Follow this guide
- ✅ Expected: 50 KB - 5 MB of input messages

**This is the final missing piece for input protocol documentation!**
