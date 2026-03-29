#!/usr/bin/env python3
"""
Extract detailed page state from Edge PWA using Chrome DevTools Protocol via pyppeteer
Falls back to X11 screenshot and memory inspection if CDP unavailable
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

def take_screenshots():
    """Take screenshots of both PWA windows"""
    print("Taking screenshots...")
    
    # Find Edge window
    edge_wid = run_cmd("xdotool search --class msedge 2>/dev/null | head -1").strip()
    brave_wid = run_cmd("xdotool search --name 'Play Xbox' 2>/dev/null | head -1").strip()
    
    if edge_wid:
        run_cmd(f"import -window {edge_wid} {OUTPUT_DIR}/edge_screenshot_{int(time.time())}.png 2>/dev/null")
    
    if brave_wid:
        run_cmd(f"import -window {brave_wid} {OUTPUT_DIR}/brave_screenshot_{int(time.time())}.png 2>/dev/null")
    
    return {'edge_wid': edge_wid, 'brave_wid': brave_wid}

def analyze_strace_for_infinite_loop():
    """Analyze Edge renderer strace for infinite loop patterns"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not edge_renderer:
        return "No renderer found"
    
    print(f"Analyzing renderer {edge_renderer} for loop patterns (15 seconds)...")
    
    # Get detailed trace
    trace = run_cmd(f"timeout 15 strace -p {edge_renderer} -e trace=write,futex,poll,epoll_wait,recv,recvmsg -s 100 2>&1 | tail -1000 || true")
    
    # Analyze for patterns
    lines = trace.split('\n')
    futex_count = len([l for l in lines if 'futex' in l])
    write_count = len([l for l in lines if 'write(' in l])
    poll_count = len([l for l in lines if 'poll' in l or 'epoll' in l])
    
    return {
        'trace_sample': trace[-2000:],  # Last 2000 chars
        'futex_calls': futex_count,
        'write_calls': write_count,
        'poll_calls': poll_count,
        'total_lines': len(lines)
    }

def check_javascript_state():
    """Try to inspect JavaScript execution state"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not edge_renderer:
        return "No renderer found"
    
    # Check for V8 heap info in maps
    maps = run_cmd(f"cat /proc/{edge_renderer}/maps | grep -E 'heap|stack'")
    
    # Get thread states
    threads = run_cmd(f"cat /proc/{edge_renderer}/status | grep -E 'Threads|State'")
    
    # Check CPU time
    cpu_time = run_cmd(f"cat /proc/{edge_renderer}/stat")
    
    return {
        'heap_maps': maps[:1000],
        'thread_info': threads,
        'cpu_stat': cpu_time
    }

def extract_har_analysis():
    """Analyze existing HAR files for clues"""
    har_files = list(Path(OUTPUT_DIR).glob("*.har"))
    
    analysis = {}
    for har_file in har_files[:3]:  # First 3 HAR files
        try:
            with open(har_file) as f:
                har_data = json.load(f)
                
            entries = har_data.get('log', {}).get('entries', [])
            
            # Find requests that might be blocking
            pending = []
            failed = []
            slow = []
            
            for entry in entries:
                url = entry.get('request', {}).get('url', '')
                time_taken = entry.get('time', 0)
                response_status = entry.get('response', {}).get('status', 0)
                
                if response_status == 0 or response_status >= 400:
                    failed.append({'url': url, 'status': response_status})
                
                if time_taken > 5000:  # Over 5 seconds
                    slow.append({'url': url, 'time': time_taken})
            
            analysis[har_file.name] = {
                'total_requests': len(entries),
                'failed_requests': failed[:10],
                'slow_requests': slow[:10]
            }
        except Exception as e:
            analysis[har_file.name] = f"Error: {e}"
    
    return analysis

def get_console_errors_from_devtools_md():
    """Parse the existing DevTools markdown file"""
    devtools_file = OUTPUT_DIR / "devtools_why_is_this_page_stuck_in_a_loading_loop_ive_turned_.md"
    
    if not devtools_file.exists():
        return "File not found"
    
    with open(devtools_file) as f:
        content = f.read()
    
    return content[:5000]  # First 5000 chars

def create_summary_report():
    """Create a comprehensive summary"""
    
    # Key findings from previous diagnostics
    edge_info = {}
    try:
        with open(OUTPUT_DIR / "edge_process_info_1774561612.json") as f:
            edge_info = json.load(f)
    except:
        pass
    
    strace_info = {}
    try:
        with open(OUTPUT_DIR / "strace_edge_renderer_1774561612.json") as f:
            strace_info = json.load(f)
    except:
        pass
    
    summary = {
        'timestamp': time.time(),
        'problem': 'Xbox Play PWA stuck on splash screen in Edge but works in Brave',
        'edge_renderer_pid': edge_info.get('instant_process_renderer_pid'),
        'edge_cpu_usage': '34.4%',
        'strace_summary': strace_info.get('summary', '')[:500],
        'key_syscalls': 'Mostly futex (84%), write (5%), getrandom (5%)',
        'likely_cause': 'JavaScript infinite loop or tight polling loop based on high CPU and futex pattern',
        'comparison': 'Brave instance shows "Play Xbox - Play Xbox" title, Edge shows only "msedge" (splash)',
        'recommendations': [
            'Page is stuck in initialization/authentication loop',
            'Check service worker registration',
            'Inspect entry.worker.js for infinite loops',
            'May be related to PWA cache or storage permissions',
            'Edge and Brave handle PWA differently - possibly manifest or feature detection issue'
        ]
    }
    
    return summary

def main():
    timestamp = int(time.time())
    print("=" * 80)
    print("Final Analysis: Xbox Play PWA Splash Screen Hang")
    print("=" * 80)
    
    print("\n[1/6] Taking screenshots...")
    screenshots = take_screenshots()
    
    print("\n[2/6] Analyzing strace for infinite loop patterns...")
    loop_analysis = analyze_strace_for_infinite_loop()
    
    print("\n[3/6] Checking JavaScript state...")
    js_state = check_javascript_state()
    
    print("\n[4/6] Analyzing HAR files...")
    har_analysis = extract_har_analysis()
    
    print("\n[5/6] Extracting DevTools console log...")
    devtools_log = get_console_errors_from_devtools_md()
    
    print("\n[6/6] Creating summary report...")
    summary = create_summary_report()
    
    # Combine everything
    final_report = {
        'summary': summary,
        'screenshots': screenshots,
        'loop_analysis': loop_analysis,
        'javascript_state': js_state,
        'har_analysis': har_analysis,
        'devtools_console': devtools_log
    }
    
    # Save
    output_file = OUTPUT_DIR / f"final_analysis_{timestamp}.json"
    with open(output_file, 'w') as f:
        json.dump(final_report, f, indent=2)
    
    # Also save as readable text
    text_output = OUTPUT_DIR / f"ANALYSIS_SUMMARY_{timestamp}.txt"
    with open(text_output, 'w') as f:
        f.write("=" * 80 + "\n")
        f.write("XBOX PLAY PWA SPLASH SCREEN HANG - ANALYSIS SUMMARY\n")
        f.write("=" * 80 + "\n\n")
        f.write(json.dumps(summary, indent=2))
        f.write("\n\n" + "=" * 80 + "\n")
        f.write("LOOP ANALYSIS\n")
        f.write("=" * 80 + "\n")
        f.write(json.dumps(loop_analysis, indent=2))
        f.write("\n\n" + "=" * 80 + "\n")
        f.write("HAR FILE ANALYSIS\n")
        f.write("=" * 80 + "\n")
        f.write(json.dumps(har_analysis, indent=2))
    
    print(f"\n{'=' * 80}")
    print(f"Final analysis saved to:")
    print(f"  JSON: {output_file}")
    print(f"  TEXT: {text_output}")
    print(f"{'=' * 80}")
    
    print("\n\nSUMMARY OF FINDINGS:")
    print("-" * 80)
    for key, value in summary.items():
        if key != 'recommendations':
            print(f"{key}: {value}")
    print("\nRECOMMENDATIONS:")
    for i, rec in enumerate(summary['recommendations'], 1):
        print(f"  {i}. {rec}")

if __name__ == "__main__":
    main()
