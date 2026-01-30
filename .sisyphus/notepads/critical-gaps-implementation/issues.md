# Issues & Gotchas

## EventType Enum Disambiguation
- **TWO EventType enums exist**:
  - `RetroEvent.EventType` (nested) - USE THIS for timer events
  - `direct.reflect.facilitator.eventing.EventType` (standalone) - legacy, unused
- RetroSessionService has unused import of standalone EventType - LEAVE IT
- Factory methods in RetroEvent.java use nested enum (no import needed)

## Authorization Patterns
- `getParticipantForSession()` THROWS ParticipantNotFoundException (not returns null)
- New API endpoints: use try/catch pattern
- View fragments: use `@PreAuthorize("@participantService.canAccessRetro(#retroId)")`

## Timer Component HTMX Swap Conflict - FIXED

### Problem
The timer-countdown.html component had conflicting HTMX swap behavior:
- Timer container used `hx-swap="outerHTML"` with `hx-trigger="sse:timer_paused from:body, sse:timer_started from:body"`
- Parent `#retro-content` also refreshes on `sse:step_advanced` with `hx-swap="innerHTML"`
- Race condition: When step advances, parent replaces entire `inner-content` div while timer tries to swap itself
- Integration tests timed out waiting for `data-step-index` change because DOM updates were blocked

### Root Cause
Nested HTMX swap operations create race conditions:
```
#retro-content (hx-swap="innerHTML" on step_advanced)
  └─ inner-content
      └─ timer-container (hx-swap="outerHTML" on timer events) ← CONFLICT
```

When parent refreshes, it replaces the entire inner-content including timer-container, but timer component also tries to swap itself simultaneously.

### Solution
Removed HTMX attributes from timer-countdown.html:
- Deleted `th:attr="hx-get=@{/retro/{retroId}/timer-fragment(retroId=${retroSession.id})}"`
- Deleted `hx-trigger="sse:timer_paused from:body, sse:timer_started from:body"`
- Deleted `hx-swap="outerHTML"`
- Kept `id="timer-container"` (needed for JavaScript)
- Kept JavaScript interval cleanup logic (still works on parent refresh)

Timer now becomes a **passive component** that gets re-rendered when parent refreshes. No self-refresh capability needed.

### Pattern Reference
Matches multi-column-board.html pattern:
- Root container has NO HTMX self-refresh attributes
- Individual cards inside listen to SSE events, not the container
- Parent handles all refresh operations

### Verification
- ✅ All integration tests pass (RetroFlowIntegrationTest, SessionRecreationIntegrationTest)
- ✅ shouldValidateCompleteRetroFlowWithColumnIsolation passes without timeout
- ✅ No race conditions on step advancement
- ✅ Timer still displays and counts down correctly
- ✅ JavaScript interval cleanup still works

### Commit
- File: src/main/resources/templates/fragments/components/timer-countdown.html
- Change: Removed 3 HTMX attributes from root div
- Result: Eliminates DOM update race condition
