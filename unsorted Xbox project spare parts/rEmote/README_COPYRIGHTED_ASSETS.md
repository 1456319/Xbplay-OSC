# Copyrighted Assets Archive

**File:** COPYRIGHTED_ASSETS.7z (80 MB)  
**Password:** EMERGENCY  
**Created:** 2026-03-29

## ⚠️ WARNING: COPYRIGHTED MATERIALS

This archive contains copyrighted materials that **MUST NOT** be used in the cleanroom implementation:

### Contents

1. **Xbplay-OSC-repo** (Studio08 source code)
   - Contains Microsoft's stolen/reverse-engineered code
   - UI assets and layouts
   - Build configurations
   - **DO NOT use protocol implementation code**

2. **XBPlay-Flatpak-Patched** (Binary flatpak)
   - Flatpak packaging structure
   - Desktop integration files
   - Application metadata

## What You CAN Use

✅ **Safe to extract and use:**
- Icon files (.png, .svg, .ico)
- Image assets
- Desktop entry files (.desktop)
- AppStream metadata (.xml)
- Build script structures (package.json, webpack configs)
- Flatpak manifest structure (as reference)
- UI layout concepts (visual design, not code)

❌ **DO NOT use:**
- Any JavaScript/TypeScript protocol implementation
- Authentication logic
- WebRTC setup code
- Input handling code
- Session management code
- Anything that communicates with Xbox/Microsoft servers

## Cleanroom Compliance

**Remember:** You are Team 2 (Clean Room)

- ✅ Implement from specifications in `docs/specs/`
- ✅ Extract visual assets (icons, images)
- ✅ Reference build configurations
- ❌ Do NOT look at protocol implementation code
- ❌ Do NOT copy authentication/WebRTC/input code
- ❌ Do NOT run or analyze the binary executable

**If you violate cleanroom methodology, the entire project becomes legally tainted.**

## Extraction (If Needed)

```bash
# Extract archive
7z x COPYRIGHTED_ASSETS.7z
# Password: EMERGENCY

# Extract only safe assets (example)
7z e COPYRIGHTED_ASSETS.7z "*.png" "*.svg" "*.desktop" -o./assets/
```

## Why This Archive Exists

This archive preserves Studio08's work for:
1. **Asset extraction** - Icons, images, desktop files
2. **UI reference** - Visual design inspiration
3. **Build reference** - Electron/Flatpak packaging structure
4. **Historical reference** - What the original project looked like

**All protocol implementation MUST come from `docs/specs/` cleanroom documentation.**

## Legal Notes

- Studio08's code contains Microsoft's intellectual property
- Using their protocol implementation would violate cleanroom methodology
- Visual assets and build configurations are generally safe to reuse
- When in doubt, implement from specs, not this archive

---

**Status:** For reference only. Protocol implementation from specs.  
**Extraction:** Only if you need specific assets (icons, images).  
**Default:** Work entirely from `docs/specs/` without opening this archive.
