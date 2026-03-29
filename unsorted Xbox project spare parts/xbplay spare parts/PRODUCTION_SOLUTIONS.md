# CORS Test SUCCESS - Production Solutions

**Date:** March 27, 2026 03:33
**Status:** ✅ CORS CONFIRMED AS ROOT CAUSE
**Test Result:** App loads successfully with --disable-web-security

## Test Results

With `--disable-web-security` flag:
- ✅ Auth token request succeeds
- ✅ WebSocket connects with valid AuthKey
- ✅ App loads fully
- ✅ Access to both xCloud play (subscription) and Remote play (free)

**This confirms:** The infinite retry loop was caused by CORS blocking the auth request.

## Production Solutions (Pick One)

### Option 1: Use Brave Permanently ⭐ EASIEST
**If Brave already works without the flag**, just use it:

```bash
# Create launcher script
cat > ~/xbox-play.sh << 'EOF'
#!/bin/bash
flatpak run com.brave.Browser \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
EOF
chmod +x ~/xbox-play.sh
```

**Pros:**
- No security risks
- No extra software
- Already proven working

**Cons:**
- Need to verify Brave doesn't need --disable-web-security too
- Different browser (if you prefer Edge)

**Test:** Close current Edge, run Brave normally (without flags), see if it works.

---

### Option 2: Electron Wrapper 🚀 BEST FOR DESKTOP

Package the PWA in Electron where auth happens in main process (no CORS):

**Quick Proof-of-Concept:**
```bash
cd /tmp
npm init -y
npm install electron
```

**main.js:**
```javascript
const { app, BrowserWindow, ipcMain } = require('electron');
const https = require('https');

// Handle auth token requests from renderer
ipcMain.handle('get-auth-token', async (event, xuid) => {
  return new Promise((resolve, reject) => {
    https.get(
      `https://chat.xboxlive.com/users/xuid(${xuid})/chat/auth`,
      {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
      },
      (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
          try {
            const authKey = JSON.parse(data).AuthKey;
            resolve(authKey);
          } catch (e) {
            reject(e);
          }
        });
      }
    ).on('error', reject);
  });
});

app.whenReady().then(() => {
  const win = new BrowserWindow({
    width: 1280,
    height: 720,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: __dirname + '/preload.js'
    }
  });
  
  win.loadURL('https://play.xbox.com/?pwaVersion=1.0');
});
```

**preload.js:**
```javascript
const { contextBridge, ipcRenderer } = require('electron');

// Inject auth handler before page loads
contextBridge.exposeInMainWorld('electronAPI', {
  getAuthToken: (xuid) => ipcRenderer.invoke('get-auth-token', xuid)
});
```

**Then patch the app's JS to use it:**
```javascript
// In DevTools Overrides or via preload injection:
const originalGenerateAuthToken = window.generateAuthToken;
window.generateAuthToken = async function(xuid) {
  if (window.electronAPI) {
    return window.electronAPI.getAuthToken(xuid);
  }
  return originalGenerateAuthToken(xuid);
};
```

**Pros:**
- Production-safe
- No CORS issues
- Full control
- Can package as native app

**Cons:**
- Requires Node.js/Electron
- Need to inject preload script
- More complex setup

---

### Option 3: Local Reverse Proxy 🌐 WORKS IN BROWSER

Run a local server that proxies chat requests:

```javascript
// proxy-server.js
const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const cors = require('cors');

const app = express();
app.use(cors()); // Allow all origins

app.use('/chat', createProxyMiddleware({
  target: 'https://chat.xboxlive.com',
  changeOrigin: true,
  pathRewrite: { '^/chat': '' },
  onProxyRes: (proxyRes) => {
    // Add permissive CORS headers
    proxyRes.headers['access-control-allow-origin'] = '*';
    proxyRes.headers['access-control-allow-methods'] = 'GET,POST';
  }
}));

app.listen(3001, () => {
  console.log('Xbox Chat proxy running on http://localhost:3001');
});
```

**Then use DevTools Overrides to redirect requests:**
```javascript
// Override the auth URL
const originalAuthUrl = 'https://chat.xboxlive.com/users/...';
const proxiedAuthUrl = 'http://localhost:3001/chat/users/...';
```

**Pros:**
- Works in any browser
- No Electron needed
- Can use normal Edge

**Cons:**
- Requires Node.js server running
- Must modify app code (via overrides)
- Proxy must run whenever using app

---

### Option 4: Browser Extension 🔌 ADVANCED

Create a Chrome/Edge extension that intercepts and proxies chat requests:

**manifest.json:**
```json
{
  "manifest_version": 3,
  "name": "Xbox Play CORS Fix",
  "version": "1.0",
  "permissions": ["webRequest", "webRequestBlocking"],
  "host_permissions": ["https://chat.xboxlive.com/*"],
  "background": {
    "service_worker": "background.js"
  }
}
```

**background.js:**
```javascript
chrome.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    // Modify headers to bypass CORS
    details.requestHeaders.push({
      name: 'Origin',
      value: 'https://chat.xboxlive.com'
    });
    return { requestHeaders: details.requestHeaders };
  },
  { urls: ["https://chat.xboxlive.com/*"] },
  ["blocking", "requestHeaders"]
);
```

**Pros:**
- Seamless once installed
- Works across sessions
- Browser-native

**Cons:**
- Need to package as extension
- Must enable developer mode
- May break on browser updates

---

### Option 5: Just Accept the Limitation 🤷

Use Edge with `--disable-web-security` ONLY for Xbox Play:

```bash
# Create a dedicated launcher
cat > ~/xbox-play-edge.sh << 'EOF'
#!/bin/bash
# WARNING: Only use for Xbox Play, security disabled!
flatpak run com.microsoft.Edge \
  --disable-web-security \
  --user-data-dir=/tmp/edge-xbox-only \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
EOF
chmod +x ~/xbox-play-edge.sh
```

**Use separate user-data-dir so:**
- Regular Edge stays secure
- Xbox Play has security disabled in its isolated profile
- Can't access other sites from Xbox Play window

**Pros:**
- Simple
- Already works
- Isolated profile reduces risk

**Cons:**
- Still a security risk if you navigate elsewhere
- Not recommended for production

---

## Recommendation

**Immediate:** Test if Brave works without `--disable-web-security`:
```bash
flatpak run com.brave.Browser \
  --profile-directory=Default \
  --app-id=kanajofaghckoijdglhndcjjlemljefb \
  '--app-url=https://play.xbox.com/?pwaVersion=1.0'
```

**If Brave works:** ✅ Use Brave permanently (Option 1)

**If Brave also needs flag:** Build Electron wrapper (Option 2) or use isolated Edge profile (Option 5)

**For sharing with others:** Electron wrapper is cleanest solution

---

## Files to Create

Shall I create:
1. ✅ Brave test script
2. ⏳ Electron wrapper proof-of-concept
3. ⏳ Proxy server example
4. ⏳ Browser extension template

Which solution interests you most?
