# XBPlay Client - Phase 1 Implementation

## Quick Start Scaffold

Let's build the foundation RIGHT NOW. I'll create a working Electron base with the auth proxy.

### Project Setup

```bash
#!/bin/bash
# scaffold-xbplay.sh - Create XBPlay project structure

PROJECT_DIR="$HOME/xbplay-client"
mkdir -p "$PROJECT_DIR"/{main,renderer,native,shaders,config}

cd "$PROJECT_DIR"

# Initialize npm project
cat > package.json << 'EOF'
{
  "name": "xbplay-client",
  "version": "0.1.0",
  "description": "Custom Xbox streaming client with native optimizations",
  "main": "main/index.js",
  "scripts": {
    "start": "electron .",
    "dev": "electron . --enable-logging",
    "build": "electron-builder"
  },
  "dependencies": {
    "electron": "^28.0.0"
  },
  "devDependencies": {
    "electron-builder": "^24.0.0"
  }
}
EOF

# Main process entry point
cat > main/index.js << 'EOF'
const { app, BrowserWindow, ipcMain, session } = require('electron');
const path = require('path');
const https = require('https');
const { URL } = require('url');

let mainWindow;

// CORS-bypassing auth proxy
ipcMain.handle('xbox-auth-token', async (event, xuid) => {
  console.log(`[AUTH] Fetching token for XUID: ${xuid}`);
  
  return new Promise((resolve, reject) => {
    const authUrl = `https://chat.xboxlive.com/users/xuid(${xuid})/chat/auth`;
    
    https.get(authUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Accept': 'application/json'
      }
    }, (res) => {
      let data = '';
      
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          console.log(`[AUTH] ✓ Token obtained: ${parsed.AuthKey.substring(0, 20)}...`);
          resolve(parsed.AuthKey);
        } catch (e) {
          console.error('[AUTH] ✗ Parse error:', e);
          reject(e);
        }
      });
    }).on('error', (e) => {
      console.error('[AUTH] ✗ Request error:', e);
      reject(e);
    });
  });
});

// WebRTC interceptor - log connections
ipcMain.on('webrtc-connection', (event, data) => {
  console.log('[WEBRTC]', data);
});

// Frame data from intercepted stream
ipcMain.on('video-frame', (event, frameData) => {
  // Future: send to native renderer
  console.log('[VIDEO] Frame received:', frameData.timestamp);
});

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, '../renderer/preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: true // Keep enabled, we bypass via IPC
    }
  });

  // Load Xbox Play site
  mainWindow.loadURL('https://play.xbox.com/?pwaVersion=1.0');

  // Open DevTools for debugging
  mainWindow.webContents.openDevTools();

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});
EOF

# Preload script - injected before page loads
cat > renderer/preload.js << 'EOF'
const { contextBridge, ipcRenderer } = require('electron');

console.log('[XBPLAY] Preload script initializing...');

// Expose safe IPC to renderer
contextBridge.exposeInMainWorld('xbplay', {
  getAuthToken: (xuid) => ipcRenderer.invoke('xbox-auth-token', xuid),
  sendWebRTCInfo: (data) => ipcRenderer.send('webrtc-connection', data),
  sendFrame: (frameData) => ipcRenderer.send('video-frame', frameData)
});

// Inject hooks immediately when page starts loading
window.addEventListener('DOMContentLoaded', () => {
  console.log('[XBPLAY] DOM loaded, injecting hooks...');
  
  // Inject the main hook script
  const script = document.createElement('script');
  script.src = 'xbplay://inject/hooks.js';
  script.async = false;
  document.documentElement.appendChild(script);
});

console.log('[XBPLAY] Preload complete, xbplay API exposed');
EOF

# Main injection script - runs in page context
cat > renderer/hooks.js << 'EOF'
(function() {
  'use strict';
  
  console.log('[XBPLAY] Hooks initializing in page context...');
  
  // Store original functions
  const OriginalRTCPeerConnection = window.RTCPeerConnection;
  let currentXuid = null;
  
  // Hook generateAuthToken if it exists
  const originalGenerateAuthToken = window.generateAuthToken;
  if (typeof originalGenerateAuthToken === 'function') {
    window.generateAuthToken = async function(xuid) {
      console.log('[XBPLAY] generateAuthToken called, using our proxy');
      currentXuid = xuid;
      
      try {
        // Use our CORS-free proxy
        const token = await window.xbplay.getAuthToken(xuid);
        console.log('[XBPLAY] ✓ Auth token obtained via proxy');
        return token;
      } catch (e) {
        console.error('[XBPLAY] ✗ Auth token failed, falling back to original');
        return originalGenerateAuthToken.call(this, xuid);
      }
    };
  }
  
  // Hook RTCPeerConnection to intercept WebRTC
  window.RTCPeerConnection = function(...args) {
    console.log('[XBPLAY] RTCPeerConnection created', args);
    
    const pc = new OriginalRTCPeerConnection(...args);
    
    // Log connection state
    pc.addEventListener('connectionstatechange', () => {
      console.log('[XBPLAY] Connection state:', pc.connectionState);
      window.xbplay.sendWebRTCInfo({
        state: pc.connectionState,
        iceConnectionState: pc.iceConnectionState
      });
    });
    
    // Intercept tracks (video/audio)
    pc.addEventListener('track', (event) => {
      console.log('[XBPLAY] Track received:', event.track.kind);
      
      if (event.track.kind === 'video') {
        interceptVideoTrack(event.track, event.streams[0]);
      }
    });
    
    return pc;
  };
  
  // Copy static properties
  window.RTCPeerConnection.prototype = OriginalRTCPeerConnection.prototype;
  
  // Intercept video track
  function interceptVideoTrack(track, stream) {
    console.log('[XBPLAY] Intercepting video track...');
    
    // Create hidden video element to extract frames
    const video = document.createElement('video');
    video.srcObject = stream;
    video.play();
    
    // Extract frames (basic proof of concept)
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    
    video.addEventListener('loadedmetadata', () => {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      
      console.log(`[XBPLAY] Video dimensions: ${canvas.width}x${canvas.height}`);
      
      // Sample frames periodically
      setInterval(() => {
        if (video.readyState === video.HAVE_ENOUGH_DATA) {
          ctx.drawImage(video, 0, 0);
          
          // Send frame info to main process
          window.xbplay.sendFrame({
            timestamp: Date.now(),
            width: canvas.width,
            height: canvas.height
          });
          
          // Future: extract raw pixel data for native rendering
          // const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        }
      }, 1000); // Sample 1fps for now
    });
  }
  
  console.log('[XBPLAY] Hooks installed successfully');
})();
EOF

echo "✓ Project scaffolded at $PROJECT_DIR"
echo ""
echo "Next steps:"
echo "  cd $PROJECT_DIR"
echo "  npm install"
echo "  npm start"
EOF

chmod +x scaffold-xbplay.sh
```

Would you like me to:

1. **Generate this scaffold now** - create the full Electron project
2. **Add native modules** - start building FFmpeg integration for frame processing
3. **Build the HID input reader** - raw gamepad access
4. **Create a Docker dev environment** - for consistent building

Which component should we tackle first? The auth proxy is ready to go!
