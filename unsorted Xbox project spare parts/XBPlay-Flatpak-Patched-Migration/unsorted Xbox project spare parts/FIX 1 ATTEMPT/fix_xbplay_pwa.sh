#!/bin/bash
# Quick fix for Xbox Play PWA - Block problematic chat endpoint

echo "=============================================="
echo "Xbox Play PWA - Chat Endpoint Fix"
echo "=============================================="
echo ""
echo "ROOT CAUSE: chat.xboxlive.com returns 404, causing infinite retry loop"
echo ""
echo "SOLUTIONS:"
echo "  1. Block chat.xboxlive.com (forces fast timeout)"
echo "  2. Switch to Brave PWA (works correctly)"
echo "  3. Clear Edge cache and restart"
echo ""
read -p "Choose option (1/2/3): " choice

case $choice in
  1)
    echo ""
    echo "Blocking chat.xboxlive.com in /etc/hosts..."
    echo "127.0.0.1 chat.xboxlive.com" | sudo tee -a /etc/hosts
    echo ""
    echo "Killing Edge PWA..."
    flatpak kill com.microsoft.Edge
    echo ""
    echo "Restart the Xbox Play PWA from your applications menu."
    echo "The chat feature will be disabled but the app should load."
    ;;
  
  2)
    echo ""
    echo "Launching Brave PWA (should work)..."
    flatpak run com.brave.Browser --profile-directory=Default --app-id=kanajofaghckoijdglhndcjjlemljefb &
    ;;
  
  3)
    echo ""
    echo "Killing Edge PWA..."
    flatpak kill com.microsoft.Edge
    sleep 2
    
    echo "Clearing service worker cache..."
    rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/Default/Service\ Worker/
    
    echo "Clearing general cache..."
    rm -rf ~/.var/app/com.microsoft.Edge/config/microsoft-edge/Default/Cache/
    
    echo ""
    echo "Cache cleared. Restart the Xbox Play PWA from your applications menu."
    ;;
  
  *)
    echo "Invalid choice"
    ;;
esac

echo ""
echo "=============================================="
echo "Additional Info:"
echo "  - Full analysis in: /home/deck/dump/xbplay spare parts/"
echo "  - See ROOT_CAUSE_WEBSOCKET_404.md for details"
echo "  - If issue persists, use Brave PWA instead"
echo "=============================================="
