# FINAL DIAGNOSIS: Xbox Play PWA - Service Worker Retry Storm

## Root Cause (Confirmed Round 2)

The Xbox Play PWA gets stuck because:

1. **247 failed chat.xboxlive.com requests** in HAR file
2. **Service worker (entry.worker.js) in infinite retry loop**
3. **Two failing endpoints:**
   - `https://chat.xboxlive.com/users/xuid(...)/chat/auth` 
   - `wss://chat.xboxlive.com/users/xuid(...)/chat/connect`

### Console Evidence:
```
SW_FALLBACK request failed: https://chat.xboxlive.com/users/xuid(2535421847897820)/chat/auth
The FetchEvent for "<URL>" resulted in a network error response (60+ times)
```

### Service Worker Behavior:
The PWA's service worker catches network failures but **keeps retrying indefinitely** instead of failing gracefully or implementing exponential backoff.

## Why It's Stuck

1. PWA initializes
2. Attempts to auth with chat service (`/chat/auth`)
3. Gets network error (due to /etc/hosts block)
4. Service worker logs "SW_FALLBACK" and retries
5. Loop repeats endlessly
6. Main app never progresses past splash screen

## Why Brave Works

Brave either:
- Has different service worker lifecycle behavior
- Implements faster timeout
- Cached a successful session before chat service failed
- Different PWA manifest interpretation

## Applied Fix

1. ✓ Blocked chat.xboxlive.com in /etc/hosts
2. ✓ Killed all Edge processes
3. ✓ Cleared service worker cache
4. ✓ Cleared browser cache

## Expected Result

After restart:
- Service worker rebuilds from scratch
- Chat requests fail immediately (localhost = instant)
- Service worker should timeout and continue
- App loads without chat functionality

## If Still Stuck

The PWA may **require** chat functionality to initialize. In that case:

### Option A: Check the PWA's network tab after restart
Look for:
- Are chat requests still being made?
- Are they timing out faster now?
- Are there OTHER endpoints failing?

### Option B: Try disabling service workers entirely
```bash
# Launch Edge PWA with SW disabled
flatpak run --command=msedge com.microsoft.Edge \
  --disable-service-workers \
  --app=https://play.xbox.com/?pwaVersion=1.0
```

### Option C: Use Brave (guaranteed working)
The Brave PWA works perfectly - use that until Microsoft fixes the chat service.

## Files Generated This Round

- `round2_investigation_*.json` - Full process/network analysis
- `ROUND2_SERVICE_WORKER_ISSUE.md` - Detailed findings
- `complete_fix_round2.sh` - Automated fix script
- `devtools_console_*.png` - Screenshot of console errors
- `play.xbox.com-*CONSOLE_ERRORS.log` - Raw console output
- `fullloginstillstuck.har` - Network traffic showing 247 failures

## Summary

**Problem:** Service worker retry storm on chat.xboxlive.com  
**Fix Applied:** Blocked endpoint + cleared service worker cache  
**Status:** Restart PWA and monitor - may need to disable service workers entirely

---
**Investigation:** Round 2 Complete  
**Date:** 2026-03-26  
**All diagnostic data preserved in spare parts directory**
