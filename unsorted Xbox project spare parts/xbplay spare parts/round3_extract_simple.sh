#!/bin/bash
# Simple extraction of all JS sources from Edge PWA

SPARE_DIR="/home/deck/dump/xbplay spare parts"
OUTPUT_DIR="$SPARE_DIR/round3_sources"
mkdir -p "$OUTPUT_DIR"

echo "================================================================================"
echo "ROUND 3: Extracting JavaScript Sources (Simple Method)"
echo "================================================================================"

echo ""
echo "1. Extracting from Edge profile directories..."
EDGE_PROFILE="/home/deck/.var/app/com.microsoft.Edge/config/microsoft-edge"

# Find all JS files in Edge profile
echo ""
echo "Finding .js files..."
find "$EDGE_PROFILE" -name "*.js" -type f 2>/dev/null | while read jsfile; do
    size=$(stat -f "%z" "$jsfile" 2>/dev/null || stat -c "%s" "$jsfile" 2>/dev/null)
    
    # Skip very small files
    if [ "$size" -gt 500 ]; then
        # Create a safe filename
        basename=$(basename "$jsfile")
        dirname=$(dirname "$jsfile")
        parent=$(basename "$dirname")
        
        # Copy with parent directory name to avoid conflicts
        cp "$jsfile" "$OUTPUT_DIR/${parent}_${basename}"
        echo "  ✓ Copied: $parent/$basename ($(($size / 1024)) KB)"
    fi
done

echo ""
echo "2. Extracting from Service Worker cache..."
find "$EDGE_PROFILE" -path "*/Service Worker/*" -type f 2>/dev/null | while read swfile; do
    size=$(stat -f "%z" "$swfile" 2>/dev/null || stat -c "%s" "$swfile" 2>/dev/null)
    
    if [ "$size" -gt 500 ]; then
        # Check if it looks like JS
        if head -c 1000 "$swfile" | grep -q -E "(function|const|var|let|class)"; then
            basename=$(basename "$swfile")
            cp "$swfile" "$OUTPUT_DIR/sw_${basename}"
            echo "  ✓ Found JS: sw_$basename ($(($size / 1024)) KB)"
        fi
    fi
done

echo ""
echo "3. Extracting from Cache directories..."
find "$EDGE_PROFILE" -path "*/Cache/*" -type f -name "*js*" 2>/dev/null | head -50 | while read cachefile; do
    size=$(stat -f "%z" "$cachefile" 2>/dev/null || stat -c "%s" "$cachefile" 2>/dev/null)
    
    if [ "$size" -gt 500 ]; then
        basename=$(basename "$cachefile")
        cp "$cachefile" "$OUTPUT_DIR/cache_${basename}"
        echo "  ✓ Cached: $basename ($(($size / 1024)) KB)"
    fi
done

echo ""
echo "4. Extracting from IndexedDB..."
find "$EDGE_PROFILE" -path "*/IndexedDB/*" -type f 2>/dev/null | while read dbfile; do
    size=$(stat -f "%z" "$dbfile" 2>/dev/null || stat -c "%s" "$dbfile" 2>/dev/null)
    
    if [ "$size" -gt 500 ] && [ "$size" -lt 10000000 ]; then
        # Check if it contains JS-like content
        if head -c 2000 "$dbfile" | grep -q -E "(play\.xbox\.com|function|const)"; then
            basename=$(basename "$dbfile")
            dirname=$(basename $(dirname "$dbfile"))
            cp "$dbfile" "$OUTPUT_DIR/idb_${dirname}_${basename}"
            echo "  ✓ IndexedDB: $dirname/$basename ($(($size / 1024)) KB)"
        fi
    fi
done

echo ""
echo "5. Creating file listing..."
ls -lh "$OUTPUT_DIR" > "$OUTPUT_DIR/FILES_LIST.txt"

echo ""
echo "================================================================================"
echo "EXTRACTION COMPLETE"
echo "================================================================================"
echo "Total files extracted: $(ls -1 "$OUTPUT_DIR" | wc -l)"
echo "Output directory: $OUTPUT_DIR"
echo ""
echo "Largest files:"
ls -lhS "$OUTPUT_DIR" | head -10

# Create summary
cat > "$OUTPUT_DIR/EXTRACTION_SUMMARY.txt" << EOF
Round 3 Source Extraction
=========================
Date: $(date)
Method: Direct filesystem extraction (no DevTools Protocol needed)

Extracted from:
- Edge profile: $EDGE_PROFILE
- Service Worker caches
- Browser cache directories  
- IndexedDB stores

Total files: $(ls -1 "$OUTPUT_DIR" | wc -l)

Files are prefixed with their source:
- sw_* : Service Worker cache
- cache_* : Browser cache
- idb_* : IndexedDB
- Other: Direct .js files from profile

Next: Open these files in a text editor to find the "unreadable.js" sources
that are causing issues in the DevTools console.
EOF

echo ""
cat "$OUTPUT_DIR/EXTRACTION_SUMMARY.txt"
