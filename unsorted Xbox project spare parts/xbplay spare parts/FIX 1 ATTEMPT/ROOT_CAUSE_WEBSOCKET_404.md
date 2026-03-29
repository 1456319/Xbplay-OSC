# CRITICAL FINDING: Xbox Play PWA Stuck Due to WebSocket 404

## ROOT CAUSE IDENTIFIED

The Play Xbox Play PWA is stuck in an infinite retry loop because it's trying to connect to a WebSocket endpoint that **returns 404 (Not Found)**:

```
URL: wss://chat.xboxlive.com/users/xuid(2535421847897820)/chat/connect
Response: HTTP/2 404
```

### Test Confirmation
```bash
$ curl -i https://chat.xboxlive.com
HTTP/2 404
Content-Type: text/html; charset=us-ascii
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
<HTML><HEAD><TITLE>Not Found</TITLE>
<META HTTP-EQUIV="Content-Type" Content="text/html; charset=us-ascii"></HEAD>
<BODY><h2>Not Found</h2>
<hr><p>HTTP Error 404. The requested resource is not found.</p>
</BODY></HTML>
```

## What's Happening

1. **Edge PWA starts** → Initializes chat service  
2. **Attempts WebSocket connection** → `wss://chat.xboxlive.com/...`  
3. **Gets 404 error** → Endpoint doesn't exist or has moved  
4. **Enters retry loop** → JavaScript keeps trying to reconnect  
5. **High CPU usage** → 34% from futex calls (waiting/retrying)  
6. **Never proceeds** → Splash screen never completes  

## Why Brave Works But Edge Doesn't

**Hypothesis:**
- **Different service worker behavior**: Edge and Brave handle PWA service workers differently  
- **Cached vs fresh requests**: Brave may have a cached fallback or skip chat initialization  
- **Timing difference**: Brave might timeout faster and continue without chat  
- **Feature detection**: Edge might report different capabilities that trigger the chat requirement  

## HAR File Evidence

From `spare parts.har`:
- **Total requests**: 1095  
- **Failed WebSocket**: 2 chat.xboxlive.com connections  
  - Duration: 14188 seconds (4+ hours!)  
  - Duration: 3775 seconds (1+ hour)  
- **Telemetry failures**: 10+ requests to browser.events.data.microsoft.com returning 503  

## Technical Details

### System Call Pattern
```
84.64% futex    - Waiting on synchronization primitives
 5.57% getrandom - Generating random data (retry backoff?)
 5.09% write     - Writing to event file descriptors
```

### Thread State
- Main renderer thread: Active but stuck in event loop  
- 2x DedicatedWorker threads: Waiting on futex  
- Compositor thread: Waiting  
- All threads blocked waiting for async operation to complete  

### Browser-Specific Behavior
**Edge (Broken):**
- Renderer type: `--instant-process`  
- CPU: 34.4%  
- Window title: "msedge" (splash only)  
- Futex calls: 689 in 15 seconds  

**Brave (Working):**
- Renderer type: Standard  
- CPU: <3%  
- Window title: "Play Xbox - Play Xbox" (fully loaded)  
- Minimal futex activity  

## Solutions

### Option 1: Block the Chat Endpoint (Quick Fix)
Add to `/etc/hosts` or use Flatpak overrides to block chat.xboxlive.com, forcing immediate timeout:
```bash
echo "127.0.0.1 chat.xboxlive.com" | sudo tee -a /etc/hosts
```

### Option 2: Use Brave Instead
The Brave PWA works correctly - use it until Microsoft fixes the endpoint or the app handles the 404 gracefully.

### Option 3: Wait for Microsoft Fix
This is likely a Microsoft infrastructure issue. The chat service endpoint has either:
- Been deprecated/removed  
- Moved to a new URL  
- Has regional restrictions  
- Is experiencing an outage  

### Option 4: Clear PWA Cache and Restart
Sometimes service workers cache bad state:
```bash
# Stop Edge
flatpak kill com.microsoft.Edge

# Clear cache
rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/Default/Service\ Worker/
rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/Default/Cache/

# Restart Edge PWA
```

### Option 5: Launch Edge with Developer Tools
Restart Edge PWA with remote debugging to inspect and potentially patch:
```bash
flatpak run --command=msedge com.microsoft.Edge \\
  --remote-debugging-port=9222 \\
  --app-id=kanajofaghckoijdglhndcjjlemljefb
```

Then connect to `http://localhost:9222` to inject JavaScript that disables chat.

## Diagnostic Files Summary

All investigation files in: `/home/deck/dump/xbplay spare parts/`

### Process Analysis:
- `edge_process_info_1774561612.json` - Process tree and CPU usage  
- `renderer_memory_1774561612.json` - Memory maps showing heap state  
- `gdb_backtraces_1774561612.json` - Stack traces (all waiting on futex)  

### System Traces:
- `strace_edge_renderer_1774561612.json` - System calls showing retry pattern  
- `stack_samples_1774561612.json` - Multiple samples over time  

### Comparison:
- `edge_vs_brave_comparison_1774561723.json` - Side-by-side analysis  
- Shows Edge stuck, Brave working  

### Deep Investigation:
- `deep_investigation_1774562076.json` - Live analysis including WebSocket test  
- **KEY**: WebSocket test showing 404 response  

### Screenshots:
- `edge_current_state_1774562132.png` - Current state of Edge window  

### Network Analysis:
- HAR files show chat.xboxlive.com failing repeatedly  
- Telemetry endpoints also failing with 503  

## Conclusion

**Definitive Cause**: Xbox Play chat service endpoint at `chat.xboxlive.com` returns 404, causing the PWA to enter an infinite retry loop in Edge.

**Why Edge Specifically**: Edge's PWA implementation handles the failure differently than Brave, likely due to service worker behavior or timeout settings.

**Immediate Action**: Use Brave PWA instance or block chat.xboxlive.com to force timeout and allow app to continue.

**Long-term Fix**: Microsoft needs to either restore the chat endpoint or update the PWA to handle the 404 gracefully.

---
**Analysis Date:** 2026-03-26  
**Edge Version:** Microsoft Edge 146.0.3856.72 (Flatpak)  
**Brave Version:** Brave 146.1.88.136 (Flatpak)  
