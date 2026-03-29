#!/bin/bash
# Test if Brave works WITHOUT --disable-web-security

SPARE_DIR="/home/deck/dump/xbplay spare parts"
LOG_FILE="$SPARE_DIR/brave_test_normal.log"

echo "========================================"
echo "Testing Brave WITHOUT CORS bypass"
echo "========================================"
echo ""

echo "Launching Brave with normal security..."
echo ""

flatpak run com.brave.Browser \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0' \
  2>&1 | tee $LOG_FILE &

echo ""
echo "If Brave loads successfully:"
echo "  → Use Brave permanently (EASIEST solution)"
echo ""
echo "If Brave also gets stuck:"
echo "  → Need Electron wrapper or proxy"
echo ""
echo "Log: $LOG_FILE"
