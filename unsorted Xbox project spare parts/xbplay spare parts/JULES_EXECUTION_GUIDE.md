# Jules Execution Guide for XBPlay Project

## Overview
This guide explains how to use Jules (Google's async coding agent) to build the XBPlay client in parallel, bite-sized tasks.

---

## Jules Capabilities & Constraints

### ✅ What Jules CAN Do
- Execute single, well-defined coding tasks
- Work on multiple repositories simultaneously
- Run 3-4 parallel sessions for same task (redundancy)
- Clone repos, create branches, write code, commit changes
- Handle complex coding tasks (but needs clear instructions)
- Work asynchronously (continue other work while Jules codes)

### ❌ What Jules CANNOT Do
- Answer follow-up questions (one-shot only)
- Fix errors interactively (will stop if task is impossible)
- Reference files that don't exist (will deadlock)
- Handle ambiguous instructions (needs explicit paths/signatures)
- Edit its own prompts mid-execution

---

## Command Reference

### Create New Session
```bash
# Basic: uses current directory's repo
jules new "create package.json with electron dependencies"

# Specific repo
jules new --repo 1456319/xbplay-client "create package.json"

# Parallel sessions (3-4 recommended for critical tasks)
jules new --parallel 3 --repo 1456319/xbplay-client "create package.json"
```

### Monitor Sessions
```bash
# List all your sessions
jules remote list --session

# Filter by repo
jules remote list --session | grep xbplay-client

# Check specific session
jules remote list --session | grep SESSION_ID
```

### Pull Results
```bash
# View changes (doesn't apply)
jules remote pull --session SESSION_ID

# Apply changes to local repo
jules remote pull --session SESSION_ID --apply

# Teleport (clone + checkout + apply in one command)
jules teleport SESSION_ID
```

---

## Execution Strategy

### Phase-Based Approach

**Phase 1: Foundation (Tasks 1-50)**
- Sequential execution (each depends on previous)
- 3 parallel sessions per critical task
- Pull and test after each batch of 5-10 tasks

**Phase 2+: Parallel Streams (Tasks 51+)**
- Independent work streams can run simultaneously
- Example: Auth proxy (t61-75) + Console UI (t76-90) at same time
- Reduces overall completion time

### Redundancy Pattern
For each critical task, launch 3 variants with slightly different wording:

```bash
# Variant 1: Explicit file structure
jules new --repo 1456319/xbplay-client "Create file src/main/auth-proxy.ts. Export async function fetchAuthToken(xuid: string): Promise<string>. Use Node.js https module to make GET request to https://chat.xboxlive.com/users/xuid(${xuid})/chat/auth. Return the authKey field from JSON response."

# Variant 2: Functional approach
jules new --repo 1456319/xbplay-client "Implement authentication proxy module in src/main/auth-proxy.ts using Node https to fetch Xbox auth tokens from Microsoft's chat endpoint"

# Variant 3: Example-driven
jules new --repo 1456319/xbplay-client "Build auth-proxy.ts that retrieves Xbox Live chat auth tokens. Example: fetchAuthToken('2535421847897820') should return JWT string from https://chat.xboxlive.com/.../chat/auth endpoint"
```

**Success = 1 out of 3 works.** Discard failed attempts, keep the working one.

---

## Quality Control Process

### After Each Task Batch (5-10 tasks)

1. **Pull all completed sessions**
   ```bash
   jules remote list --session | grep "Completed" | grep "xbplay"
   ```

2. **Review changes locally**
   ```bash
   jules remote pull --session SESSION_ID > review.patch
   git apply --check review.patch  # Test before applying
   ```

3. **Apply working solutions**
   ```bash
   jules remote pull --session SESSION_ID --apply
   ```

4. **Test locally**
   ```bash
   npm run build
   npm test
   npm run lint
   ```

5. **Commit if passing**
   ```bash
   git add .
   git commit -m "feat: implement tasks t001-t010 (Jules sessions)"
   ```

6. **Continue to next batch**

---

## Task Templates

### Template 1: Create New File
```
Create file [PATH]. 
Export [FUNCTIONS/CLASSES]. 
Use [DEPENDENCIES]. 
Implementation: [SPECIFIC LOGIC].
Example usage: [CODE EXAMPLE].
```

### Template 2: Modify Existing File
```
In file [PATH], modify [FUNCTION/CLASS].
Change [WHAT] to [HOW].
Do not modify [WHAT TO KEEP].
Test that [EXPECTED BEHAVIOR].
```

### Template 3: Add Dependency
```
Add npm package [PACKAGE_NAME] to package.json dependencies.
Run npm install.
Create wrapper in [PATH] that exports [API].
```

### Template 4: Implement Feature
```
Implement [FEATURE] in [FILE].
Requirements: [NUMBERED LIST].
Dependencies: [FILES THAT MUST EXIST].
Test by: [VERIFICATION METHOD].
```

---

## Error Handling

### If Jules Fails 3+ Times on Same Task

**Symptom:** All 3 parallel sessions fail or deadlock

**Solutions:**
1. **Break down further** - split into 2-3 sub-tasks
2. **Add more context** - explicit file paths, function signatures
3. **Provide example** - show exact code structure expected
4. **Check dependencies** - ensure prerequisite tasks completed
5. **Manual intervention** - do this task yourself, move on

### Common Failure Modes

**"File not found"**
- Jules tried to import/modify non-existent file
- Fix: Create file first, or specify in task description

**"Ambiguous instruction"**
- Task too vague ("implement auth")
- Fix: Be explicit ("create src/lib/auth.ts with login() method")

**"Dependency missing"**
- Referenced package not installed
- Fix: Add installation step to task

**"Awaiting User Feedback"**
- Jules needs decision on implementation approach
- Fix: Make decision in task description, don't leave choices

---

## Progress Tracking

### SQL Database
All tasks stored in session SQL database:
```sql
-- Check pending tasks
SELECT id, title FROM todos WHERE status = 'pending' LIMIT 10;

-- Mark task in progress
UPDATE todos SET status = 'in_progress' WHERE id = 't001';

-- Complete task
UPDATE todos SET status = 'done' WHERE id = 't001';

-- Find ready tasks (no pending dependencies)
SELECT t.id, t.title FROM todos t
WHERE t.status = 'pending'
AND NOT EXISTS (
    SELECT 1 FROM todo_deps td
    JOIN todos dep ON td.depends_on = dep.id
    WHERE td.todo_id = t.id AND dep.status != 'done'
);
```

### Session Mapping
Track which Jules session handles which task:
```sql
CREATE TABLE jules_sessions (
    task_id TEXT PRIMARY KEY,
    session_id TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status TEXT
);
```

---

## Optimization Tips

### Minimize Wait Time
- Launch 10-20 tasks at once (if independent)
- Check back in 30-60 minutes
- Jules works while you do other things

### Maximize Success Rate
- Clear, explicit instructions
- Concrete examples
- No ambiguous choices
- State all dependencies

### Reduce Rework
- Test locally before committing
- Run linters after each batch
- Keep git history clean (squash Jules commits)

---

## Example Workflow: First 10 Tasks

```bash
# 1. Navigate to project directory
cd ~/xbplay-client  # (create if doesn't exist)

# 2. Launch first batch (tasks t001-t005: repo setup)
jules new --parallel 3 "Create GitHub repository named xbplay-client with MIT license and Node gitignore"
jules new --parallel 3 "Initialize package.json with name 'xbplay-client', version '0.1.0', main 'dist/main/index.js'"
jules new --parallel 3 "Add dependencies to package.json: electron@^28.0.0, typescript@^5.0.0, @types/node@^20.0.0"
jules new --parallel 3 "Create directory structure: src/main/, src/renderer/, src/lib/, src/native/, shaders/, config/, docs/, assets/"
jules new --parallel 3 "Create tsconfig.json with target ES2020, module commonjs, outDir 'dist/', strict true"

# 3. Wait for completion (check status)
watch -n 60 'jules remote list --session | grep xbplay | head -20'

# 4. Pull successful sessions
for session in $(jules remote list --session | grep "Completed" | grep "xbplay" | awk '{print $1}'); do
    jules remote pull --session $session --apply
done

# 5. Test locally
npm run build
npm test

# 6. Commit batch
git add .
git commit -m "feat: project setup (tasks t001-t005)"

# 7. Launch next batch (t006-t010)
# ... repeat process
```

---

## Next Steps

1. ✅ **Review JULES_TASK_BREAKDOWN.md** - ensure tasks are clear
2. ⏳ **Create xbplay-client repo** on GitHub (manual or Jules)
3. ⏳ **Load tasks t001-t100 into SQL** for tracking
4. ⏳ **Launch first 5 tasks** with 3 parallel sessions each
5. ⏳ **Monitor and pull results** after 30-60 minutes
6. ⏳ **Test and commit** first batch
7. ⏳ **Continue through all 500+ tasks** until complete

**Estimated Timeline:**
- 10 tasks/day with testing = 50 days for 500 tasks
- With parallelization = ~30 days
- With your oversight = high quality, low rework

**Let's build XBPlay! 🎮**
