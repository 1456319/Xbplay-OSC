# Xbox Remote Play Video and Audio Codec Specification

**Document Version:** 1.0  
**Date:** 2026-03-29  
**Team:** Team 1 (Dirty Room)  
**Status:** Complete - Ready for Team 2 Implementation

---

## Executive Summary

Xbox Remote Play uses H.264 video and Opus audio codecs for streaming gameplay from console to client. Both codecs are industry-standard, with specific configuration for low-latency gaming.

**Key characteristics:**
- **Video:** H.264 Baseline Profile, 720p-1080p, 60fps, 1-10 Mbps
- **Audio:** Opus, 48kHz stereo, 64-128 kbps
- **Latency target:** < 100ms glass-to-glass
- **Transport:** RTP over UDP via WebRTC

---

## Video Codec: H.264

### Overview

**Codec:** H.264/AVC (Advanced Video Coding)  
**Standard:** ITU-T H.264 / ISO/IEC 14496-10  
**Profile:** Baseline Profile  
**Level:** 3.1 (minimum), 4.0 (typical for 1080p)

### Why H.264 Baseline?

**Baseline Profile** is chosen for:
- **Low complexity:** Fast encoding/decoding (important for 60fps)
- **Low latency:** No B-frames (bi-predictive frames)
- **Hardware support:** Universally supported by GPUs
- **Error resilience:** Simpler structure = better recovery from packet loss

**Trade-off:** Lower compression efficiency vs Main/High profiles (~10-20% higher bitrate for same quality)

### SDP Configuration

**RTP Payload Type:** 96 (H.264), 97 (RTX for retransmission)

**SDP Attributes:**
```
m=video 9 UDP/TLS/RTP/SAVPF 96 97 98
a=rtpmap:96 H264/90000
a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f
a=rtcp-fb:96 goog-remb
a=rtcp-fb:96 transport-cc
a=rtcp-fb:96 ccm fir
a=rtcp-fb:96 nack
a=rtcp-fb:96 nack pli
a=rtpmap:97 rtx/90000
a=fmtp:97 apt=96
```

### Key Parameters

#### profile-level-id: 42001f

Decoded as:
```
42 = Baseline Profile (0x42)
00 = Constraint flags (no constraints)
1f = Level 3.1 (0x1f = 31 = 3.1)
```

**Level 3.1 capabilities:**
- Max resolution: 1280×720 @ 60fps (or 1920×1080 @ 30fps)
- Max bitrate: 14 Mbps
- Max macroblocks: 40,500 per second

**For 1080p @ 60fps:** Level 4.0 (42001f → 420028)

#### packetization-mode: 1

**Mode 1:** Non-interleaved mode
- NAL units (Network Abstraction Layer) sent in order
- May use fragmentation for large frames (FU-A)
- Lower latency than mode 0 (single NAL unit per packet)

**Alternative:** Mode 0 (single NAL unit mode) - simpler but less efficient

#### level-asymmetry-allowed: 1

Allows client and Xbox to use different H.264 levels:
- Xbox encoder: Level 4.0 (1080p60)
- Client decoder: Level 3.1 (720p60)

Enables downscaling if client hardware is limited.

### RTCP Feedback Mechanisms

#### goog-remb (Receiver Estimated Maximum Bitrate)

Client sends REMB messages to Xbox indicating available bandwidth:
```
Current bandwidth estimate: 8 Mbps
→ Send REMB to Xbox
→ Xbox adjusts encoder bitrate to ≤ 8 Mbps
```

#### transport-cc (Transport-wide Congestion Control)

Extension for better congestion control:
- Client reports packet arrival times
- Xbox calculates packet loss, jitter, bandwidth
- Xbox adjusts bitrate dynamically

#### ccm fir (Full Intra Request)

Client can request a full intra frame (I-frame) when:
- Decoder errors occur
- Packet loss causes artifacts
- Stream quality degrades

**Message:** `RTCP FIR` or via control DataChannel

#### nack / nack pli (Negative Acknowledgment)

Client reports lost packets:
- **NACK:** Request specific packet retransmission
- **PLI (Picture Loss Indication):** Request keyframe if loss is severe

### Video Parameters

#### Resolution

**Supported resolutions:**
- 1920×1080 (1080p) - Requires good bandwidth (> 8 Mbps)
- 1280×720 (720p) - Default, best latency/quality balance
- 854×480 (480p) - Fallback for low bandwidth

**Negotiation:**
- Client requests preferred resolution in session creation
- Xbox may override based on console performance
- Can change mid-session via message DataChannel

#### Framerate

**Target:** 60 fps  
**Minimum:** 30 fps (fallback for low bandwidth)

**Frame interval:**
- 60fps: 16.67ms per frame
- 30fps: 33.33ms per frame

#### Bitrate

**Range:** 1,000 - 10,000 kbps (1-10 Mbps)  
**Default:** 5,000 kbps (5 Mbps)

**Bitrate ladder:**
```
 480p @ 30fps:  1-2 Mbps
 720p @ 30fps:  2-4 Mbps
 720p @ 60fps:  4-6 Mbps
1080p @ 30fps:  4-6 Mbps
1080p @ 60fps:  8-10 Mbps
```

**Adaptive bitrate:** Adjusts based on network conditions (via REMB, transport-cc)

#### Group of Pictures (GOP)

**GOP structure (Baseline Profile):**
```
I P P P P P P P P P P P P P P P ...
^                               ^
Keyframe (intra)                Next keyframe
```

**GOP size:** ~60-120 frames (1-2 seconds @ 60fps)
- **Smaller GOP:** More keyframes, better error recovery, higher bitrate
- **Larger GOP:** Fewer keyframes, lower bitrate, worse error recovery

**For gaming:** Prefer smaller GOP (60 frames = 1 second) for faster recovery from packet loss

### Video Encoding Settings (Xbox Side)

**Encoder:** Hardware encoder (Xbox One/Series uses AMD VCE, Nvidia NVENC equivalent)

**Encoding parameters (typical):**
```
Profile:    Baseline
Level:      4.0 (1080p60) or 3.1 (720p60)
Bitrate:    5-8 Mbps (CBR or VBR)
GOP:        60 frames (1 second @ 60fps)
B-frames:   0 (Baseline Profile doesn't support B-frames)
Entropy:    CAVLC (Baseline) not CABAC (Main/High)
Latency:    Low-latency mode (single-pass, no lookahead)
```

**Rate control:**
- **CBR (Constant Bitrate):** Stable bitrate, may sacrifice quality
- **VBR (Variable Bitrate):** Better quality, variable bitrate
- Recommended: **CBR** for predictable network usage

### Video Decoding (Client Side)

#### Hardware Decoding

**Recommended:** Use hardware decoder for lowest CPU usage and latency

**Linux:**
- **VAAPI:** VA-API (Video Acceleration API)
  - Intel: `i965-va-driver` or `intel-media-driver`
  - AMD: `mesa-va-drivers`
  - Check: `vainfo` command
- **VDPAU:** Nvidia proprietary
- **V4L2:** Generic kernel interface

**Windows:**
- **DXVA2:** DirectX Video Acceleration
- **D3D11:** Direct3D 11 video decoding

**macOS:**
- **VideoToolbox:** Apple's hardware decoder

**Implementation (WebRTC/Electron):**
```javascript
const pc = new RTCPeerConnection({
  // Force hardware decoding
  encodedInsertableStreams: false,
  // Let browser choose best decoder
});
```

For native implementation, use platform-specific APIs (VAAPI, DXVA2, VideoToolbox).

#### Software Decoding

**Fallback:** If hardware decoder unavailable

**Libraries:**
- **FFmpeg/libavcodec:** Universal, cross-platform
- **OpenH264:** Cisco's open-source decoder (Baseline only)
- **libx264:** Encoding only, not decoding

**Performance:**
- Software decoding 720p60: ~30-50% CPU (1 core)
- Software decoding 1080p60: ~60-100% CPU (1 core)
- Hardware decoding: < 5% CPU

**Recommendation:** Require hardware decoder for 60fps, allow software fallback for 30fps

### Video Quality Optimization

#### Sharpness / Deblocking

H.264 Baseline uses in-loop deblocking filter:
- Reduces blocking artifacts (visible 8×8 macroblocks)
- Slight blur effect
- Cannot be disabled in Baseline Profile

**Client-side enhancement:**
- Apply sharpening filter post-decode (OpenGL/Vulkan shader)
- Use FSR (FidelityFX Super Resolution) for upscaling

#### Color Space

**YUV 4:2:0:** Standard for H.264
- Y (luma): Full resolution
- U/V (chroma): Half resolution (subsampled)

**Color range:**
- **Limited range (16-235):** Typical for video
- **Full range (0-255):** Better for games

Check SDP for `a=range:full` or `a=range:limited`

#### HDR Support

**Baseline Profile:** No HDR (8-bit only)
- Xbox Remote Play uses SDR (Standard Dynamic Range)
- HDR requires Main 10 Profile (10-bit)

---

## Audio Codec: Opus

### Overview

**Codec:** Opus  
**Standard:** RFC 6716 / IETF Opus  
**Sample rate:** 48 kHz  
**Channels:** 2 (stereo)  
**Bitrate:** 64-128 kbps

### Why Opus?

**Opus** is chosen for:
- **Low latency:** 5-20ms algorithmic delay (configurable)
- **High quality:** Excellent speech & music quality
- **Error resilience:** FEC (Forward Error Correction)
- **Bandwidth efficiency:** Low bitrate for high quality
- **Patent-free:** Open standard, no licensing

### SDP Configuration

**RTP Payload Type:** 111

**SDP Attributes:**
```
m=audio 9 UDP/TLS/RTP/SAVPF 111 110
a=rtpmap:111 opus/48000/2
a=fmtp:111 minptime=10;useinbandfec=1
a=rtcp-fb:111 transport-cc
```

### Key Parameters

#### Sample Rate: 48000 Hz

**48 kHz** is optimal for:
- Full frequency range (up to 24 kHz, beyond human hearing)
- Compatible with Xbox audio output
- Standard for Opus codec

**Alternative:** 16 kHz (narrowband, for voice chat only)

#### Channels: 2 (stereo)

**Stereo audio** for:
- Spatial awareness in games
- Music/sound effects

**Mono (1 channel):** Not used (stereo is standard)

#### minptime: 10

**Minimum packet time:** 10ms

**Opus frame sizes:**
- 10ms: Low latency, higher overhead
- 20ms: Balanced (most common)
- 40ms, 60ms: Lower overhead, higher latency

**For gaming:** 10ms or 20ms (prefer 10ms for lowest latency)

#### useinbandfec: 1

**FEC (Forward Error Correction):** Enabled

**How FEC works:**
- Encoder includes redundant data in next packet
- If packet N is lost, packet N+1 can partially reconstruct packet N
- ~5% bitrate overhead, significantly better error recovery

**Without FEC:** Lost packets = audio gaps/glitches  
**With FEC:** Lost packets = slight quality degradation, no gaps

### Audio Parameters

#### Bitrate

**Range:** 64-128 kbps for stereo  
**Typical:** 96 kbps

**Quality ladder:**
```
64 kbps:  Good quality (voice)
96 kbps:  Very good quality (gaming default)
128 kbps: Excellent quality (music/effects)
```

**Adaptive bitrate:** Can adjust based on bandwidth (lower priority than video)

#### Latency

**Algorithmic delay:**
- Encoding: 5-10ms
- Decoding: 2-5ms
- Total: ~10-15ms codec latency

**Network delay:**
- Jitter buffer: 20-50ms (configurable)
- RTP transmission: RTT/2 (~3-10ms on LAN)
- Total: ~30-70ms audio latency

**Target:** < 100ms total (codec + network + buffering)

#### Packet Loss Concealment (PLC)

**Opus decoder includes PLC:**
- If packet lost and FEC unavailable: Synthesize missing audio
- Uses previous audio to extrapolate
- Works for up to ~100ms gaps

**PLC quality:**
- Single packet loss: Imperceptible
- Multiple consecutive losses: Slight degradation
- > 5% packet loss: Noticeable artifacts

### Audio Encoding Settings (Xbox Side)

**Encoder:** libopus (standard reference implementation)

**Encoding parameters:**
```
Sample rate:      48000 Hz
Channels:         2 (stereo)
Bitrate:          96 kbps
Frame size:       20ms (960 samples @ 48kHz)
Application:      OPUS_APPLICATION_AUDIO (not VOIP or RESTRICTED_LOWDELAY)
Complexity:       5-10 (lower = faster, higher = better quality)
FEC:              Enabled (inband FEC)
DTX:              Disabled (Discontinuous Transmission, not useful for games)
```

**Application mode:**
- `AUDIO`: Optimized for music/effects (gaming default)
- `VOIP`: Optimized for speech (voice chat)
- `RESTRICTED_LOWDELAY`: Lowest latency (for < 10ms requirement)

### Audio Decoding (Client Side)

#### Decoder Implementation

**Libraries:**
- **libopus:** Official reference decoder (C)
- **opus-tools:** Command-line tools
- **Browser native:** Chrome/Firefox have built-in Opus support

**WebRTC (JavaScript):**
```javascript
// Browser handles Opus decoding automatically
remoteStream.getAudioTracks()[0]; // Already decoded
```

**Native (C/C++):**
```c
#include <opus/opus.h>

OpusDecoder *decoder = opus_decoder_create(48000, 2, &error);
opus_decode(decoder, packet, len, pcm_out, frame_size, 0);
```

#### Audio Output

**Linux:**
- **PulseAudio:** Most common
- **ALSA:** Direct hardware access
- **PipeWire:** Modern audio server (Fedora, Arch)

**Windows:**
- **WASAPI:** Windows Audio Session API (lowest latency)
- **DirectSound:** Legacy API

**macOS:**
- **CoreAudio:** Apple's audio framework

**Latency considerations:**
- Use exclusive mode (WASAPI) or low-latency PulseAudio
- Buffer size: 10-20ms (smaller = lower latency, higher CPU)

### Audio Quality Optimization

#### Jitter Buffer

**Purpose:** Smooth out network jitter (variable packet arrival times)

**Size:**
- Small (20ms): Low latency, more packet loss impact
- Large (100ms): High latency, better quality
- Recommended: 30-50ms (balance)

**Adaptive jitter buffer:** Adjusts size based on network conditions

#### Volume Normalization

**Game audio** can have wide dynamic range (quiet footsteps → loud explosions)

**Options:**
- **Loudness normalization:** EBU R128, ReplayGain
- **Dynamic range compression:** Reduce volume spikes
- **AGC (Automatic Gain Control):** Opus has built-in AGC (disable for gaming)

**Recommendation:** No normalization (preserve original game audio dynamics)

#### Audio Sync (A/V Sync)

**Challenge:** Keep audio in sync with video

**Synchronization:**
- Use RTP timestamps (both audio and video)
- Render audio at same timestamp as video frame
- Typical tolerance: ±50ms (imperceptible)

**If audio leads video:** Delay audio playback  
**If video leads audio:** Drop old audio packets (or speed up playback slightly)

---

## RTP Transport

### RTP (Real-time Transport Protocol)

**Standard:** RFC 3550

**RTP Header:**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       Sequence Number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           Timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Synchronization Source (SSRC) identifier            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**Key fields:**
- **PT (Payload Type):** 96 (video), 111 (audio)
- **Sequence Number:** Detect packet loss, reordering
- **Timestamp:** Synchronization (90kHz for video, 48kHz for audio)
- **SSRC:** Stream identifier

### RTCP (RTP Control Protocol)

**Purpose:** Feedback and statistics

**RTCP Packet Types:**
- **SR (Sender Report):** Xbox sends stats (packets sent, bytes, timestamp)
- **RR (Receiver Report):** Client sends stats (packets lost, jitter)
- **SDES (Source Description):** SSRC metadata
- **BYE:** Stream termination
- **APP:** Application-specific (e.g., REMB)

**RTCP Interval:** ~5 seconds (low bandwidth overhead)

### SRTP (Secure RTP)

**Encryption:** All media encrypted via SRTP

**Key exchange:** DTLS-SRTP
- DTLS handshake (same as DataChannels)
- Derive SRTP keys from DTLS master secret
- Separate keys for send/receive, video/audio

**Encryption algorithm:**
- **Cipher:** AES-128-GCM (default) or AES-128-CM with HMAC-SHA1-80
- **Authentication:** Prevents packet tampering

**No plaintext transmission of media**

---

## Bandwidth Budget

### Total Bandwidth Requirements

**Typical session (720p60):**
```
Video:        5,000 kbps (5 Mbps)
Audio:           96 kbps
Input:           20 kbps (43 bytes × 60 Hz)
Control:          1 kbps
Message:          5 kbps
RTP/RTCP:       100 kbps (overhead ~2%)
------------------------------------------
Total:        5,222 kbps (~5.2 Mbps)
```

**High quality (1080p60):**
```
Video:       10,000 kbps (10 Mbps)
Audio:          128 kbps
Other:          126 kbps
------------------------------------------
Total:       10,254 kbps (~10.3 Mbps)
```

**Low quality (480p30):**
```
Video:        1,500 kbps (1.5 Mbps)
Audio:           64 kbps
Other:           26 kbps
------------------------------------------
Total:        1,590 kbps (~1.6 Mbps)
```

### Network Requirements

**Minimum:** 2 Mbps (480p30)  
**Recommended:** 6 Mbps (720p60)  
**Optimal:** 15 Mbps (1080p60 with headroom)

**Latency:** < 30ms RTT (preferably < 10ms on LAN)  
**Packet loss:** < 1% (FEC can handle up to ~5%)

---

## Implementation Checklist for Team 2

### Phase 1: Video Setup

- [ ] Configure RTCPeerConnection with H.264 codec
- [ ] Set SDP attributes (profile-level-id, packetization-mode)
- [ ] Request preferred resolution (720p or 1080p)
- [ ] Enable hardware decoding (VAAPI/DXVA2/VideoToolbox)

### Phase 2: Audio Setup

- [ ] Configure Opus codec (48kHz, stereo)
- [ ] Enable FEC (useinbandfec=1)
- [ ] Set minptime (10ms or 20ms)
- [ ] Route audio to system audio output

### Phase 3: Quality Control

- [ ] Implement REMB (bandwidth estimation)
- [ ] Handle FIR (keyframe requests)
- [ ] Monitor packet loss (RTCP reports)
- [ ] Adjust bitrate dynamically

### Phase 4: A/V Sync

- [ ] Extract RTP timestamps
- [ ] Synchronize audio/video rendering
- [ ] Handle clock drift (NTP sync)
- [ ] Test A/V sync (< 50ms tolerance)

### Phase 5: Optimization

- [ ] Tune jitter buffer size (30-50ms)
- [ ] Implement adaptive bitrate
- [ ] Add post-processing (sharpening, upscaling)
- [ ] Profile CPU/GPU usage

---

## Testing Strategy

### Unit Tests

- SDP parsing (extract codec parameters)
- RTP header parsing
- Bitrate calculation
- Timestamp conversion

### Integration Tests

- Video decoding (test H.264 stream)
- Audio decoding (test Opus stream)
- Hardware decoder fallback
- A/V sync verification

### End-to-End Tests

- Stream from Xbox, verify quality
- Test all resolutions (480p, 720p, 1080p)
- Test packet loss (simulate 1%, 5%, 10%)
- Test bandwidth throttling (verify adaptive bitrate)
- Test A/V sync under load

### Quality Assessment

- **Video:** PSNR, SSIM (compare to source)
- **Audio:** PESQ, POLQA (perceptual quality)
- **Latency:** Glass-to-glass measurement (< 100ms target)

---

## Comparison with Open Source

**Reference:** `xbox-xcloud-player` (MIT Licensed)

**Expected similarities:**
- H.264 + Opus codecs
- Similar bitrate ranges
- FEC enabled
- Low-latency configuration

**Potential differences:**
- Profile/level (may use Main Profile instead of Baseline)
- Bitrate ladder (different resolution/quality tiers)
- FEC settings

**Team 2 action:** Compare codec configuration with open-source implementation, document differences.

---

## Document Metadata

**Source:** 79 MB HAR capture + rtcstats dump + WebRTC internals  
**Capture date:** 2026-03-28  
**Analysis method:** SDP parsing, RTP analysis, codec parameter extraction  
**Cleanroom compliance:** ✅ Yes - No Microsoft code copied, only protocol specifications documented

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-29 | Initial specification from capture analysis |

---

**Status:** Ready for Team 2 implementation.

