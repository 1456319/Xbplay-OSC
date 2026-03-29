=============================================================================
XBOX PLAY PWA TROUBLESHOOTING - ROUND 3 COMPLETE
=============================================================================

Status: INVESTIGATING (Source code analyzed, awaiting user input)
Date: March 26, 2026

QUICK START
-----------
1. Read: ROUND3_QUICK_REFERENCE.md (fastest overview)
2. Try: Fix 1 from quick reference (disable service worker)
3. Report: What you see in DevTools Console

WHAT WE ACCOMPLISHED THIS ROUND
--------------------------------
✅ Downloaded all JavaScript sources from HAR files (345 files)
✅ Analyzed service worker code (entry.worker.js)
✅ Found retry logic: 3 attempts with exponential backoff
✅ Confirmed 117 new failed requests to chat.xboxlive.com
✅ Identified 2 high-CPU renderer processes
✅ Created comprehensive analysis documents

KEY FINDING
-----------
Service worker has aggressive retry logic that treats the /etc/hosts block
as a "transient network error" and retries 3 times with delays. The app
doesn't handle the final failures gracefully, preventing initialization.

RECOMMENDED FIX
---------------
Disable the service worker entirely:

  flatpak run com.microsoft.Edge \
    --disable-service-workers \
    --profile-directory=Default \
    --app-id=kanajofaghckoijdglhndcjjlemljefb \
    '--app-url=https://play.xbox.com/?pwaVersion=1.0'

ALTERNATIVE: Switch to Brave (already confirmed working)

DOCUMENTS IN THIS ROUND
------------------------

📄 START HERE:
   ROUND3_QUICK_REFERENCE.md - Fast overview and action items
   
📊 DETAILED ANALYSIS:
   ROUND3_ANALYSIS.md - Complete troubleshooting report
   SERVICE_WORKER_ANALYSIS.md - Service worker code analysis
   
📁 DATA:
   round3_sources/ - 345 extracted JavaScript files
   round3_diagnostics/ - Process traces and diagnostic JSON
   
📝 LOGS:
   round3_extraction.log - Source extraction output
   round3_troubleshoot.log - Diagnostic script output

SCRIPTS CREATED:
   round3_extract_sources.py - Extract JS from HAR (with fallbacks)
   round3_troubleshoot.py - Comprehensive diagnostic suite
   round3_extract_simple.sh - Simple filesystem extraction

WHAT WE LEARNED
----------------

1. Service Worker Retry Logic:
   - 3 retries per request
   - Exponential backoff: 100ms → 200ms → 400ms → 800ms
   - Retries network errors and HTTP 5xx
   - Does NOT retry HTTP 4xx

2. Why Still Stuck:
   - /etc/hosts block works (instant connection refused)
   - SW treats as transient error → triggers retries
   - ~39 unique requests × 3 retries = 117 failures in HAR
   - App doesn't handle final failure
   - Stuck waiting for chat to work

3. Process State:
   - 6 renderer processes (excessive)
   - 2 with high CPU: 22.5% and 13.9%
   - Strace: 38% write, 27% madvise, 12% futex
   - Pattern: logging + promise waiting

4. Source Code:
   - No hardcoded chat URLs in service worker
   - All SW logs prefixed "SW_FALLBACK"
   - Chat endpoints loaded from remote config/manifest
   - FETCH_RETRIES constant = 3

ROUNDS SUMMARY
--------------

Round 1: Identified WebSocket wss://chat.xboxlive.com 404 issue
         Applied /etc/hosts block → Still stuck

Round 2: Found service worker retry storm (247 failures)
         Cleared all caches → Still stuck

Round 3: Analyzed service worker source code
         Found retry logic causing the issue
         Recommendation: Disable SW or mock service

WHAT YOU NEED TO PROVIDE
-------------------------

CRITICAL:
1. DevTools Console tab - screenshot or copy ALL errors
2. Application → Service Workers - status and scope

HELPFUL:
3. Network tab - failed requests screenshot
4. Application → Cache Storage - list of caches

NEXT STEPS
----------

1. Try recommended fix (disable service worker)
2. If that fails, try Brave PWA (confirmed working)
3. Report back with:
   - What you tried
   - What happened
   - Any new error messages

TECHNICAL DETAILS
-----------------

Current Network State:
  ✅ https://play.xbox.com - HTTP 200 (working)
  ❌ https://chat.xboxlive.com - Blocked by /etc/hosts
  ❌ wss://chat.xboxlive.com - Blocked by /etc/hosts

Service Worker Locations:
  /home/deck/.var/app/com.microsoft.Edge/config/microsoft-edge/
    Default/Service Worker/ - 551 files
    Profile 1/Service Worker/ - 741 files
    Profile 2/Service Worker/ - 784 files

Running Processes (at time of analysis):
  PID 162593 - 22.5% CPU, 2.5% RAM
  PID 163211 - 13.9% CPU, 4.6% RAM
  + 4 other renderers with low CPU

Files Created:     19 (scripts, logs, analysis docs)
Sources Extracted: 345 JavaScript files
HAR Analyzed:      Attempt3.har (117 failures)
Time Spent:        ~45 minutes

RULE REMINDER
-------------
NOTHING in "/home/deck/dump/xbplay spare parts/" will EVER be deleted.
Everything stays for historical reference.

=============================================================================
Questions? Check ROUND3_QUICK_REFERENCE.md for fast answers.
Need details? Read ROUND3_ANALYSIS.md and SERVICE_WORKER_ANALYSIS.md.
Ready to fix? Try the command in "RECOMMENDED FIX" above.
=============================================================================
