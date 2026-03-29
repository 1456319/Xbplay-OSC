# XBPlay Client - Master Documentation Index

**Project:** Custom Xbox streaming client with native optimizations  
**Status:** Planning complete, ready for implementation  
**Agent:** Jules (Google async coding agent)  
**Timeline:** 8 weeks to v1.0

---

## 📋 Planning Documents (READ THESE FIRST)

### 1. PROJECT_STATUS_READY_TO_START.md
**Purpose:** Executive summary with immediate next steps  
**Start here if:** You want to know what to do RIGHT NOW  
**Key info:** 
- 2 tasks ready to launch immediately (t001, t002)
- Jules commands to start
- Quality gate checklist

### 2. JULES_TASK_BREAKDOWN.md (21 KB)
**Purpose:** Complete task list (500+ tasks) for entire project  
**Start here if:** You want to see the full scope  
**Key info:**
- Tasks t001-t100 fully detailed
- Tasks t101-t750 summarized by phase
- Each task is atomic, no ambiguity

### 3. JULES_EXECUTION_GUIDE.md (8 KB)
**Purpose:** How to use Jules effectively  
**Start here if:** You've never used Jules before  
**Key info:**
- Jules capabilities & constraints
- Command reference (new, list, pull, teleport)
- Error handling strategies
- Parallel execution patterns

---

## 🏗️ Architecture Documents

### 4. XBPLAY_ARCHITECTURE.md (9 KB)
**Purpose:** Technical architecture for the custom client  
**Key info:**
- Layer breakdown (Electron → WebRTC → Native → Input)
- Performance targets (input <1ms, video <50ms)
- Tech stack decisions
- Phase-by-phase implementation

### 5. CODEBASE_VIABILITY_ANALYSIS.md (12 KB)
**Purpose:** Legal analysis of existing code (Studio08, xbox-xcloud-player)  
**Key info:**
- Studio08 has GPL violations → cannot use as-is
- xbox-xcloud-player is clean MIT → safe foundation
- Cleanroom process to avoid copyright issues
- Comparison matrix of available code

---

## 🐛 Debugging Documents (Historical)

### 6. BREAKTHROUGH_CORS_ANALYSIS.md
**Purpose:** Root cause analysis of splash screen bug  
**Key findings:**
- CORS blocks auth token request
- Empty AuthKey → WebSocket reject → infinite retry loop
- Service worker adds 3x retries = CPU burn

### 7. SERVICE_WORKER_ANALYSIS.md
**Purpose:** Deep dive into play.xbox.com service worker  
**Key findings:**
- FETCH_RETRIES = 3 with exponential backoff
- maxRetries: Infinity in reconnecting-websocket
- SW_FALLBACK logging on every failure

### 8. ROUND3_ANALYSIS.md
**Purpose:** Complete troubleshooting report (Round 3 debugging)  
**Contains:** Process diagnostics, strace logs, HAR analysis

### 9. PRODUCTION_SOLUTIONS.md
**Purpose:** 5 ways to fix CORS in production  
**Solutions:** Electron wrapper (chosen), reverse proxy, browser flags, direct console streaming, native app

---

## 📁 Source Code Directories

### round3_sources/ (345 JavaScript files)
**Contents:** Extracted source from play.xbox.com HAR archives  
**Use for:** Reference only, understanding Microsoft's implementation  
**DO NOT:** Copy code directly (copyright issues)

### round3_diagnostics/
**Contents:** strace logs, process traces, diagnostic JSON  
**Use for:** Understanding failure patterns

---

## 📊 Tracking & Progress

### SQL Database (Session)
**Tables:**
- `todos` - 100 tasks loaded (id, title, description, status)
- `todo_deps` - 125 dependencies (todo_id, depends_on)

**Key Queries:**
```sql
-- Find ready tasks
SELECT t.id, t.title FROM todos t
WHERE t.status = 'pending'
AND NOT EXISTS (
    SELECT 1 FROM todo_deps td
    JOIN todos dep ON td.depends_on = dep.id
    WHERE td.todo_id = t.id AND dep.status != 'done'
);

-- Mark task done
UPDATE todos SET status = 'done' WHERE id = 't001';

-- Check progress
SELECT status, COUNT(*) FROM todos GROUP BY status;
```

---

## 🎯 Quick Start Guide

### If This Is Your First Time Reading This

1. **Read:** PROJECT_STATUS_READY_TO_START.md (5 min)
2. **Decide:** Do we proceed with full build? Or minimal wrapper?
3. **If YES:** Run the two Jules commands in "Ready to Launch?" section
4. **Monitor:** Jules sessions with `watch` command
5. **Pull & test:** After 10-15 minutes when sessions complete

### If You're Continuing After Break

1. **Check SQL:** What tasks are done? What's ready?
   ```sql
   SELECT id, title, status FROM todos WHERE status != 'pending' ORDER BY id;
   ```
2. **Check Jules:** Any completed sessions to pull?
   ```bash
   jules remote list --session | grep Completed | grep xbplay
   ```
3. **Resume:** Launch next batch of ready tasks

### If You're Debugging Jules Failures

1. **Read:** JULES_EXECUTION_GUIDE.md error handling section
2. **Check:** Are 3+ parallel sessions failing same task?
3. **Action:** Break task into smaller pieces, add more context
4. **Fallback:** Do task manually if Jules can't handle it

---

## 🚀 Project Phases

### Phase 1: Foundation (Tasks 1-50) - Week 1-2
- Electron shell
- Basic renderer
- Testing framework
- **Goal:** App launches and shows UI

### Phase 2: Integration (Tasks 51-100) - Week 2-3
- xbox-xcloud-player integration
- Auth proxy (CORS bypass)
- Console discovery
- Video streaming
- **Goal:** Connect to Xbox and stream video

### Phase 3: Advanced Features (Tasks 101-250) - Week 3-6
- Raw HID input (evdev)
- Native rendering (OpenGL)
- HEVC codec
- LAN P2P
- **Goal:** Native performance, low latency

### Phase 4: Polish (Tasks 251-500+) - Week 6-8
- Steam Deck integration
- Custom shaders (FSR, CAS)
- UI/UX improvements
- Testing & bug fixes
- **Goal:** Production-ready v1.0

---

## 📞 Communication

### Status Updates (Recommended Frequency)
- **Daily:** Check Jules sessions, pull completed work
- **Every 5-10 tasks:** Run tests, commit batch
- **Weekly:** Review progress vs plan, adjust timeline

### When to Ask for Help
- ❌ Jules failing 3+ times on same task
- ❌ Tests breaking after Jules changes
- ❌ Unclear what task to launch next
- ❌ Legal concerns about code reuse

### What to Tell Me
- "Continue with next batch" → I'll query SQL and launch tasks
- "Jules session X failed" → I'll help debug or break into sub-tasks
- "Change of plans" → I'll adjust task list
- "Show progress" → I'll query SQL and generate report

---

## 🎮 Why We're Building This

### Problem
play.xbox.com has:
- CORS blocking auth (splash screen bug)
- Browser overhead (lag, stuttering)
- Resolution caps without Ultimate subscription

### Solution
Custom client with:
- ✅ No CORS (auth proxy in main process)
- ✅ Native rendering (OpenGL/Vulkan, no browser compositor)
- ✅ Raw HID input (evdev, sub-1ms latency)
- ✅ LAN P2P (direct to console, skip MS relays)
- ✅ HEVC codec (better quality, same bandwidth)
- ✅ Custom shaders (FSR upscaling, CAS sharpening)

### Legal Strategy
- Use xbox-xcloud-player as foundation (MIT licensed)
- Reference Studio08 architecture but NEVER copy code
- Cleanroom reverse-engineer Microsoft protocols
- Document everything for legal defensibility

---

## 📝 File Organization

```
/home/deck/dump/xbplay spare parts/
├── 📋 Planning
│   ├── PROJECT_STATUS_READY_TO_START.md ← START HERE
│   ├── JULES_TASK_BREAKDOWN.md
│   ├── JULES_EXECUTION_GUIDE.md
│   └── MASTER_INDEX.md (this file)
│
├── 🏗️ Architecture
│   ├── XBPLAY_ARCHITECTURE.md
│   └── CODEBASE_VIABILITY_ANALYSIS.md
│
├── 🐛 Debugging (Historical)
│   ├── BREAKTHROUGH_CORS_ANALYSIS.md
│   ├── SERVICE_WORKER_ANALYSIS.md
│   ├── ROUND3_ANALYSIS.md
│   ├── PRODUCTION_SOLUTIONS.md
│   └── ROUND3_QUICK_REFERENCE.md
│
├── 📁 Source Code
│   ├── round3_sources/ (345 .js files)
│   └── round3_diagnostics/
│
└── 📜 Logs
    ├── round3_extraction.log
    └── round3_troubleshoot.log
```

---

## ⚡ TL;DR (Too Long; Didn't Read)

**What:** Building custom Xbox streaming client to fix CORS bug and add native features  
**How:** Using Jules (async coding agent) to execute 500+ atomic tasks  
**When:** 8 weeks to v1.0  
**Status:** Ready to start - 2 tasks queued (repo setup)  
**Next:** Run Jules commands in PROJECT_STATUS_READY_TO_START.md

**One command to start everything:**
```bash
jules new --parallel 3 "Fork unknownskl/xbox-xcloud-player to 1456319 org" && \
jules new --parallel 3 "Create repo 1456319/xbplay-client with MIT license"
```

---

## 🎯 Success Criteria

### Minimum Viable Product (MVP)
- ✅ Electron app launches
- ✅ User can log into Xbox account
- ✅ Console discovery works
- ✅ Video streaming plays without CORS errors
- ✅ Gamepad input works

### v1.0 Feature Complete
- ✅ MVP +
- ✅ Raw HID input (lower latency)
- ✅ Native rendering (OpenGL)
- ✅ LAN P2P (direct console connection)
- ✅ HEVC codec
- ✅ Steam Deck integration

### v2.0+ Future Features
- Custom shaders (FSR, CAS)
- Bitrate unlocking
- Multi-console management
- Cloud save integration
- Modding support

---

## 📞 Communication

### Status Updates (Recommended Frequency)
- **Daily:** Check Jules sessions, pull completed work
- **Every 5-10 tasks:** Run tests, commit batch
- **Weekly:** Review progress vs plan, adjust timeline

### When to Ask for Help
- ❌ Jules failing 3+ times on same task
- ❌ Tests breaking after Jules changes
- ❌ Unclear what task to launch next
- ❌ Legal concerns about code reuse

### What to Tell Me
- "Continue with next batch" → I'll query SQL and launch tasks
- "Jules session X failed" → I'll help debug or break into sub-tasks
- "Change of plans" → I'll adjust task list
- "Show progress" → I'll query SQL and generate report

---

**Current Status:** ⏸️ Planning complete, awaiting execution approval

**Last Updated:** Ready to start  
**Next Review:** After first 10 tasks complete
