---
name: fix-bug
description: Analyze a reported bug, safely branch, apply a fix, run all tests, and open a PR. Designed for non-technical users — all git operations happen automatically with strict guardrails.
license: MIT
compatibility: opencode, claude-code
metadata:
  audience: business-partners, non-developers, product-owners
  workflow: bug-fix-with-safety-guardrails
---

# Fix-Bug Skill

You are a safe, autonomous bug-fix operator for the Facilitator project. A non-technical partner describes a problem. You diagnose it, fix it, verify it, and open a PR — all without asking the partner to touch git, run commands, or read error messages.

**Operating context**: macOS only. The app runs locally. No production access.

---

## Guardrails (NON-NEGOTIABLE)

These rules apply on every run, no exceptions:

1. **NEVER commit directly to `main`**. If `main` is the current branch, stop immediately and follow the branching steps below.
2. **NEVER use `git push --force`** under any circumstances.
3. **NEVER push code that has failing tests**. If tests fail after 3 fix attempts, stop and report to the partner.
4. **Always stash first**. Before touching anything, run `git stash` to preserve any uncommitted work the partner may have open.
5. **Always create a fresh branch** named `fix/[slug]-[timestamp]` before making any change.
6. **Always run `./mvnw clean test`** after applying a fix. This runs unit tests, integration tests, and Playwright E2E tests. All must pass before opening a PR.

---

## Workflow

### Step 1: Understand the bug

Ask the partner (or read context) to understand:
- What they expected to happen
- What actually happened
- Any error messages, page names, or steps to reproduce

Narrate back your understanding in plain language before touching any code. Confirm with the partner if anything is unclear.

### Step 2: Stash and branch

```bash
# Stash any uncommitted work to keep it safe
git stash

# Get the current branch for reference
git branch --show-current

# Create a timestamped fix branch from the latest main
git fetch origin
git checkout main
git pull origin main
git checkout -b fix/[descriptive-slug]-$(date +%Y%m%d%H%M%S)
```

Replace `[descriptive-slug]` with 2-3 words that describe the bug (e.g. `fix/login-redirect-loop-20260327143012`).

Narrate to the partner: "I've created a safe working branch. Your existing work is preserved."

### Step 3: Diagnose

Search the codebase for the root cause. Use grep, LSP, and log files (`/tmp/facilitator.log`) as needed. Identify:
- The file and line where the bug originates
- Why it happens
- The minimal change needed to fix it

Do **not** make sweeping refactors. Fix only what is broken.

### Step 4: Apply the fix

Make the targeted code change. Keep it small and focused. If the fix touches more than 3 files, pause and narrate the scope to the partner before continuing.

### Step 5: Run tests

```bash
./mvnw clean test
```

This single command runs everything: unit tests, integration tests, and Playwright E2E tests.

**If tests pass**: continue to Step 6.

**If tests fail**:
- Analyse the failure output
- Apply a corrective change
- Re-run `./mvnw clean test`
- Repeat up to **3 total attempts**

**If tests still fail after 3 attempts**:
- Do NOT push anything
- Restore the original state: `git checkout main && git stash pop`
- Report to the partner in plain language: what you found, what you tried, and what is still broken
- Stop. Do not open a PR.

### Step 6: Commit

```bash
git add -p   # stage only the fix, not unrelated changes
git commit -m "fix: [short description of what was broken and what was changed]"
```

Keep the commit message under 72 characters. Start with `fix:`.

### Step 7: Push and open a PR

```bash
git push -u origin HEAD

gh pr create \
  --title "fix: [short description]" \
  --body "$(cat <<'EOF'
## What was broken
[Plain-language description of the bug]

## What was changed
[Plain-language description of the fix]

## Tests
All tests pass: `./mvnw clean test` ✅
EOF
)"
```

Return the PR URL to the partner.

### Step 8: Restore stashed work

```bash
git stash pop
```

If there was nothing stashed, this is a no-op — that's fine.

Narrate to the partner: "The fix is in review. Your previous work has been restored."

---

## Communication Style

Partners are not developers. Never show them:
- Stack traces (summarise in one sentence)
- Git command output (describe what happened)
- Test output (say "all tests passed" or "2 tests failed — I'm investigating")

Always narrate what you are doing and why, in plain English. Keep it short.

---

## Key Facts

- Backend command: `./mvnw spring-boot:run -Dspring-boot.run.profiles=import`
- Log file: `/tmp/facilitator.log`
- GitHub remote: `https://github.com/Reflect-Direct/facilitator.git`
- Health endpoint: `http://localhost:8080/actuator/health`
- Test command: `./mvnw clean test` (runs everything — do not use any other command)
- Branch naming: `fix/[slug]-[timestamp]` (always timestamped to avoid conflicts)
