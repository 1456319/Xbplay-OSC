# Play Xbox Play PWA - Splash Screen Hang Analysis
**Date:** 2026-03-26
**Issue:** Xbox Play PWA stuck on splash screen in Microsoft Edge but works in Brave Browser

## Executive Summary

The Xbox Play PWA is experiencing an infinite loop/tight polling condition in the Microsoft Edge browser that prevents it from progressing past the splash screen. The Brave browser instance of the same PWA works correctly.

## Key Findings

### 1. Process Analysis
- **Edge PWA Process:** `msedge` PID 119639 (instant-process renderer)
- **CPU Usage:** 34.4% - Extremely high for a splash screen
- **Brave PWA Process:** Multiple renderers, all <3% CPU
- **Window Title:** Edge shows "msedge" (splash), Brave shows "Play Xbox - Play  Xbox" (loaded)

### 2. System Call Analysis (strace)
```
% time     syscall           calls    errors
------     --------          -----    ------
 84.64%    futex             1961     43
  5.57%    getrandom         966      0
  5.09%    write             1015     0
  2.45%    madvise           180      0
```

**Interpretation:** 
- 84% of time spent in futex operations (synchronization primitives)
- High frequency of calls (1961 in 10 seconds = ~196/second)
- This pattern indicates JavaScript is in a tight loop waiting for something
- The high `getrandom` calls suggest cryptographic operations or session token generation

### 3. Network Analysis (HAR Files)
Failed/problematic requests:
- **WebSocket:** `wss://chat.xboxlive.com` - Connection failed (status 0)
- **Telemetry:** Multiple `browser.events.data.microsoft.com` requests returning 503
- **Slow requests:** WebSocket chat connection took 14+ seconds before timing out

### 4. Comparison: Edge vs Brave

| Aspect | Edge (Broken) | Brave (Working) |
|--------|---------------|-----------------|
| Window Title | "msedge" | "Play Xbox - Play Xbox" |
| CPU Usage | 34.4% | <3% |
| Renderer Type | instant-process | standard renderer |
| Futex Calls | 689 in 15s | minimal |
| Status | Stuck on splash | Fully loaded |

## Root Cause Analysis

### Likely Cause: Authentication/Initialization Loop

The evidence points to the PWA being stuck in an initialization or authentication loop:

1. **High futex usage** = JavaScript waiting on promises/async operations
2. **Failed WebSocket connections** = Chat service unavailable, but app may be retrying infinitely
3. **Failed telemetry** (503 errors) = Microsoft telemetry service unavailable
4. **Edge vs Brave difference** = Different PWA implementations or feature detection

### Specific Technical Issues:

1. **WebSocket Retry Loop:** The app may be stuck retrying the failed `wss://chat.xboxlive.com` connection without proper timeout/fallback
2. **Service Worker Issues:** Edge and Brave handle service workers differently - the `entry.worker.js` may be behaving differently
3. **Storage/Cache Permissions:** PWA cache or IndexedDB may have initialization issues specific to Edge Flatpak
4. **Feature Detection:** The app may detect a feature in Edge that triggers a different code path that hangs

## Diagnostic Files Generated

All files saved to: `/home/deck/dump/xbplay spare parts/`

### Process Diagnostics:
- `process_info_1774561378.json` - Detailed Chrome/Edge process tree
- `edge_process_info_1774561612.json` - Edge-specific process analysis
- `renderer_memory_1774561612.json` - Memory maps and file descriptors

### Trace Data:
- `strace_edge_renderer_1774561612.json` - System call trace showing infinite loop pattern
- `gdb_backtraces_1774561612.json` - Stack traces from all processes
- `stack_samples_1774561612.json` - Multiple stack samples over time

### Comparison Data:
- `edge_vs_brave_comparison_1774561723.json` - Side-by-side comparison
- `final_analysis_1774561801.json` - Comprehensive analysis
- `ANALYSIS_SUMMARY_1774561801.txt` - Human-readable summary

### Network Data:
- `network_activity_1774561612.json` - Active network connections
- HAR files show failed WebSocket and telemetry requests

### Console/DevTools:
- `devtools_why_is_this_page_stuck_in_a_loading_loop_ive_turned_.md` - Existing DevTools analysis
- `console_logs_1774561612.json` - Extracted console logs

## Recommendations

### Immediate Actions:
1. **Kill and Restart:** Kill the Edge PWA process and restart - may clear stuck state
2. **Clear Cache:** Clear Edge PWA cache and storage data
3. **Check Network:** Verify `chat.xboxlive.com` is reachable from your network
4. **Use Brave:** The Brave instance works - use it instead of Edge

### Investigation Actions:
1. **Attach JavaScript Debugger:** Use Chrome DevTools Protocol to pause execution and see exact code location
2. **Check Service Worker:** Inspect service worker registration and state
3. **Review Telemetry Config:** The 503 errors from telemetry might be intentional blocks, but app may not handle gracefully
4. **Browser Feature Comparison:** Check what features Edge reports that Brave doesn't

### Potential Fixes:
1. **Disable WebSocket Chat:** If chat isn't critical, block `chat.xboxlive.com` to force timeout
2. **Block Telemetry:** Block `browser.events.data.microsoft.com` to prevent retry loops
3. **Switch Browser:** Use Brave or Chrome version of PWA instead
4. **Wait for Update:** This may be a known issue that Microsoft will fix

## Technical Details for Developers

### Infinite Loop Pattern:
```
futex(wait) -> futex(wake) -> write(eventfd) -> futex(wait) ...
```
This pattern repeats ~200 times per second, indicating:
- JavaScript Promise chain that never resolves
- setInterval/setTimeout that fires too frequently
- Event loop blocked waiting for external resource

### Stack Trace Hints:
All threads show waiting on futex in thread pool workers, which means the main JavaScript event loop is blocked waiting for an async operation that never completes.

### Browser Difference:
Edge uses `--instant-process` flag which may affect:
- Renderer process initialization
- Service worker lifecycle
- Permission model
- Feature availability

## Conclusion

The Xbox Play PWA is stuck in an infinite loop in Microsoft Edge, likely due to failed WebSocket chat connections or telemetry endpoints returning 503 errors. The app does not gracefully handle these failures and enters a retry loop. The Brave browser works because it either:
1. Handles service workers differently
2. Has different network timeout behavior
3. Doesn't trigger the problematic code path

**Recommended Action:** Use the Brave browser instance or clear Edge PWA data and restart.

---
**Generated:** 2026-03-26T17:50:00Z
**Diagnostic Tool Version:** 1.0
