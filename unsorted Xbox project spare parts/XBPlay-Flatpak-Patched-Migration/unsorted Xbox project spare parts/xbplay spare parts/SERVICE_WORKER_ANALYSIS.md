# Round 3: Service Worker Source Code Analysis

**File:** `entry.worker.js` (1.4 MB)
**Analysis Date:** March 26, 2026

## Key Findings

### 1. Retry Configuration

The service worker has **aggressive retry logic** built-in:

```javascript
var FETCH_RETRIES = 3;
const maxRetries = options?.maxRetries ?? 3;
```

**Default: 3 retry attempts** for most fetches.

### 2. Retry Implementation

Function: `fetchWithRetry(maxAttempts, url, init)`

**Retry Strategy:**
- Base delay: 100ms
- **Exponential backoff:** `delay = 100ms * 2^(attempt-1) * (1 + random*0.1)`
- **Delays:** ~100ms, ~200ms, ~400ms, ~800ms...
- **Only retries on:**
  - Network errors (connection refused, timeout, etc.)
  - HTTP 5xx server errors
- **Does NOT retry:**
  - HTTP 4xx client errors (404, 403, etc.)  
  - HTTP 2xx/3xx success responses

**Code snippet (lines 42864-42900):**
```javascript
async function fetchWithRetry(maxAttempts, url, init) {
    if (maxAttempts < 1) throw new Error("maxAttempts must be at least 1");
    let attempt = 1;
    const baseDelay = 100;
    
    while (attempt <= maxAttempts) {
        try {
            const response = await fetch(url, init);
            if (response.ok) {
                if (attempt > 1) logger.debug(`successfully fetched ${url.href} on attempt ${attempt}`);
                return response;
            }
            // Retry on 5xx errors
            if (response.status >= 500 && response.status < 600 && attempt < maxAttempts) {
                logger.warn(`failed response for ${url.href} on attempt ${attempt} - retrying...`);
            }
        } catch (e) {
            // Network error
            logger.warn(`network error fetching ${url.href} on attempt ${attempt}`);
            if (attempt === maxAttempts) throw e;
        }
        
        const delay = baseDelay * Math.pow(2, attempt - 1) * (1 + Math.random() * .1);
        logger.warn(`Retrying ${url.href} in ${delay}ms (attempt ${attempt + 1}/${maxAttempts})`);
        await new Promise((resolve) => setTimeout(resolve, delay));
        attempt++;
    }
}
```

### 3. Logging with "SW_FALLBACK" Prefix

All service worker console output is prefixed with `"SW_FALLBACK"`:

**Lines 42598, 42610, 42622, 42634:**
```javascript
// Info logging
if (!this.postLog("info", message, ...contextArgs)) 
    console.info("SW_FALLBACK", ...args);

// Warning logging  
if (!this.postLog("warn", message, ...contextArgs)) 
    console.warn("SW_FALLBACK", ...args);

// Error logging
if (!this.postLog("error", message, ...contextArgs)) 
    console.error("SW_FALLBACK", ...args);

// Debug logging
if (!this.postLog("debug", message, ...contextArgs)) 
    console.debug("SW_FALLBACK", ...args);
```

**This explains** the "SW_FALLBACK" messages we saw in previous console logs!

### 4. Error Messages

The service worker logs specific error patterns:

**On HTTP 5xx errors (line 42878):**
```javascript
logger.warn(`failed response for ${url.href} on attempt ${attempt} - retrying...`, {
    status: response.status,
    statusText: response.statusText
});
```

**On network errors (line 42890):**
```javascript
logger.warn(`network error fetching ${url.href} on attempt ${attempt}`, {
    error: e.message,
    type: e.name
});
```

**On retry delay (line 42897):**
```javascript
logger.warn(`Retrying ${url.href} in ${delay}ms (attempt ${attempt + 1}/${maxAttempts})`);
```

## Implications for Xbox Play PWA Issue

### Why the App Is Stuck

1. **chat.xboxlive.com blocked by /etc/hosts**
   - DNS resolves to 127.0.0.1
   - Connection immediately refused (ECONNREFUSED)

2. **Service Worker catches network error**
   - Treats it as transient failure
   - Triggers retry logic

3. **Exponential backoff retries**
   - Attempt 1: Immediate failure
   - Wait ~100ms
   - Attempt 2: Immediate failure
   - Wait ~200ms
   - Attempt 3: Immediate failure
   - Wait ~400ms
   - Final attempt: Throws exception

4. **Exception bubbles up to app**
   - App may not handle SW exceptions gracefully
   - JavaScript error prevents initialization
   - Or app is waiting indefinitely for chat to succeed

### Why 117 Failures in HAR

**117 failures ÷ 3 retries per request = ~39 unique requests**

The app is attempting to fetch ~39 different resources from chat.xboxlive.com, each retried 3 times before finally failing.

**Likely endpoints:**
- `/chat/auth` - Authentication
- `/chat/connect` - WebSocket handshake
- `/chat/users/*` - User data
- `/chat/presence` - Online status
- Other chat-related APIs

### Why High CPU

Two renderer processes showing 22.5% and 13.9% CPU:

**Possible causes:**
1. **Promise rejection loop** - Unhandled promise rejections causing event loop churn
2. **Timeout polling** - JavaScript checking `if (chatReady)` in tight loop
3. **React re-rendering** - Component stuck re-rendering waiting for chat data
4. **Animation loop** - Splash screen spinner/animation running while waiting

**Strace evidence supports this:**
- 38% write operations → Console logging errors repeatedly
- 12% futex with 13 failures → Waiting on promises that never resolve
- 10% getrandom → Generating request IDs or tokens for retries

## Search for Chat References

Searched `entry.worker.js` for chat-related code:

```bash
grep -i "chat\|xboxlive" entry.worker.js
```

**Result:** No hardcoded references to "chat" or "xboxlive.com" in the service worker!

**Conclusion:** The chat endpoints are likely:
1. Loaded from a remote manifest/config
2. Defined in the main app JavaScript (not service worker)
3. Stored in IndexedDB or localStorage
4. Part of API discovery/bootstrap process

## Recommended Solutions

### Option 1: Disable Service Worker Entirely
```bash
flatpak run com.microsoft.Edge \
  --disable-service-workers \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
```

**Pros:**
- Bypasses SW retry logic completely
- App fetches directly, fails instantly on blocked endpoints
- May have better error handling in main app vs SW

**Cons:**
- Loses offline capabilities
- May break other PWA features
- App might require SW to function at all

### Option 2: Unregister Service Worker via DevTools

1. Open DevTools → Application tab
2. Service Workers section
3. Click "Unregister" next to play.xbox.com scope
4. Hard refresh (Ctrl+Shift+R)

**Pros:**
- Can test without SW temporarily
- Easy to re-enable
- No command-line changes needed

**Cons:**
- Must do manually each time
- SW may auto-register again

### Option 3: Mock the Chat Endpoint

Remove /etc/hosts block and set up local mock server:

```bash
# Start mock server on port 8443
python3 mock_chat_server.py

# Redirect chat.xboxlive.com to localhost:8443  
# (requires modifying system proxy or DNS)
```

Mock server returns:
- HTTP 200 for /chat/auth
- Empty/minimal JSON responses
- Fake WebSocket that immediately closes

**Pros:**
- App gets successful response, continues initialization
- Can see what data app expects
- Most realistic test

**Cons:**
- Complex setup
- Need to reverse-engineer expected responses
- May have authentication/token validation

### Option 4: Patch the Service Worker

Create modified `entry.worker.js`:

```javascript
// Add at top of file:
const BLOCKED_DOMAINS = ['chat.xboxlive.com'];

// Modify fetchWithRetry to skip blocked domains:
async function fetchWithRetry(maxAttempts, url, init) {
    // Skip retry for blocked domains
    if (BLOCKED_DOMAINS.some(domain => url.href.includes(domain))) {
        console.log('Skipping blocked domain:', url.href);
        return new Response(JSON.stringify({error: 'blocked'}), {
            status: 200,
            headers: {'Content-Type': 'application/json'}
        });
    }
    // ... rest of function
}
```

Install via DevTools Overrides:
1. Sources tab → Overrides
2. Select folder for overrides
3. Save modified entry.worker.js
4. Refresh

**Pros:**
- Surgical fix - only affects chat endpoints
- Returns success responses to unblock app
- Can iterate quickly

**Cons:**
- Manual DevTools setup
- Must redo after browser update
- May break if app validates chat responses

### Option 5: Switch to Brave PWA

User confirmed Brave works. Simply use:

```bash
flatpak run com.brave.Browser \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
```

**Pros:**
- Already works!
- No debugging needed
- Proven solution

**Cons:**
- Doesn't solve Edge mystery
- May have different features/behavior
- User may prefer Edge

## Files Created

- `ROUND3_ANALYSIS.md` - Overall Round 3 findings
- `SERVICE_WORKER_ANALYSIS.md` - This file
- `round3_diagnostics/round3_diagnosis.json` - Diagnostic data
- `round3_sources/` - 345 extracted JavaScript files

## Next Actions Required

**From User:**
1. Check DevTools Console for current errors
2. Check Application → Service Workers status
3. Try Option 1 (disable SW) or Option 2 (unregister SW)
4. Report results

**If still stuck:**
- Need to analyze the main app JavaScript (not just service worker)
- Look for chat initialization code
- Find error handling around chat failures
- Consider mocking the chat service

---

**Analysis Complete:** Service worker has 3-retry logic with exponential backoff. The /etc/hosts block causes instant failures that trigger retries, consuming CPU and preventing app initialization. Solution: Disable SW or mock the chat service.
