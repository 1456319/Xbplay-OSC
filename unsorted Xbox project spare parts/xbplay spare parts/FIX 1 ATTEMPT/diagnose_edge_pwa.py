#!/usr/bin/env python3
"""
Comprehensive diagnostic for Play Xbox Play (Edge PWA) splash screen hang
Targets the actual Microsoft Edge Flatpak PWA processes
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
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
        return result.stdout + result.stderr
    except Exception as e:
        return f"Error: {e}"

def get_edge_process_info():
    """Get detailed info about Edge PWA processes"""
    info = {}
    
    # Find main Edge process
    main_pid = run_cmd("pgrep -f 'msedge.*enable-features=WebRTCPipeWireCapturer' | head -1").strip()
    info['main_edge_pid'] = main_pid
    
    # Find the high CPU renderer (instant-process)
    renderer_pid = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    info['instant_process_renderer_pid'] = renderer_pid
    
    # Get all Edge process PIDs
    all_edge_pids = run_cmd("pgrep -f 'msedge'").strip().split('\n')
    info['all_edge_pids'] = [p for p in all_edge_pids if p]
    
    # Process tree
    if main_pid:
        info['process_tree'] = run_cmd(f"pstree -p {main_pid}")
        info['process_cmdline'] = run_cmd(f"cat /proc/{main_pid}/cmdline | tr '\\0' ' '")
        info['process_environ'] = run_cmd(f"cat /proc/{main_pid}/environ | tr '\\0' '\\n'")
    
    # CPU and memory for all Edge processes
    info['resource_usage'] = {}
    for pid in info['all_edge_pids'][:20]:  # First 20
        if pid:
            usage = run_cmd(f"ps -p {pid} -o pid,pcpu,pmem,rss,vsz,cmd --no-headers")
            if usage.strip():
                info['resource_usage'][pid] = usage.strip()
    
    return info

def strace_renderer():
    """Strace the problematic renderer process"""
    renderer_pid = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not renderer_pid:
        return "No instant-process renderer found"
    
    print(f"Tracing renderer {renderer_pid} for 10 seconds...")
    
    # Get system call summary
    strace_summary = run_cmd(f"timeout 10 strace -p {renderer_pid} -c 2>&1 || true")
    
    # Get last 500 system calls
    strace_calls = run_cmd(f"timeout 5 strace -p {renderer_pid} -s 200 2>&1 | tail -500 || true")
    
    return {
        'summary': strace_summary,
        'recent_calls': strace_calls,
        'renderer_pid': renderer_pid
    }

def gdb_backtrace_all():
    """Get backtraces from key processes"""
    backtraces = {}
    
    # Main Edge process
    main_pid = run_cmd("pgrep -f 'msedge.*enable-features=WebRTCPipeWireCapturer' | head -1").strip()
    if main_pid:
        print(f"Getting backtrace from main process {main_pid}...")
        backtraces['main_process'] = run_cmd(f"gdb -batch -p {main_pid} -ex 'thread apply all bt' -ex 'quit' 2>&1 | head -1000")
    
    # Instant-process renderer
    renderer_pid = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    if renderer_pid:
        print(f"Getting backtrace from renderer {renderer_pid}...")
        backtraces['instant_renderer'] = run_cmd(f"gdb -batch -p {renderer_pid} -ex 'thread apply all bt' -ex 'quit' 2>&1 | head -1000")
    
    # GPU process
    gpu_pid = run_cmd("pgrep -f 'msedge.*type=gpu-process' | head -1").strip()
    if gpu_pid:
        print(f"Getting backtrace from GPU process {gpu_pid}...")
        backtraces['gpu_process'] = run_cmd(f"gdb -batch -p {gpu_pid} -ex 'thread apply all bt' -ex 'quit' 2>&1 | head -1000")
    
    return backtraces

def check_flatpak_env():
    """Check Flatpak environment and configuration"""
    info = {}
    
    # Flatpak info
    info['flatpak_list'] = run_cmd("flatpak list | grep -i edge")
    info['flatpak_ps'] = run_cmd("flatpak ps | grep -i edge")
    
    # Edge config directory
    edge_config = Path.home() / ".var/app/com.microsoft.Edge/config/microsoft-edge"
    if edge_config.exists():
        info['config_dir_contents'] = run_cmd(f"find {edge_config} -maxdepth 3 -type f | head -50")
    
    # Check for DevTools Active Port
    info['devtools_port'] = run_cmd("find ~/.var/app/com.microsoft.Edge -name 'DevToolsActivePort' -exec cat {} \\; 2>/dev/null")
    
    # Edge logs
    info['edge_logs'] = run_cmd("find ~/.var/app/com.microsoft.Edge -name '*.log' -mtime -1 -exec tail -100 {} \\; 2>/dev/null | head -500")
    
    # Check for crash dumps
    info['crash_reports'] = run_cmd("find ~/.var/app/com.microsoft.Edge/config/microsoft-edge/'Crash Reports' -type f 2>/dev/null | head -20")
    
    return info

def capture_network_activity():
    """Capture network connections from Edge processes"""
    info = {}
    
    # All Edge network connections
    info['connections'] = run_cmd("lsof -i -P -n | grep msedge")
    
    # Check what domains are being accessed
    info['dns_cache'] = run_cmd("nscd -g 2>/dev/null || echo 'nscd not available'")
    
    return info

def inspect_renderer_memory():
    """Check renderer memory maps for clues"""
    renderer_pid = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    if not renderer_pid:
        return "No renderer found"
    
    info = {}
    info['maps'] = run_cmd(f"cat /proc/{renderer_pid}/maps | head -200")
    info['smaps_summary'] = run_cmd(f"cat /proc/{renderer_pid}/smaps_rollup 2>/dev/null")
    info['status'] = run_cmd(f"cat /proc/{renderer_pid}/status")
    info['fd_count'] = run_cmd(f"ls -l /proc/{renderer_pid}/fd 2>/dev/null | wc -l")
    info['open_files'] = run_cmd(f"lsof -p {renderer_pid} 2>/dev/null | head -100")
    
    return info

def try_devtools_connection():
    """Try to connect to Edge DevTools Protocol"""
    import urllib.request
    
    # Try to find DevTools port
    devtools_port_file = run_cmd("find ~/.var/app/com.microsoft.Edge -name 'DevToolsActivePort' 2>/dev/null | head -1").strip()
    
    if devtools_port_file:
        try:
            with open(devtools_port_file) as f:
                port = f.readline().strip()
                
            with urllib.request.urlopen(f"http://localhost:{port}/json", timeout=5) as response:
                tabs = json.loads(response.read().decode())
                return {
                    'port': port,
                    'tabs': tabs,
                    'success': True
                }
        except Exception as e:
            return {'error': str(e), 'devtools_port_file': devtools_port_file}
    
    return {'error': 'No DevTools port file found'}

def get_javascript_console_logs():
    """Try to extract JavaScript console logs"""
    # Check Edge log files
    log_dir = Path.home() / ".var/app/com.microsoft.Edge/config/microsoft-edge"
    
    logs = {}
    if log_dir.exists():
        # Look for debug logs
        for log_file in log_dir.rglob("*.log"):
            if log_file.stat().st_size < 10 * 1024 * 1024:  # Under 10MB
                try:
                    with open(log_file) as f:
                        content = f.read()
                        if content:
                            logs[str(log_file)] = content[-5000:]  # Last 5000 chars
                except:
                    pass
    
    return logs

def save_report(data, filename):
    """Save diagnostic data to file"""
    output_file = OUTPUT_DIR / filename
    with open(output_file, 'w') as f:
        if isinstance(data, dict):
            json.dump(data, f, indent=2)
        else:
            f.write(str(data))
    print(f"Saved: {output_file}")
    return output_file

def main():
    timestamp = int(time.time())
    print("=" * 80)
    print("Play Xbox Play (Edge PWA) Diagnostic Tool")
    print("=" * 80)
    print(f"Timestamp: {datetime.now().isoformat()}")
    print()
    
    # 1. Edge Process Information
    print("[1/10] Gathering Edge process information...")
    edge_info = get_edge_process_info()
    save_report(edge_info, f"edge_process_info_{timestamp}.json")
    
    # 2. Flatpak Environment
    print("[2/10] Checking Flatpak environment...")
    flatpak_info = check_flatpak_env()
    save_report(flatpak_info, f"flatpak_env_{timestamp}.json")
    
    # 3. Network Activity
    print("[3/10] Capturing network activity...")
    network_info = capture_network_activity()
    save_report(network_info, f"network_activity_{timestamp}.json")
    
    # 4. Renderer Memory Inspection
    print("[4/10] Inspecting renderer memory...")
    memory_info = inspect_renderer_memory()
    save_report(memory_info, f"renderer_memory_{timestamp}.json")
    
    # 5. DevTools Connection Attempt
    print("[5/10] Attempting DevTools connection...")
    devtools_info = try_devtools_connection()
    save_report(devtools_info, f"devtools_connection_{timestamp}.json")
    
    # 6. JavaScript/Console Logs
    print("[6/10] Extracting console logs...")
    console_logs = get_javascript_console_logs()
    save_report(console_logs, f"console_logs_{timestamp}.json")
    
    # 7. System Call Trace
    print("[7/10] Tracing system calls (15 seconds)...")
    strace_data = strace_renderer()
    save_report(strace_data, f"strace_edge_renderer_{timestamp}.json")
    
    # 8. Stack Traces
    print("[8/10] Getting stack traces with GDB (may take a minute)...")
    backtraces = gdb_backtrace_all()
    save_report(backtraces, f"gdb_backtraces_{timestamp}.json")
    
    # 9. Check for stuck JavaScript
    print("[9/10] Checking for JavaScript infinite loops...")
    renderer_pid = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    if renderer_pid:
        # Sample the stack multiple times to see if it's stuck
        samples = []
        for i in range(5):
            print(f"  Sample {i+1}/5...")
            sample = run_cmd(f"gdb -batch -p {renderer_pid} -ex 'thread apply all bt 10' -ex 'quit' 2>&1")
            samples.append(sample)
            time.sleep(2)
        save_report(samples, f"stack_samples_{timestamp}.json")
    
    # 10. Summary
    print("[10/10] Creating summary report...")
    summary = {
        'timestamp': datetime.now().isoformat(),
        'main_findings': {
            'high_cpu_renderer': edge_info.get('instant_process_renderer_pid'),
            'total_edge_processes': len(edge_info.get('all_edge_pids', [])),
            'devtools_available': devtools_info.get('success', False),
        }
    }
    save_report(summary, f"diagnostic_summary_{timestamp}.json")
    
    print()
    print("=" * 80)
    print("DIAGNOSTIC COMPLETE!")
    print(f"All files saved to: {OUTPUT_DIR}")
    print()
    print("Key Findings:")
    print(f"  - High CPU Renderer PID: {edge_info.get('instant_process_renderer_pid', 'Not found')}")
    print(f"  - Total Edge Processes: {len(edge_info.get('all_edge_pids', []))}")
    print(f"  - DevTools Available: {devtools_info.get('success', False)}")
    print("=" * 80)

if __name__ == "__main__":
    main()
