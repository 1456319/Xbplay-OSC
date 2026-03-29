#!/usr/bin/env python3
"""
Deep investigation: Hook into Edge PWA and extract runtime state
"""

import subprocess
import json
import time
from pathlib import Path

OUTPUT_DIR = Path("/home/deck/dump/xbplay spare parts")

def run_cmd(cmd):
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
        return result.stdout + result.stderr
    except Exception as e:
        return f"Error: {e}"

def find_devtools_port():
    """Find if there's a debug port we can connect to"""
    edge_pid = run_cmd("pgrep -f 'msedge.*enable-features=WebRTCPipeWireCapturer' | head -1").strip()
    
    if not edge_pid:
        return None
    
    # Check cmdline for debug port
    cmdline = run_cmd(f"cat /proc/{edge_pid}/cmdline | tr '\\0' ' '")
    
    # Check for listening ports
    ports = run_cmd(f"lsof -p {edge_pid} -a -i TCP -s TCP:LISTEN 2>/dev/null")
    
    # Try common DevTools ports
    for port in [9222, 9223, 9224, 37647]:
        test = run_cmd(f"curl -s http://localhost:{port}/json/version 2>&1")
        if "Browser" in test or "webSocketDebuggerUrl" in test:
            return port
    
    return None

def inject_javascript_via_xdotool():
    """Use xdotool to inject JavaScript into the page"""
    edge_wid = run_cmd("xdotool search --class msedge 2>/dev/null | head -1").strip()
    
    if not edge_wid:
        return "No Edge window found"
    
    print(f"Found Edge window: {edge_wid}")
    
    # Focus the window
    run_cmd(f"xdotool windowactivate {edge_wid}")
    time.sleep(0.5)
    
    # Open DevTools with F12
    run_cmd(f"xdotool key --window {edge_wid} F12")
    time.sleep(2)
    
    # Try to get console
    run_cmd(f"xdotool key --window {edge_wid} ctrl+shift+j")
    time.sleep(1)
    
    return "DevTools opened"

def check_console_errors():
    """Try to extract console errors from Edge"""
    # Look for Edge log files
    edge_logs = run_cmd("find ~/.var/app/com.microsoft.Edge -name 'chrome_debug.log' -o -name '*.log' -mmin -30 2>/dev/null")
    
    if edge_logs.strip():
        latest_log = edge_logs.strip().split('\n')[0]
        log_content = run_cmd(f"tail -500 {latest_log}")
        return log_content
    
    return "No recent logs found"

def check_browser_detection():
    """Check what the app sees as browser identity"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not edge_renderer:
        return "No renderer found"
    
    # Look for User-Agent strings in memory (crude but effective)
    # This is safe on Linux - just reading memory maps
    maps = run_cmd(f"cat /proc/{edge_renderer}/maps | grep -E 'heap' | head -1")
    
    info = {
        'renderer_pid': edge_renderer,
        'maps_sample': maps[:500]
    }
    
    return info

def try_websocket_manually():
    """Try to connect to the WebSocket that's failing"""
    print("Testing WebSocket connection to chat.xboxlive.com...")
    
    # Use curl to test WebSocket upgrade
    result = run_cmd("curl -i -N -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: test' https://chat.xboxlive.com 2>&1")
    
    return result

def capture_page_source_via_debugger():
    """Try to get page source via gdb"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not edge_renderer:
        return "No renderer"
    
    print(f"Attempting to extract JavaScript state from renderer {edge_renderer}...")
    
    # Get register state which might contain pointers to JS objects
    regs = run_cmd(f"gdb -batch -p {edge_renderer} -ex 'info registers' -ex 'quit' 2>&1")
    
    return regs[:2000]

def check_flatpak_permissions():
    """Check what permissions the Edge Flatpak has"""
    permissions = run_cmd("flatpak info --show-permissions com.microsoft.Edge 2>/dev/null")
    overrides = run_cmd("flatpak override --show com.microsoft.Edge 2>/dev/null")
    
    return {
        'permissions': permissions,
        'overrides': overrides
    }

def check_service_worker_state():
    """Look for service worker cache/state"""
    sw_cache = run_cmd("find ~/.var/app/com.microsoft.Edge/config/microsoft-edge -name '*service*' -o -name '*worker*' -o -name '*cache*' 2>/dev/null | head -20")
    
    # Check IndexedDB
    indexeddb = run_cmd("find ~/.var/app/com.microsoft.Edge/config/microsoft-edge -path '*/IndexedDB/*' 2>/dev/null | head -20")
    
    return {
        'service_worker_files': sw_cache,
        'indexeddb': indexeddb
    }

def capture_javascript_errors_live():
    """Use gdb to catch JavaScript exceptions"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not edge_renderer:
        return "No renderer"
    
    print(f"Setting breakpoint on exception handlers in renderer {edge_renderer}...")
    
    # Try to catch V8 exception
    gdb_script = """
    set pagination off
    catch throw
    continue
    bt 10
    quit
    """
    
    result = run_cmd(f"timeout 5 gdb -batch -p {edge_renderer} -ex 'set pagination off' -ex 'info threads' -ex 'quit' 2>&1")
    
    return result[:2000]

def main():
    timestamp = int(time.time())
    print("=" * 80)
    print("Deep Investigation: Live Edge PWA Analysis")
    print("=" * 80)
    
    results = {}
    
    # 1. Find DevTools port
    print("\n[1/9] Looking for DevTools port...")
    devtools_port = find_devtools_port()
    results['devtools_port'] = devtools_port
    print(f"DevTools port: {devtools_port or 'Not found'}")
    
    # 2. Check Flatpak permissions
    print("\n[2/9] Checking Flatpak permissions...")
    results['permissions'] = check_flatpak_permissions()
    
    # 3. Check service worker state
    print("\n[3/9] Checking service worker state...")
    results['service_worker'] = check_service_worker_state()
    
    # 4. Try to open DevTools
    print("\n[4/9] Attempting to open DevTools...")
    results['devtools_injection'] = inject_javascript_via_xdotool()
    
    # 5. Check console errors
    print("\n[5/9] Looking for console errors...")
    results['console_errors'] = check_console_errors()
    
    # 6. Browser detection
    print("\n[6/9] Checking browser detection...")
    results['browser_detection'] = check_browser_detection()
    
    # 7. Test WebSocket manually
    print("\n[7/9] Testing WebSocket connection...")
    results['websocket_test'] = try_websocket_manually()
    
    # 8. Capture JavaScript state
    print("\n[8/9] Capturing JavaScript state...")
    results['js_errors'] = capture_javascript_errors_live()
    
    # 9. Check page source via debugger
    print("\n[9/9] Extracting register state...")
    results['register_state'] = capture_page_source_via_debugger()
    
    # Save
    output_file = OUTPUT_DIR / f"deep_investigation_{timestamp}.json"
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\n{'=' * 80}")
    print(f"Investigation complete! Saved to: {output_file}")
    print(f"{'=' * 80}")
    
    # Print key findings
    print("\nKEY FINDINGS:")
    print(f"  DevTools Available: {devtools_port is not None}")
    print(f"  Service Worker Files: {len(results['service_worker']['service_worker_files'].split())}")
    print(f"  Console Errors: {len(results['console_errors'].split('\\n'))} lines")
    
    if devtools_port:
        print(f"\n*** DevTools accessible at: http://localhost:{devtools_port} ***")

if __name__ == "__main__":
    main()
