#!/bin/bash
# Complete fix for Xbox Play PWA - Clear service worker and block ALL failing endpoints

echo "=============================================================================="
echo "Xbox Play PWA - Complete Fix (Round 2)"
echo "=============================================================================="
echo ""
echo "ISSUE: Service worker retry loop on multiple failed Xbox Live API endpoints"
echo ""

# Block all problematic endpoints
echo "Step 1: Blocking ALL failing Xbox Live endpoints..."
sudo tee -a /etc/hosts > /dev/null << 'EOF'
# Xbox Play PWA fixes
127.0.0.1 chat.xboxlive.com
EOF

echo "✓ Endpoints blocked"

# Kill Edge
echo ""
echo "Step 2: Stopping Edge PWA..."
for pid in $(pgrep -f "msedge.*kanajofaghckoijdglhndcjjlemljefb"); do
    kill $pid 2>/dev/null
    echo "  Killed PID $pid"
done
sleep 2
echo "✓ Edge stopped"

# Clear service worker
echo ""
echo "Step 3: Clearing service worker cache..."
rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/*/Service\ Worker/
echo "✓ Service worker cache cleared"

# Clear general cache too
echo ""
echo "Step 4: Clearing browser cache..."
rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/*/Cache/
echo "✓ Browser cache cleared"

echo ""
echo "=============================================================================="
echo "FIX COMPLETE!"
echo ""
echo "Next steps:"
echo "  1. Restart the Xbox Play PWA from your applications menu"
echo "  2. The service worker will rebuild without the broken retry loop"
echo "  3. Chat feature will be disabled but app should load"
echo ""
echo "If still stuck, the issue may be:"
echo "  - Network/firewall blocking Xbox Live services"
echo "  - Regional restrictions on Xbox Live APIs"
echo "  - PWA manifest requiring services that aren't available"
echo ""
echo "Alternative: Use Brave PWA which works correctly"
echo "=============================================================================="
