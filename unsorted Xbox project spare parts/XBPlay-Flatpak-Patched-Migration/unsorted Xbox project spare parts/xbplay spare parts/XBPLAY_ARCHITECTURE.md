# XBPlay Custom Client - Architecture Document

**Vision:** Native Xbox streaming client that bypasses browser limitations and unlocks premium features

## Core Advantages Over play.xbox.com

### 1. Network Optimization
- **Direct P2P Connection:** Connect to Xbox's local IP, skip Microsoft STUN/TURN relays
- **Lower Latency:** Eliminate external signaling server hops
- **LAN-First:** Detect Xbox on same network, establish immediate peer connection
- **WAN Fallback:** Port forwarding support for remote connections

### 2. Video Quality Enhancements
- **Resolution Cap Bypass:** Override client capabilities regardless of subscription
- **HEVC Codec:** Force H.265 negotiation for better quality at same bitrate
- **Higher Bitrates:** Request premium encoding profiles
- **Custom Shaders:** FSR upscaling, CAS sharpening on decompressed frames

### 3. Input Latency Reduction
- **Raw HID Access:** Read gamepad at 1000Hz via evdev (Linux) / raw input (Windows)
- **Direct DataChannel:** Bypass Gamepad API, inject into RTCDataChannel
- **Kernel-Level Polling:** Reduce input lag from ~16ms to <1ms

### 4. Rendering Pipeline
- **Hardware Direct:** Extract WebRTC frames, render via OpenGL/Vulkan
- **VSync Perfect:** Synchronize with display refresh, eliminate micro-stutters
- **Zero Compositor:** Bypass browser's rendering pipeline entirely

## Architecture Stack

### Layer 1: Electron Shell
```
┌─────────────────────────────────────┐
│   Main Process (Node.js)            │
│   - CORS-free auth proxy            │
│   - Native module loading            │
│   - System integration               │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Renderer Process (Chromium)       │
│   - Load play.xbox.com              │
│   - Inject custom scripts            │
│   - Intercept WebRTC                 │
└─────────────────────────────────────┘
```

### Layer 2: WebRTC Interception
```javascript
// Intercept before connection
const originalCreatePeerConnection = RTCPeerConnection;
RTCPeerConnection = function(...args) {
  const pc = new originalCreatePeerConnection(...args);
  
  // Hook track events
  pc.addEventListener('track', (event) => {
    const stream = event.streams[0];
    extractAndRenderNatively(stream);
  });
  
  return pc;
};
```

### Layer 3: Native Video Pipeline
```
WebRTC MediaStreamTrack
    ↓
Extract Raw Frames (MediaStreamTrackProcessor)
    ↓
HEVC Hardware Decode (FFmpeg/libavcodec)
    ↓
Upload to GPU (OpenGL texture)
    ↓
Apply Custom Shaders (FSR, CAS)
    ↓
Present to Display (VSync locked)
```

### Layer 4: Input Pipeline
```
evdev/Raw HID (1000Hz polling)
    ↓
Input Buffer (lockless queue)
    ↓
RTCDataChannel (bypass Gamepad API)
    ↓
Xbox receives input
```

## Technology Stack

### Core Framework
- **Electron 28+** - Application shell
- **Node.js** - Main process (auth, native modules)
- **Chromium** - Renderer (WebRTC, initial connection)

### Video Processing
- **FFmpeg** - HEVC decoding, format conversion
- **libavcodec** - Hardware decode acceleration (NVDEC, VAAPI)
- **OpenGL 4.5** - GPU rendering
- **Vulkan** (optional) - Lower-level rendering for advanced features

### Input Handling
- **evdev** (Linux) - Raw input device access
- **libusb** (cross-platform) - Direct USB HID
- **node-hid** - Node.js HID bindings

### Shader Pipeline
- **FidelityFX SDK** - FSR upscaling
- **GLSL** - Custom fragment shaders
- **Reshade** (optional) - Additional post-processing

### Network
- **node-datachannel** - WebRTC native implementation
- **mDNS** - Xbox discovery on LAN
- **UPnP** - Automatic port forwarding

## Project Structure

```
xbplay-client/
├── main/                    # Electron main process
│   ├── auth-proxy.js       # CORS-free auth token fetching
│   ├── xbox-discovery.js   # LAN Xbox detection
│   └── native-loader.js    # Load C++ modules
├── renderer/               # Electron renderer
│   ├── preload.js         # Inject before page load
│   ├── webrtc-hook.js     # Intercept connections
│   └── ui-overlay.js      # Custom UI elements
├── native/                # C++ native modules
│   ├── video-decode/      # FFmpeg HEVC decoder
│   ├── hid-input/         # Raw input reading
│   └── gl-render/         # OpenGL rendering
├── shaders/               # GLSL shaders
│   ├── fsr.frag          # FSR upscaling
│   └── cas.frag          # Contrast sharpening
└── config/
    └── capabilities.json  # Override client caps
```

## Implementation Roadmap

### Phase 1: Foundation (Week 1)
**Goal:** Get basic Electron wrapper working with auth bypass

- [x] Set up Electron project
- [x] Implement Node.js auth proxy (bypass CORS)
- [ ] Load play.xbox.com in renderer
- [ ] Inject auth token override
- [ ] Verify WebRTC connection works

### Phase 2: WebRTC Interception (Week 2)
**Goal:** Extract video stream before browser renders it

- [ ] Hook RTCPeerConnection creation
- [ ] Extract MediaStreamTrack
- [ ] Implement MediaStreamTrackProcessor
- [ ] Get raw video frames
- [ ] Display in canvas (proof of concept)

### Phase 3: Native Rendering (Week 2-3)
**Goal:** Render with OpenGL, bypass browser compositor

- [ ] Build FFmpeg integration
- [ ] Decode frames with hardware acceleration
- [ ] Create OpenGL context
- [ ] Render decoded frames
- [ ] Implement VSync synchronization

### Phase 4: Input Pipeline (Week 3)
**Goal:** Low-latency input via raw HID

- [ ] Build evdev reader (Linux)
- [ ] Map gamepad events to Xbox protocol
- [ ] Inject into RTCDataChannel
- [ ] Benchmark latency improvement

### Phase 5: Video Enhancements (Week 4)
**Goal:** HEVC, higher bitrates, custom shaders

- [ ] Negotiate HEVC codec
- [ ] Override bitrate requests
- [ ] Implement FSR shader
- [ ] Implement CAS shader
- [ ] Quality comparison testing

### Phase 6: Direct P2P (Week 5)
**Goal:** LAN-direct connections, skip MS relays

- [ ] Xbox mDNS discovery
- [ ] Custom signaling protocol
- [ ] Direct ICE candidate injection
- [ ] UPnP port forwarding
- [ ] Latency benchmarking

### Phase 7: Polish & Distribution (Week 6)
**Goal:** Package for end users

- [ ] Settings UI
- [ ] Performance metrics overlay
- [ ] Package for Linux (AppImage/Flatpak)
- [ ] Documentation
- [ ] Release v1.0

## Technical Challenges

### Challenge 1: WebRTC Frame Extraction
**Problem:** Browser tightly controls MediaStream rendering  
**Solution:** Use `MediaStreamTrackProcessor` (experimental) or inject native module to hook video decoder

### Challenge 2: HEVC Negotiation
**Problem:** Browser may refuse HEVC codec  
**Solution:** Modify SDP offer/answer to force H.265, ensure hardware decode available

### Challenge 3: Direct P2P Without STUN
**Problem:** NAT traversal typically needs STUN server  
**Solution:** mDNS for LAN, UPnP for WAN, manual port forward as fallback

### Challenge 4: Input Protocol Reverse Engineering
**Problem:** Xbox expects specific RTCDataChannel message format  
**Solution:** Capture and analyze existing play.xbox.com traffic, replicate protocol

### Challenge 5: Bitrate Cap Detection
**Problem:** Microsoft may enforce server-side bitrate limits  
**Solution:** Test with modified client capabilities, may need to spoof subscription status

## Performance Targets

| Metric | Browser (play.xbox.com) | XBPlay Target |
|--------|-------------------------|---------------|
| Input Latency | ~16ms (Gamepad API) | <1ms (raw HID) |
| Video Latency | ~100-150ms | <50ms (LAN P2P) |
| Frame Pacing | Varies (compositor) | Perfect (VSync) |
| Video Quality | H.264 Standard | H.265 Premium |
| Resolution | 1080p (capped) | 4K (if hw supports) |
| Bitrate | 15-20 Mbps | 40+ Mbps |

## Development Environment

### Requirements
- Node.js 18+
- Electron 28+
- GCC/Clang (for native modules)
- FFmpeg development libraries
- OpenGL 4.5 capable GPU
- Linux with evdev support (for HID)

### Quick Start
```bash
# Clone repo
git clone https://github.com/xbplay/client
cd xbplay-client

# Install dependencies
npm install

# Build native modules
npm run build:native

# Run development build
npm run dev

# Package for distribution
npm run package
```

## Legal Considerations

**Important:** This client:
- ✅ Uses Microsoft's official WebRTC protocol
- ✅ Requires legitimate Xbox account
- ⚠️ May violate Terms of Service by spoofing capabilities
- ⚠️ Bypassing subscription checks could be considered piracy

**Recommendation:** 
- Use for personal optimization (latency, quality) only
- Don't distribute tools to bypass payment walls
- Consider open-sourcing to demonstrate technical merit

## Next Steps

1. **Immediate:** Build Electron base with auth proxy
2. **This Week:** Intercept WebRTC and extract frames
3. **Next Week:** Native rendering with OpenGL
4. **Future:** Full native pipeline with all optimizations

---

**Ready to start building?** I can generate the initial Electron project structure and auth proxy implementation right now.
