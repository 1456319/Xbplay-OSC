# Chrome DevTools MCP Setup Guide

**Purpose:** Control Chrome/Brave from command line to capture missing Remote Play data

---

## What is Chrome DevTools MCP?

Chrome DevTools MCP is a **Model Context Protocol server** that lets AI assistants (like me) control a live Chrome browser. This means:

✅ I can navigate to specific URLs  
✅ I can monitor Network tab in real-time  
✅ I can execute JavaScript in the page console  
✅ I can capture screenshots  
✅ I can extract performance traces  
✅ **Most importantly:** I can tell you EXACTLY what to click and what NOT to click

---

## Installation Status

✅ **DONE:** Chrome DevTools MCP installed and built  
**Location:** `/tmp/chrome-devtools-mcp/`  

✅ **DONE:** Dependencies installed (635 npm packages)  

⚠️ **ISSUE:** Brave browser not properly installed as flatpak  
**Alternative:** Use Chrome Dev (available) or Ungoogled Chromium

---

## Available Browsers

| Browser | Status | MCP Compatible? |
|---------|--------|-----------------|
| Brave | ❌ Not found as flatpak | Unknown |
| Chrome Dev | ✅ Installed | ✅ Yes |
| Ungoogled Chromium | ✅ Installed | ✅ Yes |

**Recommendation:** Use **Chrome Dev** for MCP capture (fully compatible)

---

## How MCP Will Work

### Traditional Capture (Manual):
1. You open browser
2. You navigate to pages
3. You remember to open DevTools
4. You export HAR files manually
5. **Problem:** Easy to miss important data

### MCP Capture (Assisted):
1. I launch Chrome with debugging enabled
2. I navigate to specific pages programmatically
3. I monitor Network tab automatically
4. I capture everything in real-time
5. I tell you: "Don't click that yet, we need X first"
6. **Benefit:** Nothing gets missed, I guide you

---

## What MCP Can Capture for Us

### Gap #1: Console Discovery API ✅
**How:**
```
1. I navigate to /play/consoles
2. I monitor all XHR/Fetch requests
3. I identify the console list endpoint
4. I extract the full request/response
5. I save to dump folder
```

### Gap #2: Console Wake API ✅
**How:**
```
1. You put Xbox in standby (I can't do this)
2. I navigate to /play/consoles/launch/<ID>
3. I monitor Network for wake API call
4. I capture the request before session starts
5. I save to dump folder
```

### Gap #3: DataChannel Messages ⚠️ Partial
**How:**
```
1. I navigate to streaming page
2. I inject JavaScript to hook DataChannel.send()
3. I capture messages as they're sent
4. I log input messages to console
5. I extract and document protocol
```

**Limitation:** Can capture message structure, but need gamepad for input testing

### Gap #4: Error Scenarios ✅
**How:**
```
1. I can simulate network failures
2. I can block specific endpoints
3. I can modify requests to trigger errors
4. I capture all error responses
5. I document error codes
```

---

## MCP Commands I Can Use

### Navigation
- `navigate_to(url)` - Go to specific page
- `wait_for_selector(selector)` - Wait for element
- `click_element(selector)` - Click buttons/links

### Network Monitoring
- `record_network_activity()` - Start capturing
- `get_network_logs()` - Get all requests
- `export_har()` - Save HAR file

### Console Access
- `evaluate_javascript(code)` - Run JS in page
- `get_console_logs()` - Get console output

### Screenshots
- `take_screenshot()` - Capture current state

### Performance
- `start_performance_trace()` - Record performance
- `stop_performance_trace()` - Get trace data

---

## Workflow Example: Console Discovery

**Traditional way (manual):**
```
1. Open Brave
2. Open DevTools (F12)
3. Go to Network tab
4. Navigate to /play/consoles
5. Find the API call in hundreds of requests
6. Right-click → Copy as cURL
7. Save somewhere
8. Hope you didn't miss anything
```

**MCP way (assisted):**
```
ME: "Starting console discovery capture..."
[I launch Chrome with DevTools Protocol]
[I navigate to /play/consoles]
[I wait for page load]
[I filter Network tab for XHR/Fetch]
ME: "Found console list API: GET /smartglass/v2/consoles"
[I extract full request headers]
[I extract full response body]
[I sanitize your XUID]
ME: "Saved to: CONSOLE_DISCOVERY_API_SANITIZED.json"
ME: "Console list shows 2 devices: Xbox Series X (Online), Xbox One X (Standby)"
YOU: "Great! Now capture the wake API"
```

You stay focused on the Xbox, I handle the browser automation.

---

## Setup Steps

### Step 1: Start MCP Server
```bash
cd /tmp/chrome-devtools-mcp
node build/src/index.js --no-usage-statistics
```

**What this does:**
- Launches Chrome with remote debugging enabled
- Starts MCP server on stdio
- Waits for my commands

### Step 2: I Connect to MCP
I use the MCP SDK to send commands to the server

### Step 3: Capture Session
I guide you through each capture scenario while controlling the browser

---

## Security & Privacy

### What MCP Can See:
- ✅ All URLs you visit (in the controlled browser)
- ✅ All network requests/responses
- ✅ Page content and console logs
- ✅ Cookies and tokens

### What MCP Can NOT See:
- ❌ Other browser windows (only the one I launch)
- ❌ Your filesystem (unless I make it download something)
- ❌ Other applications
- ❌ Keyboard input to other apps

### Data Sanitization:
- I will redact all tokens before showing you
- I will replace your XUID with `<XUID>`
- I will not save credentials
- Everything stays in the dump folder

---

## Alternative: Manual Capture with My Guidance

If MCP doesn't work with your Brave setup, we can do **guided manual capture:**

1. I tell you: "Open Brave, press F12"
2. I tell you: "Go to Network tab, check 'Preserve log'"
3. I tell you: "Navigate to https://www.xbox.com/en-US/play/consoles"
4. I tell you: "Look for a request with 'console' in the URL"
5. I tell you: "Right-click it → Copy → Copy as cURL → paste here"
6. I parse it and tell you what's next

**Benefit:** I guide you step-by-step so nothing is missed  
**Downside:** Slower than MCP automation

---

## Current Status

✅ MCP Server built and ready  
⚠️ Need to identify correct browser binary  
⏳ Ready to start capture when you are

---

## Next Steps

**Option A: Use MCP with Chrome Dev** (Recommended)
```bash
# I launch Chrome Dev through MCP
# You monitor Xbox state (standby, online, etc.)
# I capture all browser activity
# We fill all gaps in 1-2 hours
```

**Option B: Guided Manual Capture**
```bash
# You operate browser manually
# I tell you exactly what to capture
# We fill gaps in 2-3 hours
```

**Option C: Hybrid**
```bash
# Use MCP for console discovery (automated)
# Use manual for console wake (needs Xbox state changes)
# Use manual for input testing (needs gamepad)
```

**Your choice!** What do you prefer?

---

## Files Created So Far

1. ✅ `01_REMOTE_PLAY_AUTHENTICATION_FLOW.md` - Complete auth spec
2. ✅ `00_CRITICAL_GAPS_ANALYSIS.md` - What we're missing
3. ✅ `QUICK_CAPTURE_GUIDE.txt` - Manual capture steps
4. ✅ `CAPTURED_DATA_ANALYSIS.md` - What you already captured
5. ✅ `MCP_SETUP_GUIDE.md` - This file

**Next:** Actual data capture to fill the gaps

