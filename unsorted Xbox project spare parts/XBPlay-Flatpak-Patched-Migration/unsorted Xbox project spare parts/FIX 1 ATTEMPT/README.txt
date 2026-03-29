================================================================================
XBOX PLAY PWA - SPLASH SCREEN HANG INVESTIGATION
================================================================================
Investigation Date: 2026-03-26
Investigator: Copilot CLI (root)

ROOT CAUSE IDENTIFIED:
----------------------
The Xbox Play PWA gets stuck on the splash screen in Microsoft Edge because it
tries to connect to a WebSocket endpoint that returns HTTP 404:

  wss://chat.xboxlive.com/users/xuid(2535421847897820)/chat/connect
  Response: HTTP 404 Not Found

The app enters an infinite retry loop waiting for the chat service, consuming
34% CPU and never progressing past the splash screen.

WHY EDGE BUT NOT BRAVE:
-----------------------
The same PWA works fine in Brave Browser. The difference is likely:
- Service worker behavior differences
- Timeout handling
- Feature detection triggering different code paths

EVIDENCE:
---------
- Edge renderer: 34.4% CPU, 84% time in futex (waiting)
- WebSocket connection attempts: Taking 4+ hours before timing out
- System calls: 689 futex calls in 15 seconds (retry loop)
- Brave renderer: <3% CPU, fully functional

QUICK FIX:
----------
Run: ./fix_xbplay_pwa.sh

Or manually:
1. Block chat.xboxlive.com: echo "127.0.0.1 chat.xboxlive.com" | sudo tee -a /etc/hosts
2. Kill Edge: flatpak kill com.microsoft.Edge
3. Restart the Xbox Play PWA

ALTERNATIVE:
------------
Use the Brave PWA instance which works correctly.

KEY DIAGNOSTIC FILES:
---------------------
ROOT_CAUSE_WEBSOCKET_404.md - Detailed root cause analysis
FULL_ANALYSIS_REPORT.md - Complete investigation report
edge_vs_brave_comparison_*.json - Side-by-side comparison
deep_investigation_*.json - WebSocket 404 test results
strace_edge_renderer_*.json - System call trace showing loop
gdb_backtraces_*.json - Stack traces from all threads

ALL FILES ORGANIZED IN THIS DIRECTORY FOR YOUR REVIEW.

================================================================================
SUMMARY: App stuck in chat service retry loop due to 404 endpoint.
FIX: Block chat.xboxlive.com or use Brave PWA.
================================================================================
