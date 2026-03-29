#!/usr/bin/env python3
"""
Round 3: Extract all sources from Edge DevTools
This script provides instructions for manual extraction since automated CDP is complex
"""

import os
import json
import subprocess
from pathlib import Path

SPARE_DIR = Path("/home/deck/dump/xbplay spare parts")
OUTPUT_DIR = SPARE_DIR / "round3_sources"
OUTPUT_DIR.mkdir(exist_ok=True)

print("=" * 80)
print("ROUND 3: JavaScript Source Extraction Guide")
print("=" * 80)

# Method 1: Check if Edge has remote debugging enabled
print("\n📋 METHOD 1: Enable Remote Debugging (Automated Extraction)")
print("-" * 80)

# Check for debug ports
result = subprocess.run(
    ["ss", "-tlnp"],
    capture_output=True,
    text=True
)

debug_ports = []
for line in result.stdout.splitlines():
    if "9222" in line or "9223" in line or "9224" in line:
        debug_ports.append(line)

if debug_ports:
    print("✓ Found remote debugging ports:")
    for port in debug_ports:
        print(f"  {port}")
    print("\n⚠️  Remote debugging is ENABLED but needs proper CDP client")
else:
    print("✗ Remote debugging NOT enabled")
    print("\nTo enable, kill current Edge and run:")
    print("  flatpak run com.microsoft.Edge --remote-debugging-port=9222 \\")
    print("    --profile-directory=Default --app-id=kanajofaghckoijdglhndcjjlemljefb \\")
    print("    '--app-url=https://play.xbox.com/?pwaVersion=1.0'")

# Method 2: Manual extraction via DevTools
print("\n" + "=" * 80)
print("📋 METHOD 2: Manual Source Download (RECOMMENDED)")
print("=" * 80)

manual_steps = """
Since you're looking at DevTools right now, here's how to download ALL sources:

1. Open DevTools (F12) on the Xbox Play PWA
2. Go to the "Sources" tab (top menu)
3. In the left sidebar, you'll see a tree of sources:
   - Look for: play.xbox.com
   - Look for: Various .js files (especially webpack bundles)
   - Look for: Service Worker scripts

4. For EACH JavaScript file you want to save:
   a. Right-click on the file in the left sidebar
   b. Select "Save as..." or "Save for overrides"
   c. Save to: {0}
   
5. Alternative: Use Overrides feature:
   a. In Sources tab, click "Overrides" (left sidebar)
   b. Click "+ Select folder for overrides"
   c. Choose: {0}
   d. Grant permission
   e. Refresh the page - all sources will auto-save!

6. Or use the Network tab:
   a. Go to Network tab
   b. Filter by "JS" 
   c. Right-click each JS file → "Save as..."
   d. Save all to: {0}
"""

print(manual_steps.format(OUTPUT_DIR))

# Method 3: Extract from HAR file  
print("\n" + "=" * 80)
print("📋 METHOD 3: Extract from HAR File (AUTOMATED)")
print("=" * 80)

har_files = list(SPARE_DIR.glob("*.har"))
if har_files:
    print(f"✓ Found {len(har_files)} HAR files:")
    for har in har_files:
        print(f"  - {har.name}")
    
    print("\nExtracting JavaScript from HAR files...")
    
    js_count = 0
    for har_file in har_files:
        try:
            with open(har_file) as f:
                har_data = json.load(f)
            
            entries = har_data.get("log", {}).get("entries", [])
            
            for i, entry in enumerate(entries):
                url = entry.get("request", {}).get("url", "")
                response = entry.get("response", {})
                content = response.get("content", {})
                
                mime_type = content.get("mimeType", "")
                text = content.get("text", "")
                
                # Check if it's JavaScript
                if ("javascript" in mime_type or url.endswith(".js")) and text:
                    # Generate filename from URL
                    filename = url.split("/")[-1].split("?")[0]
                    if not filename or filename == "":
                        filename = f"script_{i}.js"
                    
                    # Remove invalid chars
                    filename = "".join(c for c in filename if c.isalnum() or c in "._-")
                    
                    output_file = OUTPUT_DIR / f"{har_file.stem}_{filename}"
                    
                    # Handle base64 encoding
                    if content.get("encoding") == "base64":
                        import base64
                        text = base64.b64decode(text).decode("utf-8", errors="ignore")
                    
                    with open(output_file, "w") as out:
                        out.write(text)
                    
                    size_kb = len(text) / 1024
                    print(f"  ✓ Extracted: {filename} ({size_kb:.1f} KB)")
                    js_count += 1
        
        except Exception as e:
            print(f"  ⚠️  Error processing {har_file.name}: {e}")
    
    print(f"\n✓ Extracted {js_count} JavaScript files from HAR archives")
else:
    print("✗ No HAR files found")
    print("\nTo create a HAR file:")
    print("  1. Open DevTools → Network tab")
    print("  2. Refresh the page")
    print("  3. Right-click in network log → 'Save all as HAR with content'")
    print(f"  4. Save to: {SPARE_DIR}")

# Method 4: Look in existing captures
print("\n" + "=" * 80)
print("📋 METHOD 4: Checking Existing Captures")
print("=" * 80)

# Look for any JS files already in spare parts
existing_js = list(SPARE_DIR.glob("*.js"))
if existing_js:
    print(f"✓ Found {len(existing_js)} existing .js files:")
    for js_file in existing_js[:10]:
        size_kb = js_file.stat().st_size / 1024
        print(f"  - {js_file.name} ({size_kb:.1f} KB)")
    
    if len(existing_js) > 10:
        print(f"  ... and {len(existing_js) - 10} more")
    
    # Copy them to round3_sources
    print("\nCopying to round3_sources/...")
    for js_file in existing_js:
        dest = OUTPUT_DIR / f"existing_{js_file.name}"
        subprocess.run(["cp", str(js_file), str(dest)])
    print(f"✓ Copied {len(existing_js)} files")
else:
    print("✗ No existing .js files found")

# Summary
print("\n" + "=" * 80)
print("SUMMARY")
print("=" * 80)

all_sources = list(OUTPUT_DIR.glob("*"))
js_sources = [f for f in all_sources if f.suffix == ".js"]

print(f"Total files in round3_sources: {len(all_sources)}")
print(f"JavaScript files: {len(js_sources)}")

if js_sources:
    print("\n📁 Downloaded JavaScript files:")
    for js in sorted(js_sources)[:20]:
        size_kb = js.stat().st_size / 1024
        print(f"  - {js.name} ({size_kb:.1f} KB)")
    
    if len(js_sources) > 20:
        print(f"  ... and {len(js_sources) - 20} more")

print(f"\n✓ All sources saved to: {OUTPUT_DIR}")

# Create index
index_data = {
    "extraction_date": subprocess.run(["date"], capture_output=True, text=True).stdout.strip(),
    "total_files": len(all_sources),
    "js_files": len(js_sources),
    "files": [str(f.name) for f in all_sources]
}

with open(OUTPUT_DIR / "INDEX.json", "w") as f:
    json.dump(index_data, f, indent=2)

print(f"✓ Index created: INDEX.json")

print("\n" + "=" * 80)
print("NEXT STEPS")
print("=" * 80)
print("1. If you haven't already, use METHOD 2 to manually save sources from DevTools")
print("2. Alternatively, create a new HAR file and re-run this script")
print("3. Once sources are collected, we can analyze them for the root cause")
print("=" * 80)
