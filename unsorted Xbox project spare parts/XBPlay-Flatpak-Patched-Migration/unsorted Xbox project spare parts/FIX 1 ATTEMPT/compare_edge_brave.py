#!/usr/bin/env python3
"""
Compare Edge and Brave PWA instances to identify what's different
"""

import json
import subprocess
import time
from pathlib import Path

OUTPUT_DIR = Path("/home/deck/dump/xbplay spare parts")

def run_cmd(cmd):
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
        return result.stdout + result.stderr
    except Exception as e:
        return f"Error: {e}"

def compare_processes():
    """Compare Edge vs Brave process details"""
    comparison = {}
    
    # Edge processes
    edge_main = run_cmd("pgrep -f 'msedge.*enable-features=WebRTCPipeWireCapturer' | head -1").strip()
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    
    # Brave processes  
    brave_main = run_cmd("pgrep -f 'brave.*app-id=kanajofaghckoijdglhndcjjlemljefb' | head -1").strip()
    brave_renderers = run_cmd("pgrep -f 'brave.*type=renderer'").strip().split('\n')
    
    comparison['edge'] = {
        'main_pid': edge_main,
        'renderer_pid': edge_renderer,
        'main_cpu': run_cmd(f"ps -p {edge_main} -o %cpu --no-headers").strip() if edge_main else "N/A",
        'renderer_cpu': run_cmd(f"ps -p {edge_renderer} -o %cpu --no-headers").strip() if edge_renderer else "N/A",
        'cmdline': run_cmd(f"cat /proc/{edge_renderer}/cmdline | tr '\\0' ' '") if edge_renderer else "N/A"
    }
    
    comparison['brave'] = {
        'main_pid': brave_main,
        'renderer_pids': brave_renderers[:5],  # Top 5
        'renderer_cpu': {}
    }
    
    for pid in brave_renderers[:5]:
        if pid:
            cpu = run_cmd(f"ps -p {pid} -o %cpu --no-headers").strip()
            comparison['brave']['renderer_cpu'][pid] = cpu
    
    return comparison

def compare_network_connections():
    """Compare network activity between Edge and Brave"""
    edge_connections = run_cmd("lsof -i -P -n | grep msedge | grep ESTABLISHED")
    brave_connections = run_cmd("lsof -i -P -n | grep brave | grep ESTABLISHED")
    
    return {
        'edge': edge_connections,
        'brave': brave_connections
    }

def strace_comparison():
    """Quick strace of both renderers"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    brave_renderer = run_cmd("pgrep -f 'brave.*type=renderer' | head -1").strip()
    
    print(f"Comparing syscalls: Edge {edge_renderer} vs Brave {brave_renderer}")
    
    edge_strace = ""
    brave_strace = ""
    
    if edge_renderer:
        edge_strace = run_cmd(f"timeout 5 strace -p {edge_renderer} -c 2>&1 || true")
    
    if brave_renderer:
        brave_strace = run_cmd(f"timeout 5 strace -p {brave_renderer} -c 2>&1 || true")
    
    return {
        'edge': edge_strace,
        'brave': brave_strace
    }

def check_window_titles():
    """Get window titles to see what page they're on"""
    edge_windows = run_cmd("xdotool search --class msedge getwindowname 2>/dev/null")
    brave_windows = run_cmd("xdotool search --class brave getwindowname 2>/dev/null")
    
    return {
        'edge_windows': edge_windows,
        'brave_windows': brave_windows
    }

def stack_trace_comparison():
    """Get quick stack traces from both"""
    edge_renderer = run_cmd("pgrep -f 'msedge.*instant-process' | head -1").strip()
    brave_renderer = run_cmd("pgrep -f 'brave.*type=renderer' | head -1").strip()
    
    edge_stack = ""
    brave_stack = ""
    
    if edge_renderer:
        print(f"Sampling Edge renderer {edge_renderer}...")
        edge_stack = run_cmd(f"gdb -batch -p {edge_renderer} -ex 'thread apply all bt 5' -ex 'quit' 2>&1 | head -500")
    
    if brave_renderer:
        print(f"Sampling Brave renderer {brave_renderer}...")
        brave_stack = run_cmd(f"gdb -batch -p {brave_renderer} -ex 'thread apply all bt 5' -ex 'quit' 2>&1 | head -500")
    
    return {
        'edge_stack': edge_stack,
        'brave_stack': brave_stack
    }

def main():
    timestamp = int(time.time())
    print("=" * 80)
    print("Edge vs Brave PWA Comparison")
    print("=" * 80)
    
    print("\n[1/5] Comparing process details...")
    proc_comparison = compare_processes()
    
    print("\n[2/5] Checking window titles...")
    window_info = check_window_titles()
    
    print("\n[3/5] Comparing network connections...")
    network_comparison = compare_network_connections()
    
    print("\n[4/5] Comparing system calls (10 seconds total)...")
    strace_comparison_data = strace_comparison()
    
    print("\n[5/5] Comparing stack traces...")
    stack_comparison = stack_trace_comparison()
    
    # Combine all data
    full_comparison = {
        'processes': proc_comparison,
        'windows': window_info,
        'network': network_comparison,
        'strace': strace_comparison_data,
        'stacks': stack_comparison
    }
    
    # Save
    output_file = OUTPUT_DIR / f"edge_vs_brave_comparison_{timestamp}.json"
    with open(output_file, 'w') as f:
        json.dump(full_comparison, f, indent=2)
    
    print(f"\n{'=' * 80}")
    print(f"Comparison saved to: {output_file}")
    print(f"{'=' * 80}")
    
    # Print summary
    print("\nSUMMARY:")
    print(f"Edge renderer PID: {proc_comparison['edge']['renderer_pid']} (CPU: {proc_comparison['edge']['renderer_cpu']}%)")
    print(f"Brave renderers: {len([p for p in proc_comparison['brave']['renderer_pids'] if p])}")
    print(f"\nEdge windows:\n{window_info['edge_windows'][:200]}")
    print(f"\nBrave windows:\n{window_info['brave_windows'][:200]}")

if __name__ == "__main__":
    main()
