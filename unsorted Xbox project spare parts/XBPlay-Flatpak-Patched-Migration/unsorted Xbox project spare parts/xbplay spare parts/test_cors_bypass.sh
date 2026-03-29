#!/bin/bash
# Test: Does disabling CORS fix Xbox Play PWA?

SPARE_DIR="/home/deck/dump/xbplay spare parts"
LOG_FILE="$SPARE_DIR/round3_cors_test.log"

echo "========================================" | tee $LOG_FILE
echo "CORS BYPASS TEST" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

# Step 1: Remove /etc/hosts block
echo "1. Removing /etc/hosts block (so auth can be attempted)..." | tee -a $LOG_FILE
sudo sed -i '/chat.xboxlive.com/d' /etc/hosts
echo "   ✓ Removed" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

# Step 2: Kill existing Edge
echo "2. Killing existing Edge PWA..." | tee -a $LOG_FILE
EDGE_PIDS=$(ps aux | grep "msedge.*kanajofaghckoijdglhndcjjlemljefb" | grep -v grep | awk '{print $2}')
if [ ! -z "$EDGE_PIDS" ]; then
    echo "$EDGE_PIDS" | while read pid; do
        echo "   Killing PID $pid" | tee -a $LOG_FILE
        kill $pid 2>/dev/null
    done
    sleep 2
else
    echo "   No Edge PWA running" | tee -a $LOG_FILE
fi
echo "" | tee -a $LOG_FILE

# Step 3: Launch with CORS disabled
echo "3. Launching Edge with --disable-web-security..." | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE
echo "   Command:" | tee -a $LOG_FILE
echo "   flatpak run com.microsoft.Edge \\" | tee -a $LOG_FILE
echo "     --disable-web-security \\" | tee -a $LOG_FILE
echo "     --user-data-dir=/tmp/edge-cors-test \\" | tee -a $LOG_FILE
echo "     --app-id=kanajofaghckoijdglhndcjjlemljefb \\" | tee -a $LOG_FILE
echo "     '--app-url=https://play.xbox.com/?pwaVersion=1.0'" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

flatpak run com.microsoft.Edge \
  --disable-web-security \
  --user-data-dir=/tmp/edge-cors-test \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0' \
  2>&1 | tee -a $LOG_FILE &

EDGE_PID=$!
echo "   Launched as PID $EDGE_PID" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE
echo "INSTRUCTIONS:" | tee -a $LOG_FILE
echo "========================================" | tee -a $LOG_FILE
echo "1. Wait for Xbox Play to load" | tee -a $LOG_FILE
echo "2. Open DevTools (F12)" | tee -a $LOG_FILE
echo "3. Check Console for:" | tee -a $LOG_FILE
echo "   ✓ 'Attempting connection to chat socket'" | tee -a $LOG_FILE
echo "   ✓ Successful auth token generation" | tee -a $LOG_FILE
echo "4. Check Network tab for:" | tee -a $LOG_FILE
echo "   ✓ GET /chat/auth → Status 200" | tee -a $LOG_FILE
echo "   ✓ WebSocket /chat/connect → Status 101" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE
echo "If app loads successfully:" | tee -a $LOG_FILE
echo "  → CORS is confirmed as root cause" | tee -a $LOG_FILE
echo "  → Need Electron wrapper or proxy solution" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE
echo "Log saved to: $LOG_FILE" | tee -a $LOG_FILE
