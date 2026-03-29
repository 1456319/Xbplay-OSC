# XBPlay Client - Complete Jules Task Breakdown
## Master Implementation Plan (500+ Atomic Tasks)

**Purpose:** Bite-sized tasks for Jules asynchronous coding agent  
**Format:** Each task is standalone, clear, requires NO follow-up questions  
**Execution:** Jules processes tasks sequentially, can run 3-4 parallel for redundancy

---

## Task Numbering System

```
t001-t050: Phase 1 - Project Setup & Electron Shell
t051-t100: Phase 2 - xbox-xcloud-player Integration  
t101-t150: Phase 3 - Authentication & Console Discovery
t151-t200: Phase 4 - WebRTC Streaming & Basic Input
t201-t250: Phase 5 - Advanced Input (Raw HID prep)
t251-t300: Phase 6 - Native Rendering Preparation
t301-t350: Phase 7 - FFmpeg Integration (LGPL only)
t351-t400: Phase 8 - OpenGL Rendering Pipeline
t401-t450: Phase 9 - Custom Shaders (FSR, CAS)
t451-t500: Phase 10 - LAN P2P Discovery & Direct Connect
t501-t550: Phase 11 - Performance Optimization
t551-t600: Phase 12 - Steam Deck Integration
t601-t650: Phase 13 - UI/UX Polish
t651-t700: Phase 14 - Testing & Bug Fixes
t701-t750: Phase 15 - Documentation & Release
```

---

## PHASE 1: PROJECT SETUP & ELECTRON SHELL (t001-t050)

### Repository Setup (t001-t010)
**t001:** Fork xbox-xcloud-player repository  
- Action: Use GitHub UI or API to fork https://github.com/unknownskl/xbox-xcloud-player  
- Result: Forked repo at our-org/xbox-xcloud-player  
- No code changes

**t002:** Create xbplay-client repository  
- Action: Create new GitHub repo named xbplay-client  
- Settings: Public, MIT license, Node gitignore  
- README: "Custom Xbox streaming client with native optimizations"

**t003:** Initialize package.json  
- Run: npm init -y in xbplay-client repo  
- Edit: name "xbplay-client", version "0.1.0", main "dist/main/index.js"  
- Add: description, repository URL, author

**t004:** Add core dependencies  
- Add to package.json dependencies: "electron": "^28.0.0"  
- Add devDependencies: "typescript": "^5.0.0", "@types/node": "^20.0.0"  
- Run: npm install

**t005:** Create directory structure  
- Create: src/main/, src/renderer/, src/lib/, src/native/  
- Create: shaders/, config/, docs/, assets/  
- Add: .gitkeep file in each empty directory

**t006:** Configure TypeScript  
- Create tsconfig.json with: target ES2020, module commonjs  
- Set: outDir "dist/", include "src/**/*.ts"  
- Enable: strict true, esModuleInterop true

**t007:** Add build scripts  
- In package.json scripts add: "build": "tsc"  
- Add: "start": "electron dist/main/index.js"  
- Add: "dev": "tsc && electron dist/main/index.js --enable-logging"

**t008:** Create ESLint config  
- Create .eslintrc.json with: extends recommended  
- Rules: no-console warn, @typescript-eslint/no-explicit-any error  
- Add: npm script "lint": "eslint src/**/*.ts"

**t009:** Add .gitignore entries  
- Add: node_modules/, dist/, *.log  
- Add: .DS_Store, thumbs.db  
- Add: config/local.json (for dev secrets)

**t010:** Create README structure  
- Add sections: Features, Installation, Usage, Development  
- Add: MIT license badge, Node version badge  
- Add: Quick start instructions

### Electron Main Process (t011-t025)

**t011:** Create main process entry point  
- File: src/main/index.ts  
- Import: app, BrowserWindow from electron  
- Create: empty main() function with app.whenReady()

**t012:** Implement createWindow function  
- Function signature: createWindow(): BrowserWindow  
- Create: new BrowserWindow with width 1280, height 720  
- Set: webPreferences nodeIntegration false, contextIsolation true

**t013:** Add preload script path  
- In createWindow(), set: preload path.join(__dirname, '../renderer/preload.js')  
- Set: webSecurity true (we bypass via IPC, not disabled security)  
- Add: devTools autoopen in dev mode only

**t014:** Handle app lifecycle  
- On app.ready: call createWindow()  
- On window-all-closed: quit if not macOS (process.platform !== 'darwin')  
- On activate (macOS): recreate window if none exist

**t015:** Add graceful shutdown  
- On app.before-quit: cleanup resources  
- Close: all browser windows  
- Clear: any intervals or timeouts

**t016:** Create preload script  
- File: src/renderer/preload.ts  
- Import: contextBridge, ipcRenderer from electron  
- Expose: window.xbplay object via contextBridge

**t017:** Add IPC API surface  
- In preload, expose: xbplay.getAuthToken(xuid: string): Promise<string>  
- Expose: xbplay.sendWebRTCInfo(data: object): void  
- Expose: xbplay.sendFrame(frameData: object): void  
- All use ipcRenderer.invoke or ipcRenderer.send

**t018:** Implement IPC handlers  
- In src/main/index.ts, add: ipcMain.handle('xbox-auth-token', handler)  
- Handler: async (event, xuid) => { console.log('Auth for:', xuid); return 'placeholder'; }  
- Add: ipcMain.on for 'webrtc-connection' and 'video-frame' (just log)

**t019:** Add window management  
- Function: getMainWindow(): BrowserWindow | null  
- Store: mainWindow in module scope  
- Handle: window closed event to set mainWindow = null

**t020:** Add error handling  
- Wrap: createWindow() in try/catch  
- On error: show dialog.showErrorBox with error message  
- Log: errors to electron log file

**t021:** Configure app metadata  
- Set: app.name = 'XBPlay'  
- Set: app.setAboutPanelOptions with version, copyright  
- Set: custom user data path if needed

**t022:** Add command line flags  
- app.commandLine.appendSwitch: 'enable-gpu-rasterization'  
- Add: 'enable-accelerated-video-decode'  
- Add: 'enable-zero-copy' for video performance

**t023:** Handle deep links (future)  
- Register: xbplay:// protocol  
- Handler: app.setAsDefaultProtocolClient('xbplay')  
- Parse: deep link URLs for console connections

**t024:** Add crash reporting  
- Use: electron crashReporter  
- Upload: to our crash reporting service (or local file)  
- Include: version, platform, error stack

**t025:** Test electron shell  
- Run: npm run dev  
- Verify: window opens, shows DevTools  
- Check: console logs "Electron app started"

### Basic Renderer (t026-t040)

**t026:** Create HTML structure  
- File: src/renderer/index.html  
- Basic structure: <!DOCTYPE html>, head with meta charset UTF-8  
- Body: div id="root", script src="preload.js"

**t027:** Add CSS reset  
- File: src/renderer/styles.css  
- Reset: margin 0, padding 0, box-sizing border-box  
- Body: background #1a1a1a, color #ffffff, font-family -apple-system

**t028:** Create loading screen  
- In index.html: div id="loading" with spinner  
- CSS: centered, animated rotation  
- Text: "Initializing XBPlay..."

**t029:** Add app shell  
- Div structure: header, main, footer  
- Header: app title, user profile icon  
- Main: container for dynamic content  
- Footer: connection status indicator

**t030:** Wire HTML to main process  
- In src/main/index.ts createWindow():  
- Call: mainWindow.loadFile('src/renderer/index.html')  
- Or: use path.join(__dirname, '../../src/renderer/index.html')

**t031:** Add viewport meta  
- In HTML head: meta name="viewport" content="width=device-width"  
- Add: CSP meta tag restricting script sources  
- Add: theme-color meta for OS integration

**t032:** Create renderer entry script  
- File: src/renderer/app.ts  
- Export: init() function that starts app  
- Import: and call in index.html via script tag

**t033:** Add dev reload  
- In dev mode, add: <script>require('electron-reload')(__dirname)</script>  
- Or: use electron-reload package for hot reload  
- Conditional: only in development, not production

**t034:** Test renderer loads  
- Run: npm run dev  
- Verify: window shows loading screen  
- Check: styles applied correctly

**t035:** Add error boundary  
- In app.ts: window.onerror handler  
- Display: friendly error message instead of blank screen  
- Log: errors to main process via IPC

**t036:** Create toast notification system  
- Function: showToast(message, type)  
- Types: success (green), error (red), info (blue)  
- Auto-dismiss: after 3 seconds

**t037:** Add keyboard shortcuts  
- Listen: for Ctrl+R (reload), Ctrl+Q (quit)  
- Listen: F11 (fullscreen), Escape (exit fullscreen)  
- Prevent: default for app-specific shortcuts

**t038:** Implement navigation system  
- Div: id="nav" with menu items  
- Items: Home, Consoles, Settings, About  
- Highlight: active page with CSS class

**t039:** Add responsive layout  
- CSS: media queries for 1920px, 1280px, 720px  
- Mobile: stack navigation vertically  
- Desktop: horizontal nav bar

**t040:** Create component system  
- Pattern: each UI component in separate file  
- Export: class Component with render(), destroy()  
- Mount: components to DOM with getElementById

### Testing Setup (t041-t050)

**t041:** Add test framework  
- Install: jest, @types/jest  
- Create: jest.config.js with testEnvironment node  
- Add: npm script "test": "jest"

**t042:** Create test directory  
- Directory: tests/ with subdirs: unit/, integration/  
- File: tests/unit/main.test.ts  
- Test: basic Electron app initialization

**t043:** Mock Electron APIs  
- Create: tests/mocks/electron.ts  
- Export: mock implementations of app, BrowserWindow, ipcMain  
- Use: in jest.setup.js

**t044:** Write main process tests  
- Test: createWindow creates window with correct dimensions  
- Test: app lifecycle events handled correctly  
- Test: IPC handlers registered

**t045:** Write renderer tests  
- Test: preload script exposes xbplay API  
- Test: IPC calls work correctly  
- Test: DOM manipulation functions

**t046:** Add coverage reporting  
- Configure: jest collectCoverage true  
- Set: coverageThreshold 80% for all metrics  
- Generate: HTML coverage report

**t047:** Create CI config  
- File: .github/workflows/test.yml  
- Jobs: lint, test, build  
- Trigger: on push and pull request

**t048:** Add integration tests  
- Test: full app startup sequence  
- Test: window creation and loading  
- Use: spectron or electron test harness

**t049:** Create test utilities  
- File: tests/utils/helpers.ts  
- Functions: createTestWindow(), cleanupWindows()  
- Mock: common test data

**t050:** Document testing  
- Add: tests/README.md with testing guidelines  
- Explain: how to run tests, write new tests  
- List: testing best practices

---

## PHASE 2: XBOX-XCLOUD-PLAYER INTEGRATION (t051-t100)

### Package Integration (t051-t060)

**t051:** Install xbox-xcloud-player  
- Add: "xbox-xcloud-player": "latest" to dependencies  
- Run: npm install  
- Verify: package in node_modules

**t052:** Create xcloud wrapper  
- File: src/lib/xcloud-wrapper.ts  
- Import: xCloudPlayer, ApiClient from xbox-xcloud-player  
- Export: wrapped versions with our types

**t053:** Add TypeScript types  
- File: src/types/xcloud.d.ts  
- Declare: module 'xbox-xcloud-player'  
- Export: types for Player, ApiClient, Config

**t054:** Create player factory  
- Function: createPlayer(elementId, config): XCloudPlayer  
- Handles: player initialization with defaults  
- Returns: wrapped player instance

**t055:** Create API client factory  
- Function: createApiClient(token): ApiClient  
- Sets: locale, host from config  
- Returns: configured client instance

**t056:** Add error wrapper  
- Function: wrapAsync<T>(fn): Promise<T>  
- Wraps: xcloud-player calls in try/catch  
- Logs: errors with [XCLOUD] prefix

**t057:** Create channel wrappers  
- File: src/lib/channels/base.ts  
- Class: BaseChannel with common channel logic  
- Methods: send(), receive(), close()

**t058:** Wrap input channel  
- File: src/lib/channels/input.ts  
- Extends: BaseChannel  
- Methods: sendButton(), sendAxis(), sendVibration()

**t059:** Wrap control channel  
- File: src/lib/channels/control.ts  
- Methods: powerOn(), powerOff(), launchTitle()  
- Parse: control messages from console

**t060:** Test xcloud integration  
- Test: player factory creates instance  
- Test: API client initializes  
- Test: channels wrap correctly

### Authentication Proxy (t061-t075)

**t061:** Create auth proxy module  
- File: src/main/auth-proxy.ts  
- Import: https, url from Node.js  
- Export: fetchAuthToken(xuid): Promise<string>

**t062:** Implement token fetch  
- Function: makes HTTPS GET to chat.xboxlive.com/users/xuid({xuid})/chat/auth  
- Headers: User-Agent Mozilla/5.0, Accept application/json  
- Returns: parsed AuthKey from JSON response

**t063:** Handle auth errors  
- Catch: network errors, timeout errors  
- Retry: up to 3 times with exponential backoff  
- Throw: descriptive error on final failure

**t064:** Add token caching  
- Cache: tokens in memory with XUID as key  
- TTL: 23 hours (refresh before 24h expiry)  
- Clear: cache on logout

**t065:** Wire to IPC  
- In main/index.ts: update 'xbox-auth-token' handler  
- Call: fetchAuthToken(xuid)  
- Return: token or error

**t066:** Create token store  
- File: src/lib/token-store.ts  
- Use: electron-store for persistence  
- Schema: { tokens: { [xuid: string]: { token, expiry } } }

**t067:** Add electron-store  
- Install: electron-store package  
- Configure: with schema, encryption (optional)  
- Initialize: in token-store.ts

**t068:** Implement save token  
- Method: saveToken(xuid, token, expiresIn)  
- Calculate: expiry timestamp  
- Store: in electron-store

**t069:** Implement get token  
- Method: getToken(xuid): string | null  
- Check: if expired, return null  
- Return: valid token

**t070:** Implement clear tokens  
- Method: clearTokens()  
- Action: store.clear()  
- Also: clear in-memory cache

**t071:** Add refresh token  
- Method: refreshToken(xuid): Promise<string>  
- Check: if token expires in <1 hour  
- Fetch: new token, save, return

**t072:** Create auth manager  
- File: src/lib/auth-manager.ts  
- Combines: auth-proxy and token-store  
- Methods: login(), logout(), getToken(), isAuthenticated()

**t073:** Implement auto-refresh  
- Interval: check tokens every 30 minutes  
- Refresh: tokens expiring in <2 hours  
- Handle: refresh failures gracefully

**t074:** Add auth events  
- EventEmitter: for auth state changes  
- Events: 'login', 'logout', 'token-refreshed', 'auth-error'  
- Listeners: can react to auth changes

**t075:** Test auth flow  
- Test: fetchAuthToken makes correct request  
- Test: token storage persists across restarts  
- Test: auto-refresh works correctly

### Console Discovery (t076-t090)

**t076:** Create console types  
- File: src/types/console.d.ts  
- Interface: Console { id, name, type, powerState, liveId }  
- Enum: ConsoleType { XboxOne, SeriesX, SeriesS }

**t077:** Wrap getConsoles API  
- File: src/lib/api-client.ts  
- Method: async discoverConsoles(): Promise<Console[]>  
- Calls: apiClient.getConsoles()  
- Maps: to our Console interface

**t078:** Add console filtering  
- Method: filterOnlineConsoles(consoles): Console[]  
- Filter: by powerState 'On' or 'Standby'  
- Sort: by name alphabetically

**t079:** Create console cache  
- Cache: discovered consoles for 5 minutes  
- Refresh: on manual trigger or cache expiry  
- Store: in memory (not persisted)

**t080:** Add console icons  
- Download: Xbox One, Series X, Series S icons  
- Save: to assets/consoles/  
- Format: PNG, 256x256, transparent background

**t081:** Create console list UI  
- File: src/renderer/console-list.html  
- Grid: 3 columns on desktop, 1 on mobile  
- Each: console card with icon, name, status

**t082:** Style console cards  
- CSS: .console-card with rounded corners, shadow  
- Hover: scale(1.05) transform  
- Active: different border color

**t083:** Render console list  
- Function: renderConsoles(consoles: Console[])  
- For each: create card DOM element  
- Append: to container div

**t084:** Add console status indicator  
- Dot: green (On), yellow (Standby), red (Off)  
- Position: top-right of card  
- Pulse: animation for connecting state

**t085:** Handle console selection  
- On: card click, emit 'console-selected' event  
- Store: selected console in app state  
- Navigate: to streaming view

**t086:** Add loading state  
- Show: spinner while discovering consoles  
- Text: "Discovering your Xbox consoles..."  
- Hide: when consoles loaded or error

**t087:** Add empty state  
- Show: when no consoles found  
- Message: "No Xbox consoles found on your network"  
- Button: "Refresh" to retry discovery

**t088:** Add error state  
- Show: on discovery failure  
- Message: error details  
- Button: "Retry" to attempt again

**t089:** Implement refresh  
- Button: "Refresh" icon in top-right  
- Action: clear cache, call discoverConsoles()  
- Feedback: show loading state during refresh

**t090:** Test console discovery  
- Mock: API response with test consoles  
- Test: UI renders correctly  
- Test: selection works

### Player Initialization (t091-t100)

**t091:** Create stream manager  
- File: src/renderer/stream-manager.ts  
- Class: StreamManager  
- Methods: init(), connect(), disconnect()

**t092:** Initialize player  
- In init(): create xCloudPlayer instance  
- Pass: video element ID, config  
- Store: player reference

**t093:** Set up event listeners  
- player.onConnectionStateChange: update UI  
- player.ontrack: handle video/audio tracks  
- player.onice candidate: handle ICE

**t094:** Create video element  
- In stream.html: video#gamestream element  
- Style: width 100vw, height 100vh, object-fit contain  
- Attributes: autoplay, playsinline

**t095:** Handle video track  
- On: ontrack event where track.kind === 'video'  
- Attach: stream to video element via srcObject  
- Start: playback

**t096:** Handle audio track  
- On: ontrack event where track.kind === 'audio'  
- Create: Audio element or use AudioContext  
- Route: to default output

**t097:** Add connection status  
- Div: #connection-status in overlay  
- States: Disconnected, Connecting, Connected, Failed  
- Colors: gray, yellow, green, red

**t098:** Update status on events  
- On: connectionstatechange, update status div  
- Show: appropriate icon and text  
- Animate: status changes with fade

**t099:** Add disconnect handler  
- On: connection fails or closes  
- Show: modal "Connection lost" with Reconnect button  
- Clean: up player resources

**t100:** Test player init  
- Test: player creates successfully  
- Test: video element connects  
- Test: events fire correctly

---

## Phase Summaries for Remaining Tasks

**PHASE 3 (t101-t150): Authentication & UI**  
- OAuth flow implementation  
- Login window creation  
- User session management  
- Profile display  
- Settings UI

**PHASE 4 (t151-t200): Streaming & Basic Input**  
- WebRTC connection  
- SDP exchange  
- ICE handling  
- Gamepad API integration  
- Basic input sending

**PHASE 5 (t201-t250): Advanced Input**  
- Raw HID preparation  
- evdev integration (Linux)  
- Button mapping system  
- Analog stick processing  
- Input latency measurement

**PHASE 6 (t251-t300): Native Rendering Prep**  
- MediaStreamTrackProcessor setup  
- Frame extraction logic  
- Canvas rendering  
- Performance profiling

**PHASE 7 (t301-t350): FFmpeg Integration**  
- LGPL-only FFmpeg build  
- Video decoder setup  
- Hardware acceleration  
- Frame format conversion

**PHASE 8 (t351-t400): OpenGL Rendering**  
- OpenGL context creation  
- Texture upload  
- Shader compilation  
- VSync synchronization

**PHASE 9 (t401-t450): Custom Shaders**  
- FSR shader implementation  
- CAS shader implementation  
- Shader parameter tuning  
- Quality comparison

**PHASE 10 (t451-t500): LAN P2P**  
- mDNS discovery  
- Direct ICE candidates  
- UPnP port forwarding  
- Latency optimization

**PHASE 11 (t501-t550): Performance**  
- CPU profiling  
- GPU profiling  
- Memory optimization  
- Frame pacing

**PHASE 12 (t551-t600): Steam Deck**  
- Controller integration  
- Overlay mode  
- Suspend/resume  
- Battery optimization

**PHASE 13 (t601-t650): UI/UX Polish**  
- Spatial navigation  
- Animations  
- Themes  
- Accessibility

**PHASE 14 (t651-t700): Testing**  
- Unit tests  
- Integration tests  
- E2E tests  
- Performance tests

**PHASE 15 (t701-t750): Release**  
- Documentation  
- Build system  
- Installers  
- Distribution

---

## Jules Execution Strategy

### Parallel Execution Pattern
For each critical task, launch 3-4 variants:
```bash
# Task: "Create auth proxy"
jules new "Create src/main/auth-proxy.ts that exports fetchAuthToken function making HTTPS GET to chat.xboxlive.com auth endpoint"

jules new "Implement authentication proxy in src/main/auth-proxy.ts using Node https module to fetch tokens from chat.xboxlive.com"

jules new "Build auth-proxy.ts file in src/main with fetchAuthToken method that retrieves Xbox auth tokens via HTTPS"
```

### Success Criteria
- At least 1 of 3-4 attempts produces working code  
- Pull results with: `jules remote pull --session ID --apply`  
- Test locally before moving to next task  
- Discard failed attempts, keep working one

### Task Dependencies
- Each task explicitly states: "depends on t001-t010 being complete"  
- Never start dependent task until dependency verified  
- Jules can work on independent tasks in parallel

### Error Handling
- If Jules fails 3+ times: task needs simplification  
- Break down into smaller sub-tasks  
- Provide more explicit file paths/function signatures  
- Add concrete examples in task description

---

## Next Steps

1. **Review this plan** - identify any missing phases
2. **Load tasks 1-100 into SQL** for tracking
3. **Start Jules on t001-t005** (repo setup, no dependencies)
4. **Run 3 parallel sessions per task** for redundancy
5. **Pull and test results** after each batch completes

**Ready to start? I can begin loading tasks into SQL and launching Jules sessions.**
