#!/usr/bin/env python3
"""
Round 3: Comprehensive troubleshooting of Xbox Play PWA
Now that we have all sources, let's diagnose the issue
"""

import json
import subprocess
import time
from pathlib import Path
from collections import Counter

SPARE_DIR = Path("/home/deck/dump/xbplay spare parts")
OUTPUT_DIR = SPARE_DIR / "round3_diagnostics"
OUTPUT_DIR.mkdir(exist_ok=True)

print("=" * 80)
print("ROUND 3: COMPREHENSIVE TROUBLESHOOTING")
print("=" * 80)

# Step 1: Get current process state
print("\n1. Current Process State")
print("-" * 80)

result = subprocess.run(
    ["ps", "aux"],
    capture_output=True,
    text=True
)

edge_processes = []
for line in result.stdout.splitlines():
    if "msedge" in line and "renderer" in line:
        parts = line.split()
        pid = parts[1]
        cpu = parts[2]
        mem = parts[3]
        cmd = " ".join(parts[10:])
        
        edge_processes.append({
            "pid": pid,
            "cpu": cpu,
            "mem": mem,
            "command": cmd
        })
        
        print(f"  PID {pid}: CPU={cpu}% MEM={mem}% [{cmd[:80]}...]")

# Step 2: Check network connections
print("\n2. Active Network Connections")
print("-" * 80)

result = subprocess.run(
    ["ss", "-tnp"],
    capture_output=True,
    text=True
)

xbox_connections = []
for line in result.stdout.splitlines():
    if "msedge" in line or "xbox" in line.lower():
        print(f"  {line}")
        xbox_connections.append(line)

# Step 3: Test endpoints
print("\n3. Testing Known Endpoints")
print("-" * 80)

endpoints = [
    "https://play.xbox.com",
    "https://chat.xboxlive.com",
    "wss://chat.xboxlive.com",
]

endpoint_status = {}
for endpoint in endpoints:
    try:
        result = subprocess.run(
            ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", 
             "-m", "5", endpoint],
            capture_output=True,
            text=True,
            timeout=6
        )
        status = result.stdout.strip()
        endpoint_status[endpoint] = status
        
        symbol = "✓" if status.startswith("2") or status.startswith("3") else "✗"
        print(f"  {symbol} {endpoint}: HTTP {status}")
    except Exception as e:
        endpoint_status[endpoint] = f"ERROR: {e}"
        print(f"  ✗ {endpoint}: {e}")

# Step 4: Check /etc/hosts for blocks
print("\n4. Checking /etc/hosts Blocks")
print("-" * 80)

try:
    with open("/etc/hosts") as f:
        hosts_content = f.read()
    
    xbox_blocks = [line for line in hosts_content.splitlines() 
                   if "xbox" in line.lower() or "chat" in line.lower()]
    
    if xbox_blocks:
        print("  ⚠️  Found Xbox-related blocks:")
        for block in xbox_blocks:
            print(f"    {block}")
    else:
        print("  ✓ No Xbox blocks found")
        
except Exception as e:
    print(f"  ⚠️  Error reading /etc/hosts: {e}")

# Step 5: Analyze console errors from logs
print("\n5. Analyzing Console Error Logs")
print("-" * 80)

console_logs = list(SPARE_DIR.glob("*CONSOLE*.log"))
if console_logs:
    print(f"  Found {len(console_logs)} console log file(s)")
    
    all_errors = []
    for log_file in console_logs:
        try:
            with open(log_file) as f:
                content = f.read()
            
            # Extract error patterns
            lines = content.splitlines()
            for line in lines:
                if any(keyword in line.lower() for keyword in 
                       ["error", "failed", "exception", "refused", "timeout", "404", "500"]):
                    all_errors.append(line.strip())
        except Exception as e:
            print(f"  ⚠️  Error reading {log_file.name}: {e}")
    
    if all_errors:
        # Count error types
        error_counter = Counter(all_errors)
        
        print(f"\n  Top 10 Most Frequent Errors:")
        for error, count in error_counter.most_common(10):
            print(f"    [{count}x] {error[:100]}")
    else:
        print("  ✓ No errors found in logs")
else:
    print("  ⚠️  No console log files found")

# Step 6: Analyze HAR files for patterns
print("\n6. Analyzing HAR Files for Failure Patterns")
print("-" * 80)

har_files = list(SPARE_DIR.glob("*.har"))
if har_files:
    for har_file in har_files:
        try:
            with open(har_file) as f:
                har_data = json.load(f)
            
            entries = har_data.get("log", {}).get("entries", [])
            
            # Analyze failures
            failures = []
            for entry in entries:
                url = entry.get("request", {}).get("url", "")
                status = entry.get("response", {}).get("status", 0)
                
                if status >= 400 or status == 0:
                    failures.append({
                        "url": url,
                        "status": status,
                        "time": entry.get("time", 0)
                    })
            
            if failures:
                print(f"\n  {har_file.name}: {len(failures)} failed requests")
                
                # Group by domain
                domain_failures = Counter([
                    url.split("/")[2] if "//" in url else "unknown"
                    for url in [f["url"] for f in failures]
                ])
                
                print(f"  Top failing domains:")
                for domain, count in domain_failures.most_common(5):
                    print(f"    - {domain}: {count} failures")
                    
        except Exception as e:
            print(f"  ⚠️  Error analyzing {har_file.name}: {e}")
else:
    print("  ⚠️  No HAR files found")

# Step 7: Check service worker state
print("\n7. Service Worker Status")
print("-" * 80)

sw_cache_dir = Path("/home/deck/.var/app/com.microsoft.Edge/config/microsoft-edge")
sw_dirs = list(sw_cache_dir.rglob("Service Worker"))

if sw_dirs:
    for sw_dir in sw_dirs:
        print(f"  Found: {sw_dir}")
        
        # List contents
        try:
            result = subprocess.run(
                ["find", str(sw_dir), "-type", "f"],
                capture_output=True,
                text=True
            )
            
            files = result.stdout.splitlines()
            print(f"    - {len(files)} files")
            
            # Check for databases
            db_files = [f for f in files if "database" in f.lower() or ".db" in f]
            if db_files:
                print(f"    - {len(db_files)} database files")
                
        except Exception as e:
            print(f"    ⚠️  Error: {e}")
else:
    print("  ⚠️  No Service Worker directories found")

# Step 8: Search for specific error patterns in sources
print("\n8. Searching Sources for Error Patterns")
print("-" * 80)

sources_dir = SPARE_DIR / "round3_sources"
if sources_dir.exists():
    
    # Search for key patterns
    patterns = [
        "chat.xboxlive.com",
        "SW_FALLBACK",
        "FetchEvent",
        "network error",
        "retry",
        "timeout"
    ]
    
    for pattern in patterns:
        result = subprocess.run(
            ["grep", "-l", "-i", pattern, str(sources_dir / "*.js")],
            capture_output=True,
            text=True,
            shell=True
        )
        
        if result.returncode == 0:
            matches = result.stdout.splitlines()
            print(f"  '{pattern}': found in {len(matches)} files")
        else:
            print(f"  '{pattern}': not found")

# Step 9: CPU and performance check
print("\n9. Current Performance Metrics")
print("-" * 80)

if edge_processes:
    # Get detailed CPU info for high-usage processes
    for proc in edge_processes:
        cpu_val = float(proc["cpu"])
        if cpu_val > 5.0:
            pid = proc["pid"]
            
            # Get strace sample
            print(f"\n  High CPU process PID {pid} (CPU: {cpu_val}%)")
            print(f"  Taking syscall sample...")
            
            try:
                # 2-second strace sample
                result = subprocess.run(
                    ["timeout", "2", "strace", "-c", "-p", pid],
                    capture_output=True,
                    text=True
                )
                
                # Save full output
                strace_file = OUTPUT_DIR / f"strace_pid_{pid}.txt"
                with open(strace_file, "w") as f:
                    f.write(result.stderr)
                
                # Extract summary
                lines = result.stderr.splitlines()
                summary_start = False
                for line in lines:
                    if "% time" in line or summary_start:
                        print(f"    {line}")
                        summary_start = True
                        if summary_start and line.strip() == "":
                            break
                            
            except Exception as e:
                print(f"    ⚠️  Error: {e}")

# Step 10: Generate diagnosis
print("\n" + "=" * 80)
print("DIAGNOSIS")
print("=" * 80)

diagnosis = {
    "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
    "edge_processes": edge_processes,
    "endpoint_status": endpoint_status,
    "active_connections": xbox_connections,
    "sources_extracted": len(list(sources_dir.glob("*.js"))) if sources_dir.exists() else 0
}

# Determine issues
issues = []

# Check for blocked endpoints
if "127.0.0.1" in str(endpoint_status.get("https://chat.xboxlive.com", "")):
    issues.append("chat.xboxlive.com is blocked in /etc/hosts")

# Check for high CPU
high_cpu_procs = [p for p in edge_processes if float(p["cpu"]) > 10]
if high_cpu_procs:
    issues.append(f"{len(high_cpu_procs)} renderer(s) with high CPU usage")

# Check for many renderers
if len(edge_processes) > 5:
    issues.append(f"Many renderer processes: {len(edge_processes)}")

print("\n🔍 IDENTIFIED ISSUES:")
if issues:
    for i, issue in enumerate(issues, 1):
        print(f"  {i}. {issue}")
else:
    print("  No obvious issues detected")

diagnosis["issues"] = issues

# Save diagnosis
diagnosis_file = OUTPUT_DIR / "round3_diagnosis.json"
with open(diagnosis_file, "w") as f:
    json.dump(diagnosis, f, indent=2)

print(f"\n✓ Full diagnosis saved to: {diagnosis_file}")

print("\n" + "=" * 80)
print("NEXT: Manual Investigation Required")
print("=" * 80)
print("""
Please provide the following from DevTools:

1. Current Console errors (screenshot or text)
2. Network tab showing:
   - Failed requests (red)
   - Pending requests (stuck)
   - Request timing
3. Application tab → Service Workers:
   - Status (activated/installing/waiting)
   - Scope
4. Application tab → Cache Storage:
   - Size and entries

This will help identify if the issue is:
A) Service worker still retrying blocked endpoints
B) New JavaScript errors preventing initialization  
C) Missing resources or network issues
D) Service worker in bad state
""")
