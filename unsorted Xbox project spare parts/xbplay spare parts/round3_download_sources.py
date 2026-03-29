#!/usr/bin/env python3
"""
Download all JavaScript sources from Edge PWA DevTools
Connects via Chrome DevTools Protocol and extracts all scripts
"""

import json
import os
import re
import subprocess
import time
from pathlib import Path
import urllib.parse

def find_edge_debug_port():
    """Find the remote debugging port for Edge"""
    try:
        # Check if Edge has debug port enabled
        result = subprocess.run(
            ["netstat", "-tlnp"],
            capture_output=True,
            text=True
        )
        
        # Look for ports that might be Edge debugging
        for line in result.stdout.splitlines():
            if "msedge" in line and "LISTEN" in line:
                parts = line.split()
                for part in parts:
                    if ":" in part and "127.0.0.1:" in part:
                        port = part.split(":")[-1]
                        print(f"Found potential debug port: {port}")
                        return port
        
        # Try common debugging ports
        common_ports = [9222, 9223, 9224, 9225]
        for port in common_ports:
            result = subprocess.run(
                ["curl", "-s", f"http://localhost:{port}/json"],
                capture_output=True,
                text=True,
                timeout=2
            )
            if result.returncode == 0 and result.stdout:
                print(f"Found Edge debug port: {port}")
                return port
                
    except Exception as e:
        print(f"Error finding debug port: {e}")
    
    return None

def get_page_targets(port):
    """Get all page targets from Edge"""
    try:
        result = subprocess.run(
            ["curl", "-s", f"http://localhost:{port}/json"],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode == 0:
            targets = json.loads(result.stdout)
            return targets
    except Exception as e:
        print(f"Error getting targets: {e}")
    
    return []

def connect_to_target(port, target_id):
    """Connect to a specific target and get sources"""
    import websocket
    import json
    
    ws_url = f"ws://localhost:{port}/devtools/page/{target_id}"
    
    try:
        ws = websocket.create_connection(ws_url, timeout=10)
        
        # Enable Debugger domain
        ws.send(json.dumps({"id": 1, "method": "Debugger.enable"}))
        response = ws.recv()
        
        # Get all scripts
        ws.send(json.dumps({"id": 2, "method": "Debugger.scriptParsed"}))
        
        # Wait for script events
        scripts = []
        timeout = time.time() + 5
        
        while time.time() < timeout:
            try:
                msg = ws.recv()
                data = json.loads(msg)
                
                if "method" in data and data["method"] == "Debugger.scriptParsed":
                    params = data.get("params", {})
                    scripts.append(params)
                    
            except Exception as e:
                break
        
        ws.close()
        return scripts
        
    except Exception as e:
        print(f"Error connecting to target: {e}")
        return []

def download_source_content(port, target_id, script_id):
    """Download the actual source content for a script"""
    import websocket
    import json
    
    ws_url = f"ws://localhost:{port}/devtools/page/{target_id}"
    
    try:
        ws = websocket.create_connection(ws_url, timeout=10)
        
        # Enable Debugger
        ws.send(json.dumps({"id": 1, "method": "Debugger.enable"}))
        ws.recv()
        
        # Get script source
        ws.send(json.dumps({
            "id": 2,
            "method": "Debugger.getScriptSource",
            "params": {"scriptId": script_id}
        }))
        
        response = json.loads(ws.recv())
        ws.close()
        
        if "result" in response and "scriptSource" in response["result"]:
            return response["result"]["scriptSource"]
            
    except Exception as e:
        print(f"Error downloading source: {e}")
    
    return None

def sanitize_filename(url):
    """Convert URL to safe filename"""
    # Remove protocol
    name = re.sub(r'^https?://', '', url)
    # Replace invalid chars
    name = re.sub(r'[<>:"/\\|?*]', '_', name)
    # Limit length
    if len(name) > 200:
        name = name[:200]
    return name

def main():
    print("=" * 80)
    print("ROUND 3: Downloading JavaScript Sources from Edge DevTools")
    print("=" * 80)
    
    output_dir = Path("/home/deck/dump/xbplay spare parts/round3_sources")
    output_dir.mkdir(exist_ok=True)
    
    # Check if websocket module is available
    try:
        import websocket
    except ImportError:
        print("\n⚠️  websocket-client not installed, installing now...")
        subprocess.run(["pip3", "install", "websocket-client"], check=True)
        import websocket
    
    # Find Edge debugging port
    print("\n1. Finding Edge debugging port...")
    port = find_edge_debug_port()
    
    if not port:
        print("\n❌ Edge debugging port not found!")
        print("\nTo enable debugging, restart Edge with:")
        print("  flatpak run com.microsoft.Edge --remote-debugging-port=9222")
        print("\nOr kill current Edge and I'll restart it with debugging enabled.")
        return
    
    print(f"✓ Found debug port: {port}")
    
    # Get all targets
    print("\n2. Getting page targets...")
    targets = get_page_targets(port)
    
    if not targets:
        print("❌ No targets found!")
        return
    
    print(f"✓ Found {len(targets)} targets")
    
    # Find Xbox Play target
    xbox_targets = []
    for target in targets:
        url = target.get("url", "")
        title = target.get("title", "")
        
        if "play.xbox.com" in url or "xbox" in title.lower():
            xbox_targets.append(target)
            print(f"  - {title[:60]} | {url[:60]}")
    
    if not xbox_targets:
        print("\n⚠️  No Xbox Play targets found, downloading from all targets...")
        xbox_targets = targets
    
    # Download sources from each target
    all_sources = {}
    
    for i, target in enumerate(xbox_targets[:5]):  # Limit to 5 targets
        target_id = target.get("id")
        title = target.get("title", "unknown")
        
        print(f"\n3. Connecting to target {i+1}: {title[:60]}...")
        
        # Try direct approach: fetch via CDP
        try:
            result = subprocess.run(
                ["curl", "-s", "-X", "POST", 
                 f"http://localhost:{port}/json",
                 "-H", "Content-Type: application/json"],
                capture_output=True,
                text=True,
                timeout=5
            )
        except:
            pass
        
        # Alternative: Just download the page source directly
        try:
            url = target.get("url", "")
            if url and url.startswith("http"):
                print(f"  Downloading page source from: {url}")
                
                safe_name = sanitize_filename(url)
                output_file = output_dir / f"page_{i}_{safe_name}.html"
                
                subprocess.run(
                    ["curl", "-s", "-L", url, "-o", str(output_file)],
                    timeout=10
                )
                
                if output_file.exists() and output_file.stat().st_size > 0:
                    print(f"  ✓ Downloaded to: {output_file.name}")
                    all_sources[str(output_file)] = url
        except Exception as e:
            print(f"  ⚠️  Error: {e}")
    
    # Alternative approach: Extract from Edge's profile directory
    print("\n4. Extracting sources from Edge profile...")
    
    edge_profile = Path("/home/deck/.var/app/com.microsoft.Edge/config/microsoft-edge")
    
    if edge_profile.exists():
        # Look for cached JS files
        for js_file in edge_profile.rglob("*.js"):
            if js_file.is_file() and js_file.stat().st_size > 100:
                try:
                    rel_path = js_file.relative_to(edge_profile)
                    output_file = output_dir / f"cached_{rel_path.name}"
                    
                    # Copy the file
                    subprocess.run(["cp", str(js_file), str(output_file)])
                    
                    print(f"  ✓ Copied: {js_file.name}")
                    all_sources[str(output_file)] = str(rel_path)
                    
                except Exception as e:
                    pass
    
    # Try to extract from service worker cache
    print("\n5. Extracting from Service Worker cache...")
    
    sw_dirs = list(edge_profile.rglob("Service Worker"))
    for sw_dir in sw_dirs:
        if sw_dir.is_dir():
            print(f"  Checking: {sw_dir}")
            
            for cache_file in sw_dir.rglob("*"):
                if cache_file.is_file() and cache_file.stat().st_size > 100:
                    try:
                        # Try to read as text
                        with open(cache_file, 'rb') as f:
                            content = f.read(1000)
                            
                            # Check if it looks like JavaScript
                            if b"function" in content or b"const" in content or b"var" in content:
                                output_file = output_dir / f"sw_cache_{cache_file.name}"
                                subprocess.run(["cp", str(cache_file), str(output_file)])
                                print(f"  ✓ Found JS in: {cache_file.name}")
                                all_sources[str(output_file)] = str(cache_file)
                    except:
                        pass
    
    # Summary
    print("\n" + "=" * 80)
    print(f"SUMMARY: Downloaded {len(all_sources)} source files")
    print("=" * 80)
    
    # Create index
    index_file = output_dir / "SOURCE_INDEX.json"
    with open(index_file, 'w') as f:
        json.dump({
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "total_files": len(all_sources),
            "sources": all_sources,
            "targets": [{"title": t.get("title"), "url": t.get("url")} for t in xbox_targets]
        }, f, indent=2)
    
    print(f"\n✓ Index saved to: {index_file}")
    print(f"✓ All files saved to: {output_dir}")
    
    # List downloaded files
    print("\nDownloaded files:")
    for f in sorted(output_dir.glob("*")):
        if f.is_file():
            size_kb = f.stat().st_size / 1024
            print(f"  - {f.name} ({size_kb:.1f} KB)")

if __name__ == "__main__":
    main()
