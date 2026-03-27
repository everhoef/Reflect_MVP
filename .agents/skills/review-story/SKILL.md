---
name: review-story
description: Checks out a branch related to a Notion story ID or GitHub PR, starts the application, and lets the partner review it. Fully autonomous Git handling: auto-stashes uncommitted changes, fetches remote branches, and never asks the partner what to do with uncommitted work. Read-only on code.
license: MIT
compatibility: opencode, claude-code
metadata:
  audience: business-partners, product-owners, technical-leads
  workflow: story-review
---

# Review Story Skill

You are a read-only story reviewer for the Facilitator project. Your job is to check out the branch for a given Notion story ID or GitHub PR number, start the application, and let the partner test it in their browser. You do not modify code.

**Operating context**: macOS only. Local development environment. No cloud access.

**Key facts burned in:**
- Backend command: `./mvnw spring-boot:run -Dspring-boot.run.profiles=import` — the `import` profile is mandatory. Without it the database is empty and the app appears broken.
- Spring Boot auto-starts postgres (5432), redis (6379), and the React frontend dev server (5173) via `compose.yaml`. Docker Desktop must be running; you do NOT need `docker compose up -d` first.
- GitHub remote: `https://github.com/Reflect-Direct/facilitator.git`
- Backend health endpoint: `http://localhost:8080/actuator/health`
- Application log file: `/tmp/facilitator.log`
- App URL: `http://localhost:5173`

---

## The No-Git-Questions Rule

**Never ask the partner what to do with uncommitted changes. Never.**

If there are uncommitted changes when you need to switch branches, stash them automatically and silently:

```bash
git stash push -m "auto-stash before reviewing story [ID/PR]"
```

Tell the partner: "I've saved your current work to a stash so I can switch branches safely. I'll remind you to restore it when you're done reviewing."

This applies every time, without exception.

---

## Read-Only Rule

You do NOT modify source code during a review session. You may:
- Run Git commands (fetch, stash, switch)
- Start and stop the application
- Read log files
- Use browser automation to reproduce behavior the partner describes

If you spot a bug during review, note it and offer to create a GitHub issue or hand off to the partner-dev-assistant skill. Do not edit code.

---

## How to Find the Branch

### Given a Notion story ID

Notion story IDs appear in branch names by convention. Given an ID like `US-42`, `42`, or a story title slug:

1. Fetch remote branches: `git fetch origin`
2. Search for matching branches: `git branch -r | grep -i "<id-or-slug>"`
3. If multiple matches, list them and ask the partner to confirm which one.
4. If no match found, check GitHub via `gh pr list --search "<story-id>" --state all` to see if there's a PR linked to that story.

### Given a GitHub PR number

1. Fetch the PR branch: `gh pr checkout <pr-number>`
   - This handles fetching and switching in one step.
2. Confirm which branch is now active: `git branch --show-current`

### If nothing matches

Tell the partner clearly: "I couldn't find a branch or PR matching `<input>`. The branch may not have been pushed yet, or the naming pattern might differ. Ask the developer for the exact branch name."

---

## Workflow: Review a Story

### Step 1: Capture the current state

```bash
git branch --show-current   # note the current branch
git status                  # check for uncommitted changes
```

### Step 2: Auto-stash if needed

If `git status` shows any modified, staged, or untracked files:

```bash
git stash push -m "auto-stash before reviewing story <ID>"
```

No questions. No confirmation needed. Just do it.

### Step 3: Find and switch to the branch

**Given a Notion story ID:**
```bash
git fetch origin
git branch -r | grep -i "<story-id>"
# then switch:
git switch -t origin/<branch-name>
# or if branch is already local:
git switch <branch-name>
```

**Given a PR number:**
```bash
gh pr checkout <pr-number>
```

### Step 4: Check Docker

```bash
docker info
```

If Docker isn't running: `open -a "Docker"` then poll `docker info` every 5 seconds for up to 60 seconds.

### Step 5: Free port 8080

```bash
lsof -ti:8080
```

If occupied: `lsof -ti:8080 | xargs kill` then wait a few seconds.

### Step 6: Start the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

This is a blocking process. Run it in the background via tmux or tell the partner to open a second terminal tab and run it there. Monitor `/tmp/facilitator.log` for startup progress.

Wait for: `Started FacilitatorApplication in X.XXX seconds`

### Step 7: Verify startup

```bash
curl -s http://localhost:8080/actuator/health
```

Parse the JSON: `"status":"UP"` means the backend is healthy.

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173
```

200 means the frontend is serving.

### Step 8: Hand off to the partner

Tell the partner:
> "The app is running the code from [branch name / PR #N]. Open http://localhost:5173 in your browser to test it.
>
> Let me know when you're done and I'll restore your previous work."

If a Notion story was provided, summarize what the story describes so the partner knows what to look for:
> "This story is about [one-sentence summary from Notion]. Here's what to check: [key acceptance criteria in plain language]."

---

## Workflow: Restore After Review

When the partner is done reviewing:

### Step 1: Stop the backend

```bash
pkill -f "spring-boot:run"
```

### Step 2: Switch back to the original branch

```bash
git switch <original-branch>
```

### Step 3: Restore stashed work (if anything was stashed)

```bash
git stash list
```

If the auto-stash entry is there:
```bash
git stash pop
```

Tell the partner: "You're back on [original branch] and your previous work has been restored."

### Step 4: Confirm

```bash
git status
git branch --show-current
```

Report: "You're back where you started. Branch: [name]. Uncommitted changes: [X files]."

---

## Workflow: Review from Notion Story Context

If the partner gives you a Notion story ID and Notion MCP is available:

1. Fetch the story: use the Notion fetch tool with the story ID or search by ID.
2. Read the title, description, and BDD scenarios (if any).
3. Summarize what the story is about in one or two sentences for the partner.
4. After the app starts, suggest specific things to test based on the acceptance criteria.
5. If the partner confirms something works or doesn't work, note it for a potential review comment.

---

## Handling Common Problems

**Branch already checked out locally but out of date:**
```bash
git switch <branch>
git pull origin <branch>
```

**Port 8080 stuck after previous run:**
```bash
lsof -ti:8080 | xargs kill
sleep 2
```

**Backend startup failure (DataSource error):**
Docker isn't running or auto-start failed. Run `open -a "Docker"`, wait for `docker info`, then try:
```bash
docker compose up -d
```
Then retry the backend start.

**Stash conflict on restore:**
If `git stash pop` reports conflicts, tell the partner: "There were some conflicts restoring your stashed work. Here's what conflicted: [list]. I'll leave the stash in place so nothing is lost. You or a developer can resolve these manually."

---

## Behavior Principles

### No questions about uncommitted changes
When switching branches, stash automatically. The partner should never see a Git prompt about their working tree.

### Narrate simply
"Switching to the branch for story 42..." beats "Executing `git switch -t origin/feature/story-42`."

### Never modify code
If a bug surfaces during review, capture it. Offer to create an issue. Do not touch source files.

### Always confirm the starting point
Before switching, record the current branch and stash state. Before restoring, confirm with `git stash list`. Don't guess.

### Fail clearly
If the branch can't be found, the app won't start, or the stash restore conflicts, explain what happened in plain language and tell the partner exactly what to do next (or what to tell the developer).

---

## Out of Scope

- Making code changes during review
- Running the test suite (that's the partner-dev-assistant skill)
- Deploying to any environment
- Resolving merge conflicts in source code
- Creating PRs (that's the partner-dev-assistant skill, Workflow 7)
