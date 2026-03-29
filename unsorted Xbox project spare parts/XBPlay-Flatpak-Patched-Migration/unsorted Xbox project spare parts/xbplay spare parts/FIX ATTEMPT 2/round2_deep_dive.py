#!/usr/bin/env python3
"""
Round 2: Deep dive into Edge PWA - Why still stuck after chat.xboxlive.com block?
Looking for: Other network issues, JavaScript errors, service worker problems
"""

import subprocess
import json
import time
from pathlib import Path
from datetime import datetime

OUTPUT_DIR = Path("/home/deck/dump/xbplay spare parts")

def run_cmd(cmd, timeout=30):
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return result.stdout + result.stderr
    except Exception as e:
        return f"Error: {e}"

def get_edge_processes():
    """Get all Edge PWA process details"""
    pids = run_cmd("pgrep -f 'msedge.*kanajofaghckoijdglhndcjjlemljefb'").strip().split('\n')
    
    processes = {}
    for pid in pids:
        if pid:
            processes[pid] = {
                'cmdline': run_cmd(f"cat /proc/{pid}/cmdline | tr '\\0' ' '"),
                'cpu': run_cmd(f"ps -p {pid} -o %cpu,%mem,rss --no-headers"),
                'status': run_cmd(f"cat /proc/{pid}/status | grep -E 'State|Threads|VmSize|VmRSS'"),
            }
    
    return processes

def strace_all_renderers():
    """Strace all renderer processes to see what they're doing"""
    renderers = run_cmd("pgrep -f 'msedge.*type=renderer'").strip().split('\n')
    
    traces = {}
    for pid in renderers[:5]:  # First 5
        if pid:
            print(f"Tracing renderer {pid} for 10 seconds...")
            trace = run_cmd(f"timeout 10 strace -p {pid} -c 2>&1 || true")
            traces[pid] = trace
    
    return traces

def check_network_activity():
    """Check what network connections are active"""
    connections = run_cmd("lsof -i -P -n | grep msedge")
    
    # Check for failed connections
    netstat = run_cmd("ss -tan | grep -E 'SYN_SENT|TIME_WAIT' | head -20")
    
    return {
        'active_connections': connections,
        'stuck_connections': netstat
    }

def get_console_with_devtools():
    """Try to extract console errors using xdotool to navigate DevTools"""
    edge_wid = run_cmd("xdotool search --class msedge 2>/dev/null | head -1").strip()
    
    if not edge_wid:
        return "No window found"
    
    print(f"Opening DevTools on window {edge_wid}...")
    
    # Focus window
    run_cmd(f"xdotool windowactivate {edge_wid}")
    time.sleep(1)
    
    # Open DevTools
    run_cmd(f"xdotool key --window {edge_wid} F12")
    time.sleep(3)
    
    # Switch to Console tab
    run_cmd(f"xdotool key --window {edge_wid} ctrl+bracketleft")
    time.sleep(1)
    
    # Take screenshot of DevTools
    timestamp = int(time.time())
    run_cmd(f"import -window {edge_wid} {OUTPUT_DIR}/devtools_console_{timestamp}.png 2>/dev/null")
    
    return f"Screenshot saved: devtools_console_{timestamp}.png"

def check_page_resources():
    """Check what resources are being loaded"""
    # Look for hung network requests in HAR if available
    har_files = list(OUTPUT_DIR.glob("*.har"))
    
    latest_resources = {}
    if har_files:
        # Get most recent
        latest_har = max(har_files, key=lambda p: p.stat().st_mtime)
        try:
            with open(latest_har) as f:
                har_data = json.load(f)
            
            entries = har_data.get('log', {}).get('entries', [])
            
            # Find pending/slow requests
            pending = []
            for entry in entries[-50:]:  # Last 50 requests
                url = entry.get('request', {}).get('url', '')
                time_taken = entry.get('time', 0)
                status = entry.get('response', {}).get('status', 0)
                
                if time_taken > 1000 or status == 0:
                    pending.append({
                        'url': url[:100],
                        'time_ms': time_taken,
                        'status': status
                    })
            
            latest_resources = {
                'har_file': latest_har.name,
                'pending_requests': pending[:20]
            }
        except Exception as e:
            latest_resources = f"Error reading HAR: {e}"
    
    return latest_resources

def test_specific_endpoints():
    """Test other Xbox endpoints that might be failing"""
    endpoints = [
        'https://play.xbox.com',
        'https://browser.events.data.microsoft.com',
        'https://xboxlive.com',
        'https://achievements.xboxlive.com',
        'https://profile.xboxlive.com',
    ]
    
    results = {}
    for endpoint in endpoints:
        print(f"Testing {endpoint}...")
        response = run_cmd(f"curl -s -o /dev/null -w '%{{http_code}}' -m 5 {endpoint} 2>&1")
        results[endpoint] = response.strip()
    
    return results

def check_service_worker_errors():
    """Look for service worker issues"""
    sw_dir = Path.home() / ".var/app/com.microsoft.Edge/config/microsoft-edge/Default/Service Worker"
    
    sw_info = {
        'exists': sw_dir.exists(),
        'files': [],
        'logs': []
    }
    
    if sw_dir.exists():
        sw_info['files'] = [str(f.relative_to(sw_dir)) for f in sw_dir.rglob('*') if f.is_file()][:20]
        
        # Look for log files
        for log_file in sw_dir.rglob('*.log'):
            try:
                with open(log_file) as f:
                    sw_info['logs'].append({
                        'file': str(log_file),
                        'content': f.read()[-2000:]  # Last 2000 chars
                    })
            except:
                pass
    
    return sw_info

def main():
    timestamp = int(time.time())
    print("=" * 80)
    print("ROUND 2: Edge PWA Investigation")
    print("Chat endpoint blocked, but still stuck - what else is wrong?")
    print("=" * 80)
    print()
    
    results = {
        'timestamp': datetime.now().isoformat(),
        'investigation': 'round_2',
        'chat_block_active': True
    }
    
    # 1. Process info
    print("[1/8] Getting Edge process details...")
    results['processes'] = get_edge_processes()
    
    # 2. Network activity
    print("[2/8] Checking network activity...")
    results['network'] = check_network_activity()
    
    # 3. Test endpoints
    print("[3/8] Testing Xbox endpoints...")
    results['endpoint_tests'] = test_specific_endpoints()
    
    # 4. Strace renderers
    print("[4/8] Tracing renderer processes (50 seconds total)...")
    results['renderer_traces'] = strace_all_renderers()
    
    # 5. Check page resources
    print("[5/8] Analyzing page resources...")
    results['resources'] = check_page_resources()
    
    # 6. Service worker check
    print("[6/8] Checking service worker...")
    results['service_worker'] = check_service_worker_errors()
    
    # 7. DevTools console
    print("[7/8] Capturing DevTools console...")
    results['devtools'] = get_console_with_devtools()
    
    # 8. Get window title
    print("[8/8] Checking window state...")
    results['window_title'] = run_cmd("xdotool search --class msedge getwindowname 2>/dev/null | head -1")
    
    # Save results
    output_file = OUTPUT_DIR / f"round2_investigation_{timestamp}.json"
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\n{'=' * 80}")
    print(f"Round 2 investigation complete!")
    print(f"Saved to: {output_file}")
    print(f"{'=' * 80}\n")
    
    # Print summary
    print("KEY FINDINGS:")
    print(f"  Window Title: {results['window_title'].strip()}")
    print(f"  Active Processes: {len(results['processes'])}")
    print(f"  Endpoint Tests:")
    for endpoint, status in results['endpoint_tests'].items():
        print(f"    {endpoint}: {status}")
    
    if results['resources'].get('pending_requests'):
        print(f"\n  Pending/Slow Requests: {len(results['resources']['pending_requests'])}")
        for req in results['resources']['pending_requests'][:3]:
            print(f"    - {req['url'][:60]}... ({req['status']}, {req['time_ms']}ms)")

if __name__ == "__main__":
    main()
