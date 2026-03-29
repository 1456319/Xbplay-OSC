# Round 3: Xbox Play PWA Troubleshooting Analysis

**Date:** March 26, 2026 19:57
**Status:** INVESTIGATING - App still stuck on splash screen after Round 2 fixes

## Summary

After Round 2 (blocking chat.xboxlive.com + clearing caches), the Xbox Play PWA is still stuck on splash screen. Round 3 investigation reveals:

- **345 JavaScript source files extracted** from HAR files
- **117 failed requests** to chat.xboxlive.com in latest HAR (Attempt3.har)
- **2 high-CPU renderer processes** (22.5% and 13.9% CPU)
- **6 renderer processes total** (excessive for a single PWA)
- **chat.xboxlive.com still blocked** in /etc/hosts (as intended)

## Key Findings

### 1. Network Status
- ✅ Main site accessible: `https://play.xbox.com` → HTTP 200
- ❌ Chat endpoint blocked: `https://chat.xboxlive.com` → HTTP 000 (blocked by /etc/hosts)
- ❌ WebSocket endpoint blocked: `wss://chat.xboxlive.com` → HTTP 000 (blocked by /etc/hosts)

### 2. Process State
6 Edge renderer processes detected:

| PID    | CPU   | Memory | Status |
|--------|-------|--------|--------|
| 154751 | 0.2%  | 0.9%   | Idle   |
| 156610 | 0.7%  | 0.7%   | Low    |
| 162442 | 0.0%  | 0.9%   | Idle   |
| **162593** | **22.5%** | **2.5%**   | **High** |
| **163211** | **13.9%** | **4.6%**   | **High** |
| 163318 | 0.0%  | 0.4%   | Idle   |

**Problem:** PIDs 162593 and 163211 showing sustained high CPU usage.

### 3. System Call Analysis (PID 163211)
2-second strace sample revealed:
- 38% write operations
- 27% madvise (memory management)
- 12% futex (synchronization/waiting)
- 10% getrandom (crypto operations)

**Pattern:** Active processing but not frozen. Likely JavaScript executing in loop.

### 4. HAR File Analysis
**Attempt3.har** (latest capture):
- Total requests: Not counted
- **Failed requests: 117**
- **All 117 failures:** chat.xboxlive.com domain
- No other domain failures detected

**Conclusion:** App is STILL attempting to connect to chat endpoints despite /etc/hosts block and cache clearing.

### 5. Service Worker State
Found 3 Edge profiles with active Service Worker caches:
- **Default profile:** 551 files, 6 databases
- **Profile 1:** 741 files, 7 databases  
- **Profile 2:** 784 files, 6 databases

**Problem:** May be using wrong profile or SW cache not properly cleared.

### 6. Source Code Analysis
Extracted sources searched for key patterns:
- ❌ "chat.xboxlive.com" - NOT found in JS sources
- ❌ "SW_FALLBACK" - NOT found  
- ❌ "FetchEvent" - NOT found
- ❌ "network error" - NOT found
- ❌ "retry" - NOT found
- ❌ "timeout" - NOT found

**Note:** Grep may have failed (shell glob issue). Need manual search.

### 7. Active Network Connections
4 established HTTPS connections from Edge (PID 154515 - network process):
- 199.46.35.124:443
- 52.168.117.175:443
- 135.234.174.40:443
- 104.208.203.89:443

**Note:** These are likely legitimate Xbox/Microsoft services.

## Root Cause Theories

### Theory 1: Wrong Profile
The PWA may be running under "Profile 2" (784 SW files) instead of "Default" (551 files). Cache clearing may have only affected Default profile.

**Evidence:**
- Multiple profiles have SW caches
- PWA flatpak command doesn't specify profile
- Largest SW cache is Profile 2

### Theory 2: Service Worker Won't Die
Despite cache clearing, the SW is still registered and attempting requests. The SW_FALLBACK logic may be embedded in the app itself, not the SW.

**Evidence:**
- 117 new chat.xboxlive.com failures in Attempt3.har
- High CPU suggests active retry loop
- /etc/hosts block working (instant failures) but app not handling them

### Theory 3: App Requires Chat to Initialize
The app may have a hard dependency on chat service completing successfully. Blocking prevents timeout, but also prevents the app from deciding to skip chat features.

**Evidence:**
- Still stuck after blocking
- High CPU suggests waiting/retrying
- No graceful degradation

### Theory 4: JavaScript Error After Chat Fails
A JavaScript exception may occur when chat endpoints fail instantly, preventing app initialization.

**Evidence:**
- No console logs available from Round 3
- Need to check DevTools Console tab
- Unhandled promise rejection?

## What Changed Between Rounds

### Round 1 → Round 2
- ✅ Blocked chat.xboxlive.com in /etc/hosts
- ✅ Cleared Service Worker cache
- ✅ Cleared browser cache
- ✅ Killed all Edge PWA processes

### Round 2 → Round 3
- ❓ User restarted PWA manually
- ❓ May be using different profile
- ❓ New console errors may exist

## Data Collected in Round 3

### Files Created
- `round3_sources/` - 345 JavaScript files extracted from HAR
- `round3_diagnostics/round3_diagnosis.json` - Full diagnostic data
- `round3_diagnostics/strace_pid_162593.txt` - High-CPU process trace
- `round3_diagnostics/strace_pid_163211.txt` - High-CPU process trace
- `round3_extract_sources.py` - Source extraction script
- `round3_troubleshoot.py` - Diagnostic script
- `round3_extraction.log` - Extraction log
- `round3_troubleshoot.log` - Diagnostic log

### Existing Files Analyzed
- `Attempt3.har` - Latest network capture showing 117 failures
- `existing_entry.worker.js` - 1.4MB service worker code
- All console logs (none found from Round 3)

## Required User Input

To proceed, need the following from DevTools Console/Application tabs:

### 1. Console Tab
- Screenshot or text of ALL error messages
- Look for:
  - Uncaught exceptions
  - Promise rejections
  - Failed module loads
  - Chat service errors

### 2. Network Tab
- Screenshot showing:
  - Failed requests (red) with status codes
  - Any pending/stuck requests
  - Request timing (how long each takes)
  - Filter by "chat.xboxlive.com" to see all 117 failures

### 3. Application Tab → Service Workers
- Service Worker status: Activated/Installing/Waiting/Error
- Scope: Should be `https://play.xbox.com/`
- Update on reload: Yes/No
- Source: URL of SW script

### 4. Application Tab → Cache Storage
- Number of caches listed
- Cache names
- Size of each cache
- Number of entries in each

### 5. Application Tab → IndexedDB
- Database names
- Number of object stores
- Size if available

## Next Steps (Pending User Input)

### Option A: If Console Shows JavaScript Errors
1. Analyze the error stack traces
2. Find failing code in extracted sources
3. Potentially patch the JavaScript to skip chat features
4. Create local override in DevTools

### Option B: If Service Worker Is Active But Broken
1. Unregister service worker via DevTools
2. Disable service workers entirely: `--disable-service-workers` flag
3. Restart PWA without SW

### Option C: If Wrong Profile
1. Identify which profile PWA is using
2. Clear SW cache from correct profile
3. Or specify correct profile in launch command

### Option D: If App Truly Needs Chat
1. Remove /etc/hosts block
2. Set up local proxy to intercept and mock chat responses
3. Use mitmproxy to return fake but valid chat data
4. Or accept that app won't work without Microsoft's chat service

### Option E: Switch to Brave PWA
User mentioned Brave version works. Could:
1. Compare Brave vs Edge PWA manifests
2. Use Brave PWA permanently
3. Copy Brave's working configuration to Edge

## Technical Details

### /etc/hosts Block
```
127.0.0.1 chat.xboxlive.com
```
**Effect:** DNS resolves chat.xboxlive.com to localhost, causing instant connection refused.

### Edge PWA Launch Command
```bash
flatpak run com.microsoft.Edge \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
```

### Paths
- Edge config: `/home/deck/.var/app/com.microsoft.Edge/config/microsoft-edge/`
- Service Workers: `$EDGE_CONFIG/*/Service Worker/`
- Cache: `$EDGE_CONFIG/*/Cache/`
- IndexedDB: `$EDGE_CONFIG/*/IndexedDB/`

## Comparison: What Works in Brave?

User confirmed Brave PWA (`com.brave.Browser.flextop.brave-kanajofaghckoijdglhndcjjlemljefb-Default`) works correctly.

**Key differences to investigate:**
- Does Brave also fail to connect to chat but handle it gracefully?
- Does Brave use a different service worker implementation?
- Does Brave have different security policies?
- Is Brave using cached content while Edge is trying fresh fetch?

## Logs for Review

### Strace Summary (PID 163211 - High CPU)
```
% time     seconds  usecs/call     calls    errors syscall
------ ----------- ----------- --------- --------- ----------------
 38.21    0.000047           1        46           write
 27.64    0.000034           0        60           madvise
 12.20    0.000015           0        39        13 futex
 12.20    0.000015          15         1           restart_syscall
  9.76    0.000012           1         9           getrandom
```

**Interpretation:**
- Many write calls (38%) - outputting to console/logs?
- Memory management active (27%) - garbage collection?
- Futex errors (13 of 39 calls failed) - waiting on locks that timeout
- Crypto calls (getrandom) - token generation or hashing?

### Network Connections
All connections to Microsoft/Xbox infrastructure IPs, none to chat.xboxlive.com (successfully blocked).

## Status Summary

| Check | Status | Details |
|-------|--------|---------|
| Sources extracted | ✅ | 345 JS files saved |
| Main site accessible | ✅ | HTTP 200 |
| Chat endpoint blocked | ✅ | /etc/hosts working |
| Caches cleared | ⚠️ | May need to clear other profiles |
| High CPU diagnosed | ✅ | PIDs 162593, 163211 identified |
| Console errors logged | ❌ | Need user to provide |
| Service worker status | ❌ | Need user to check |
| Network requests logged | ✅ | 117 failures to chat in HAR |

## Bottom Line

**The app is STILL trying to connect to chat.xboxlive.com** despite blocks and cache clearing. It's failing fast (instant refused connections) but not handling the failures gracefully. Need DevTools Console output to see JavaScript errors preventing initialization.

---

**Created:** 2026-03-26 19:57
**Round:** 3 of troubleshooting
**Priority:** Need DevTools Console/Application tab data from user
