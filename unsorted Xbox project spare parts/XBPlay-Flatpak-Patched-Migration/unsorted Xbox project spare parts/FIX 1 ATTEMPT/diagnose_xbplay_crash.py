#!/usr/bin/env python3
"""
Comprehensive diagnostic tool for Play Xbox Play splash screen hang
Attaches to running Chrome instance and extracts all relevant information
"""

import json
import time
import subprocess
import os
from datetime import datetime
from pathlib import Path

OUTPUT_DIR = Path("/home/deck/dump/xbplay spare parts")

def run_cmd(cmd):
    """Run shell command and return output"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=10)
        return result.stdout + result.stderr
    except Exception as e:
        return f"Error: {e}"

def get_process_info():
    """Get information about the Chrome process"""
    info = {}
    
    # Find Chrome PID
    chrome_pid = run_cmd("pgrep -f 'selenium/chrome' | head -1").strip()
    info['chrome_pid'] = chrome_pid
    
    if chrome_pid:
        # Process details
        info['process_cmdline'] = run_cmd(f"cat /proc/{chrome_pid}/cmdline | tr '\\0' ' '")
        info['process_status'] = run_cmd(f"cat /proc/{chrome_pid}/status")
        info['process_maps'] = run_cmd(f"head -100 /proc/{chrome_pid}/maps")
        info['open_files'] = run_cmd(f"lsof -p {chrome_pid} 2>/dev/null | head -100")
        info['network_connections'] = run_cmd(f"lsof -i -P -n | grep {chrome_pid}")
        
        # CPU and memory usage
        info['top_info'] = run_cmd(f"top -b -n 1 -p {chrome_pid} | tail -5")
        
        # Child processes
        info['child_processes'] = run_cmd(f"pstree -p {chrome_pid}")
        
        # Check for high CPU renderers
        renderer_pids = run_cmd(f"pgrep -P {chrome_pid}").strip().split('\n')
        info['renderer_cpu'] = {}
        for pid in renderer_pids[:10]:  # First 10 children
            if pid:
                cpu_info = run_cmd(f"ps -p {pid} -o pid,pcpu,pmem,cmd --no-headers")
                if cpu_info.strip():
                    info['renderer_cpu'][pid] = cpu_info.strip()
    
    return info

def check_selenium_session():
    """Check for active Selenium session"""
    info = {}
    
    # Look for .com.google.Chrome directories
    chrome_dirs = run_cmd("find /tmp -maxdepth 2 -name '.com.google.Chrome.*' 2>/dev/null")
    info['chrome_temp_dirs'] = chrome_dirs
    
    # Check for DevToolsActivePort
    devtools_files = run_cmd("find /tmp -name 'DevToolsActivePort' 2>/dev/null")
    info['devtools_files'] = devtools_files
    
    if devtools_files.strip():
        for file_path in devtools_files.strip().split('\n'):
            if file_path:
                content = run_cmd(f"cat {file_path}")
                info[f'devtools_content_{file_path}'] = content
    
    return info

def attach_with_chrome_devtools():
    """Try to attach using Chrome DevTools Protocol"""
    import urllib.request
    
    # Common DevTools ports
    ports = [9222, 9223, 9224, 9515, 4444]
    
    for port in ports:
        try:
            with urllib.request.urlopen(f"http://localhost:{port}/json", timeout=2) as response:
                if response.status == 200:
                    import json
                    return {
                        'port': port,
                        'tabs': json.loads(response.read().decode())
                    }
        except:
            continue
    
    return {'error': 'No DevTools port found'}

def strace_sample():
    """Sample system calls from the main renderer"""
    chrome_pid = run_cmd("pgrep -f 'selenium/chrome' | head -1").strip()
    if not chrome_pid:
        return "Chrome not found"
    
    # Find high CPU renderer
    renderer = run_cmd(f"ps -eo pid,ppid,pcpu,cmd | grep {chrome_pid} | grep renderer | sort -k3 -rn | head -1 | awk '{{print $1}}'").strip()
    
    if renderer:
        print(f"Sampling strace from renderer {renderer} for 5 seconds...")
        strace_output = run_cmd(f"timeout 5 strace -p {renderer} -c 2>&1 || true")
        return strace_output
    
    return "No renderer found"

def gdb_backtrace():
    """Get stack trace from high CPU renderer"""
    chrome_pid = run_cmd("pgrep -f 'selenium/chrome' | head -1").strip()
    if not chrome_pid:
        return "Chrome not found"
    
    # Find high CPU renderer
    renderer = run_cmd(f"ps -eo pid,ppid,pcpu,cmd | grep {chrome_pid} | grep renderer | sort -k3 -rn | head -1 | awk '{{print $1}}'").strip()
    
    if renderer:
        print(f"Getting backtrace from renderer {renderer}...")
        gdb_commands = f"gdb -batch -p {renderer} -ex 'thread apply all bt' -ex 'quit' 2>&1"
        backtrace = run_cmd(gdb_commands)
        return backtrace
    
    return "No renderer found"

def save_report(data, filename):
    """Save diagnostic data to file"""
    output_file = OUTPUT_DIR / filename
    with open(output_file, 'w') as f:
        if isinstance(data, dict):
            json.dump(data, f, indent=2)
        else:
            f.write(str(data))
    print(f"Saved: {output_file}")

def main():
    print("=" * 80)
    print("Play Xbox Play Diagnostic Tool")
    print("=" * 80)
    print(f"Timestamp: {datetime.now().isoformat()}")
    print()
    
    # 1. Process Information
    print("[1/7] Gathering process information...")
    process_info = get_process_info()
    save_report(process_info, f"process_info_{int(time.time())}.json")
    
    # 2. Selenium Session Check
    print("[2/7] Checking Selenium session...")
    selenium_info = check_selenium_session()
    save_report(selenium_info, f"selenium_info_{int(time.time())}.json")
    
    # 3. Chrome DevTools Protocol
    print("[3/7] Attempting Chrome DevTools Protocol connection...")
    cdp_info = attach_with_chrome_devtools()
    save_report(cdp_info, f"cdp_info_{int(time.time())}.json")
    
    # 4. System call trace
    print("[4/7] Sampling system calls (5 seconds)...")
    strace_data = strace_sample()
    save_report(strace_data, f"strace_sample_{int(time.time())}.txt")
    
    # 5. Stack trace with GDB
    print("[5/7] Getting stack trace with GDB...")
    gdb_data = gdb_backtrace()
    save_report(gdb_data, f"gdb_backtrace_{int(time.time())}.txt")
    
    # 6. Check for JavaScript errors in Chrome log
    print("[6/7] Checking Chrome logs...")
    chrome_log = run_cmd("find ~/.config/google-chrome-for-testing -name 'chrome_debug.log' -exec tail -500 {} \\; 2>/dev/null")
    save_report(chrome_log, f"chrome_log_{int(time.time())}.txt")
    
    # 7. Memory maps
    print("[7/7] Analyzing memory...")
    chrome_pid = run_cmd("pgrep -f 'selenium/chrome' | head -1").strip()
    if chrome_pid:
        smaps = run_cmd(f"cat /proc/{chrome_pid}/smaps 2>/dev/null | head -200")
        save_report(smaps, f"smaps_{int(time.time())}.txt")
    
    print()
    print("=" * 80)
    print("Diagnostic collection complete!")
    print(f"All files saved to: {OUTPUT_DIR}")
    print("=" * 80)

if __name__ == "__main__":
    main()
