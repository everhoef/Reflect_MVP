# BDD Implementation Status Report

## User Story: Timer
- **Notion ID**: 8
- **Page ID**: 29bc1028-ce7b-80d3-991a-fa3949b6e255
- **Analysis Date**: 2025-01-28

---

## Executive Summary

| Category | Implemented | Total | Status |
|----------|-------------|-------|--------|
| **Critical Scenarios** | 3 | 7 | 43% |
| Non-Critical Scenarios | 1 | 7 | 14% |
| **Overall** | 4 | 14 | 29% |

**Bottom Line**: Timer infrastructure exists in backend but frontend countdown display is not implemented. Critical gaps: no real-time countdown, no pause/resume, no visual warnings. Steps configured with timer durations work via server-side expiration check.

---

## Critical Scenarios (Priority Focus)

### ⏳ Scenario 1: Timer displays at phase start
**Status**: PARTIALLY IMPLEMENTED  
**Evidence**:
- `RetroStep.durationSeconds` field stores timer duration per step
- `RetroSession.stepStartedAt` tracks when current step began
- `retrospective_steps.csv` configures durations (e.g., 180s, 240s, 480s)
- `retro.html` displays static text: "X minutes for this step"

**Gap**: No real-time MM:SS countdown display. Only shows total minutes, not remaining time.

**Current UI** (retro.html lines 89-95):
```html
<div th:if="${stepDurationMinutes != null}">
    <span th:text="${stepDurationMinutes} + ' minutes for this step'">10 minutes for this step</span>
</div>
```

### ⏳ Scenario 2: Timer counts down during active phase
**Status**: NOT IMPLEMENTED  
**Evidence**:
- No JavaScript countdown logic found
- No `setInterval` or real-time timer updates
- No SSE events for timer synchronization
- Server only checks if timer expired on advancement requests

**Gap**: No client-side countdown. No synchronized time display across participants.

### ⏳ Scenario 4: Timer reaches zero
**Status**: PARTIALLY IMPLEMENTED  
**Evidence**:
- `RetroSessionService.timerHasExpired()` checks if duration exceeded:
  ```java
  LocalDateTime expirationTime = session.getStepStartedAt().plusSeconds(step.getDurationSeconds());
  return LocalDateTime.now().isAfter(expirationTime);
  ```
- `TIMER_EXPIRES` advancement trigger enables auto-advance check

**Gap**: 
- No visual display of "0:00" 
- No automatic advancement (must poll or click Next)
- No red state indicator

### ⏳ Scenario 5: Facilitator pauses timer
**Status**: NOT IMPLEMENTED  
**Evidence**:
- No pause functionality in `RetroSessionService`
- No `timerPausedAt` field in `RetroSession`
- No pause API endpoint
- No pause UI button

### ⏳ Scenario 6: Facilitator resumes timer
**Status**: NOT IMPLEMENTED  
**Evidence**: (depends on Scenario 5 - pause not implemented)

### ✅ Scenario 7: Facilitator manually advances to next phase
**Status**: IMPLEMENTED  
**Evidence**:
- `RetroApiController.advanceToNextStep()` allows manual advancement
- `AdvancementTrigger` documentation: "Facilitators can always override and advance manually"
- Next button always enabled for facilitator regardless of timer state
- `SessionService.advanceToNextStep()` resets `stepStartedAt` for new step

### ⏳ Scenario 9: Non-facilitator view
**Status**: PARTIALLY IMPLEMENTED  
**Evidence**:
- Non-facilitators see duration display (same as facilitator)
- No pause/resume controls shown (because they don't exist)

**Gap**: No real-time countdown for anyone to see

---

## Non-Critical Scenarios

### ⏳ Implemented (1)

| # | Scenario | Evidence |
|---|----------|----------|
| 8 | Timer persists across reconnection | `RetroSession.stepStartedAt` persisted in database; on reconnect, server can calculate remaining time |

### ⏳ Not Yet Implemented (6)

| # | Scenario | Gap |
|---|----------|-----|
| 3 | Timer visual warning states (green/yellow/red) | No color-coded timer display |
| 10 | 30-second warning before expiry | No warning notification system |
| 11 | Auto-submit on timer expiry | No auto-submit functionality |
| 12 | Facilitator extends time | No "Add time" functionality |
| 13 | Pause prevents auto-submit | No pause functionality |
| 14 | Grace period after timer expiry | No grace period logic |

---

## Technical Implementation Summary

### What Exists (Backend Infrastructure)

**Entity Fields:**
- `RetroStep.durationSeconds` - Timer duration per step (Integer)
- `RetroSession.stepStartedAt` - When current step started (LocalDateTime)

**Advancement Trigger:**
```java
public enum AdvancementTrigger {
    TIMER_EXPIRES,  // Auto-advance when durationSeconds expires
    FACILITATOR_CLICK,
    ALL_RESPONDED,
    AUTO
}
```

**Timer Expiration Check:**
```java
private boolean timerHasExpired(UUID sessionId, RetroStep step) {
    LocalDateTime expirationTime = session.getStepStartedAt()
        .plusSeconds(step.getDurationSeconds());
    return LocalDateTime.now().isAfter(expirationTime);
}
```

**CSV Configuration (steps with timers):**
| Step | Duration | Trigger | showTimer |
|------|----------|---------|-----------|
| Rating Scale | 180s (3 min) | ALL_RESPONDED | true |
| Mad Sad Glad Input | 480s (8 min) | TIMER_EXPIRES | true |
| Original Four Q1 | 240s (4 min) | TIMER_EXPIRES | true |
| Original Four Q2 | 240s (4 min) | TIMER_EXPIRES | true |
| Original Four Q3 | 240s (4 min) | TIMER_EXPIRES | true |
| Original Four Q4 | 240s (4 min) | TIMER_EXPIRES | true |
| Circle of Questions | 480s (8 min) | FACILITATOR_CLICK | true |
| Close Retro Feedback | 120s (2 min) | ALL_RESPONDED | true |

### What's Missing (Frontend Display)

1. **Real-time countdown component**
   - No JavaScript timer
   - No MM:SS format display
   - No countdown animation

2. **Timer synchronization**
   - No SSE events for timer state
   - No server time broadcast
   - No drift correction

3. **Facilitator controls**
   - No pause button
   - No resume button
   - No "Add time" button

4. **Visual feedback**
   - No color states (green/yellow/red)
   - No warning notifications
   - No expiry animation

---

## Recommendations

### High Priority (Critical Functionality)

1. **Implement countdown display component**
   ```html
   <!-- Proposed: timer-countdown.html fragment -->
   <div id="step-timer" 
        th:attr="data-end-time=${stepEndTime}"
        class="timer-display">
       <span id="timer-minutes">00</span>:<span id="timer-seconds">00</span>
   </div>
   <script>
   // Countdown logic with setInterval
   // Color states based on remaining time
   // SSE sync on step_advanced event
   </script>
   ```

2. **Add server endpoint for timer state**
   ```java
   @GetMapping("/api/retro/{id}/timer")
   public TimerStateDto getTimerState(@PathVariable UUID id) {
       // Return: remainingSeconds, isPaused, endTime
   }
   ```

3. **Broadcast timer via SSE**
   - Add `TIMER_UPDATE` event type (already defined in `EventType.java`)
   - Periodic sync or on state change

### Medium Priority (Facilitator Controls)

4. **Implement pause/resume**
   - Add `timerPausedAt` and `pausedDuration` to `RetroSession`
   - Add pause/resume API endpoints
   - UI buttons for facilitator only

5. **Implement time extension**
   - Add "Add 2 minutes" button
   - Update `durationSeconds` or track extension separately

### Low Priority (Polish)

6. **Visual warning states**
   - Green (>2 min), Yellow (30s-2min), Red (<30s)
   - CSS transitions for color changes

7. **Auto-submit on expiry**
   - Complex: requires draft state management
   - Consider simplifying to "warning only" instead

---

## Architecture Considerations

### Timer Synchronization Options

**Option A: Server-authoritative (Recommended)**
- Server broadcasts timer state via SSE
- Clients display server time, no local countdown
- Pros: Perfect sync, no drift
- Cons: More SSE traffic

**Option B: Client-side with sync points**
- Server sends `stepStartedAt` and `durationSeconds`
- Client runs local countdown
- Periodic SSE sync to correct drift
- Pros: Less traffic, smoother display
- Cons: Potential desync

**Option C: Hybrid**
- Client countdown with server sync on key events
- Sync on: reconnect, pause/resume, time extension
- Best balance of UX and accuracy

### Database Changes Needed

```sql
ALTER TABLE retro_sessions ADD COLUMN timer_paused_at TIMESTAMP;
ALTER TABLE retro_sessions ADD COLUMN paused_duration_seconds INTEGER DEFAULT 0;
```

---

## Code Quality Notes

### Strengths
- Clean `AdvancementTrigger` enum with clear semantics
- `stepStartedAt` tracking enables server-side expiration
- CSV configuration allows per-step timer customization
- `showTimer` capability in componentConfig for conditional display

### Gaps
- No frontend timer implementation
- No facilitator timer controls
- `TIMER_UPDATE` event type exists but unused
- Timer expiration check is passive (on advancement request only)

---

*Report generated by BDD Analysis Skill*
