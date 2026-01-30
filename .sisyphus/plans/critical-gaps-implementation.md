# Critical BDD Gaps Implementation

## Context

### Original Request
Implement critical BDD scenario gaps identified in user stories 8 (Timer), 11 (Input mechanism), and 12 (Guided Facilitation). Work should proceed in Notion ID order.

### Interview Summary
**Key Discussions**:
- User chose "critical gaps only" scope (not all BDD scenarios)
- 10-input limit is per-step (not per-phase)
- Timer expiry shows visual warning only (no auto-advance - respects facilitator override principle)
- Timer pause state persists in database (but no migration needed - greenfield project, Hibernate auto-DDL)

**Research Findings**:
- `RetroSession` has `stepStartedAt` (LocalDateTime) field already - **currently lacks pause fields** (to be added in Task 1)
- `RetroStep` has `durationSeconds` (Integer) field
- `RetroSessionService.timerHasExpired()` exists
- **EventType enum disambiguation**: Two enums exist in the codebase:
  - `RetroEvent.EventType` (nested in `RetroEvent.java`) - **THIS IS THE ONE TO USE** - has `TIMER_PAUSED`, `TIMER_STARTED`, `TIMER_FINISHED`
  - `direct.reflect.facilitator.eventing.EventType` (standalone in `EventType.java`) - legacy/planned enum, NOT used for timer events
  - Factory methods in `RetroEvent.java` use the nested `EventType` directly (no import needed inside the same file)
  - **Note**: `RetroSessionService.java` imports the standalone `EventType` but does NOT currently use it (it's an unused import). This does NOT affect our work - the new `timerPaused()` and `timerStarted()` factory methods are added to `RetroEvent.java` which uses its own nested enum. `RetroSessionService` will call `RetroEvent.timerPaused(sessionId)` without needing any EventType import.
- SSE event naming: `EventService.sendToLocalEmitters()` at `src/main/java/direct/reflect/facilitator/eventing/EventService.java:229` sends event name as `event.type().name().toLowerCase()` → `TIMER_PAUSED` becomes `timer_paused`
- `VoteLimitExceededException` pattern exists for reference
- Sidebar uses Tailwind class `w-80` (320px) but lacks `flex-shrink-0`
- `participantService.getParticipantForSession()` **throws `ParticipantNotFoundException`** (not returns null) per `ParticipantService.java:108-109`
- Tailwind is via CDN (`layout.html:12` - `https://cdn.tailwindcss.com`) which includes all standard utilities including `min-w-80`
- Test file exists: `src/test/java/direct/reflect/facilitator/facilitation/RetroSessionServiceTest.java`
- No Playwright setup exists in this repo - will use **manual browser verification** instead

### Metis Review
**Identified Gaps** (addressed):
- Timer pause fields need to be added to `RetroSession` entity
- Timer remaining time calculation must account for pause duration
- Input limit validation must be server-side (not just client)
- Mobile responsive behavior for sidebar: Keep simple (CSS-only fix for desktop)

### Logging Policy Scope Decision
Per AGENTS.md: "DO NOT add INFO/WARN logs for debugging."
- **This plan will NOT downgrade or remove existing INFO logs** in touched methods
- **Existing `log.info` statements remain AS-IS** (e.g., `submitColumnResponse` has existing INFO log)
- **New code added by this plan will use DEBUG level** for routine operations
- **ERROR level** is appropriate for unexpected failures (e.g., catch blocks for unexpected exceptions)
- **Allowed changes**: Adding new DEBUG logs, keeping existing INFO logs unchanged

### Controller Authorization Pattern
`participantService.getParticipantForSession()` **throws `ParticipantNotFoundException`** when unauthorized.
- **New timer endpoints** will catch this exception and return 403
- **Existing endpoints will NOT be refactored** as part of this work (out of scope)

---

## Work Objectives

### Core Objective
Implement 3 critical BDD gaps to achieve full compliance with user stories 8, 11, and 12.

### Concrete Deliverables
1. **Timer (ID 8)**: Real-time countdown component with pause/resume for facilitator
2. **Input mechanism (ID 11)**: 10-input limit validation per step
3. **Guided Facilitation (ID 12)**: Sidebar CSS fix to prevent collapse

### Definition of Done
- [ ] Timer displays MM:SS format, counts down in real-time
- [ ] Timer shows visual states: green (>2min), yellow (30s-2min), red (<30s)
- [ ] Facilitator can pause/resume timer (non-facilitators see pause indicator)
- [ ] Timer state survives page refresh
- [ ] 10-input limit enforced per step for MULTI_COLUMN_BOARD
- [ ] Left sidebar does not collapse when center content expands

### Must Have
- Server-authoritative timer (server calculates remaining time)
- SSE event broadcasting for timer state changes
- Error message for input limit exceeded

### Must NOT Have (Guardrails)
- NO auto-advance on timer expiry (visual warning only)
- NO WebSocket (use existing SSE pattern)
- NO sound notifications on timer expiry
- NO timer extension feature
- NO client-side-only validation for input limit
- NO changes to sidebar content (CSS fix only)
- NO JavaScript for sidebar fix (Tailwind utilities only)
- NO changes to RATING_SCALE behavior (already has 1-per-participant)
- NO refactoring of existing log statements

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES (JUnit 5, Testcontainers, Spring Boot Test)
- **JUnit test file**: `src/test/java/direct/reflect/facilitator/facilitation/RetroSessionServiceTest.java` (exists)
- **Playwright**: NOT configured in this repo - **manual browser verification** will be used instead
- **Framework**: JUnit 5 + Testcontainers for backend, manual browser testing for frontend

### Manual Browser Verification
Each TODO includes manual browser verification steps (not automated Playwright).

### Timer Test Data Pre-Check (VERIFIED)
The timer verification requires at least one imported step with `durationSeconds > 0`.

**CSV file**: `src/main/resources/retrospective_steps.csv`
**Column**: `durationSeconds` (4th column)

**Steps with timers (verified from CSV)**:
- Stage 1, Step 2: `durationSeconds=180` (3 min) - RATING_SCALE, triggers ALL_RESPONDED
- Stage 2, Step 2: `durationSeconds=480` (8 min) - MULTI_COLUMN_BOARD, triggers TIMER_EXPIRES
- Stage 3, Steps 2/4/6/8: `durationSeconds=240` (4 min each) - MULTI_COLUMN_BOARD

**Recommended test path**: Navigate to Stage 2 Step 2 (Mad Sad Glad input step) which has an 8-minute timer with TIMER_EXPIRES trigger - ideal for testing countdown + expiry behavior.

---

## Task Flow

```
Task 1 (Entity) → Task 2 (Service + Event) → Task 3 (API + View) → Task 4 (Frontend)
                                                                          ↓
                                                             Task 5 (Input Limit) - can run parallel with 4
                                                                          ↓
                                                             Task 6 (Sidebar CSS) - independent
```

## Parallelization

| Group | Tasks | Reason |
|-------|-------|--------|
| A | 5, 6 | Independent of timer frontend work |

| Task | Depends On | Reason |
|------|------------|--------|
| 2 | 1 | Service needs entity fields |
| 3 | 2 | API needs service methods |
| 4 | 3 | Frontend needs API + view endpoints |
| 5 | - | Independent (no timer dependency) |
| 6 | - | Independent (CSS only) |

---

## TODOs

- [x] 1. Timer: Add pause fields to RetroSession entity

  **What to do**:
  - Add `timerPausedAt` (LocalDateTime) field to `RetroSession.java`
  - Add `accumulatedPauseSeconds` (Long, default 0L) field to `RetroSession.java`
  - These fields track when timer was paused and total accumulated pause time

  **Note**: `RetroSession` currently only has `createdAt`, `finishedAt`, `stepStartedAt` - the pause fields do not exist yet and must be added.

  **Naming convention**: Hibernate uses Spring Boot's default `PhysicalNamingStrategyStandardImpl` which converts camelCase to snake_case. So `timerPausedAt` → `timer_paused_at` column. No annotations needed - existing fields like `stepStartedAt` map to `step_started_at` automatically. Entity uses Lombok `@Data` for getters/setters.

  **Must NOT do**:
  - Do NOT use `Instant` (keep consistent with existing `stepStartedAt` which is `LocalDateTime`)
  - Do NOT add complex pause history tracking
  - Do NOT create a separate Timer entity

  **Parallelizable**: NO (other tasks depend on this)

  **References**:

  **Pattern References**:
  - `src/main/java/direct/reflect/facilitator/facilitation/RetroSession.java:34-36` - Existing LocalDateTime fields (`createdAt`, `finishedAt`, `stepStartedAt`) - follow this exact pattern

  **Why Each Reference Matters**:
  - The existing datetime fields use `LocalDateTime` type with simple field declarations, so new pause fields should match

  **Acceptance Criteria**:

  **Manual Execution Verification**:
  - [ ] Application starts successfully: `mvn spring-boot:run -Dspring-boot.run.profiles=import`
  - [ ] Check application logs in separate terminal: `tail -f /tmp/facilitator.log` - no Hibernate errors
  - [ ] Verify Hibernate auto-DDL created columns:
    - **DDL config**: `src/main/resources/application.yaml` line 16: `ddl-auto: create-drop`
    - This means Hibernate recreates the schema on each startup (development mode)
    - New columns `timer_paused_at` and `accumulated_pause_seconds` will be auto-created
    - **Log signature**: Look for `Hibernate: create table retro_sessions` or similar DDL statements in logs (no errors)

  **Commit**: YES
  - Message: `feat(timer): add pause tracking fields to RetroSession entity`
  - Files: `src/main/java/direct/reflect/facilitator/facilitation/RetroSession.java`

---

- [x] 2. Timer: Add timer state calculation, pause/resume methods, and event factory methods

  **Current state**: `RetroEvent.EventType` enum has `TIMER_STARTED`, `TIMER_PAUSED`, `TIMER_FINISHED` values (defined in `RetroEvent.java:39-41`), but **no existing code publishes these events**. There are no factory methods for timer events yet. This task adds both the service methods AND the factory methods to enable timer event publishing for the first time.

  **What to do**:
  
  **2.1 Create `TimerStateDto` record**:
  Location: `src/main/java/direct/reflect/facilitator/facilitation/dto/TimerStateDto.java`
  
  **Pre-check**: `TimerStateDto` does not exist in codebase (verified via grep). Safe to create.
  
  ```java
  package direct.reflect.facilitator.facilitation.dto;

  public record TimerStateDto(
      long remainingSeconds,  // seconds left (0 if expired)
      boolean isPaused,       // true if currently paused
      String state            // "green", "yellow", "red", "expired"
  ) {}
  ```

  **2.2 Add `getTimerState(UUID sessionId)` method to `RetroSessionService`**:
  
  **EXACT FORMULA** (critical for correctness):
  ```java
  public TimerStateDto getTimerState(UUID sessionId) {
      RetroSession session = getSessionById(sessionId);
      RetroStep currentStep = getCurrentStep(sessionId);
      
      // No timer for this step
      if (currentStep == null || currentStep.getDurationSeconds() == null || currentStep.getDurationSeconds() <= 0) {
          return null;  // Indicates no timer
      }
      
      // Calculate elapsed wall-clock time since step started
      LocalDateTime now = LocalDateTime.now();
      long elapsedWallClock = java.time.Duration.between(session.getStepStartedAt(), now).getSeconds();
      
      // If currently paused, freeze elapsed time at pause moment
      boolean isPaused = session.getTimerPausedAt() != null;
      if (isPaused) {
          elapsedWallClock = java.time.Duration.between(session.getStepStartedAt(), session.getTimerPausedAt()).getSeconds();
      }
      
      // Subtract accumulated pause time to get effective elapsed time
      long effectiveElapsed = elapsedWallClock - (session.getAccumulatedPauseSeconds() != null ? session.getAccumulatedPauseSeconds() : 0L);
      
      // Calculate remaining
      long remaining = currentStep.getDurationSeconds() - effectiveElapsed;
      if (remaining < 0) remaining = 0;
      
      // Determine color state
      String state;
      if (remaining <= 0) {
          state = "expired";
      } else if (remaining <= 30) {
          state = "red";
      } else if (remaining <= 120) {
          state = "yellow";
      } else {
          state = "green";
      }
      
      return new TimerStateDto(remaining, isPaused, state);
  }
  ```

  **2.3 Add `pauseTimer(UUID sessionId)` method**:
  ```java
  @Transactional
  public void pauseTimer(UUID sessionId) {
      RetroSession session = getSessionById(sessionId);
      if (session.getTimerPausedAt() == null) {  // Only pause if not already paused
          session.setTimerPausedAt(LocalDateTime.now());
          sessionRepository.save(session);
          // IMPORTANT: Use RetroEvent factory method, NOT direct EventType reference
          eventService.publish(RetroEvent.timerPaused(sessionId));
      }
  }
  ```
  
  **GUARDRAIL**: Do NOT reference `EventType.*` directly in `RetroSessionService`. Only call `RetroEvent.timerPaused()` and `RetroEvent.timerStarted()` factory methods. This avoids accidentally using the wrong (standalone) `EventType` enum that's imported but unused.

  **2.4 Add `resumeTimer(UUID sessionId)` method**:
  ```java
  @Transactional
  public void resumeTimer(UUID sessionId) {
      RetroSession session = getSessionById(sessionId);
      if (session.getTimerPausedAt() != null) {  // Only resume if paused
          // Calculate how long it was paused and add to accumulated
          long pauseDuration = java.time.Duration.between(session.getTimerPausedAt(), LocalDateTime.now()).getSeconds();
          long accumulated = (session.getAccumulatedPauseSeconds() != null ? session.getAccumulatedPauseSeconds() : 0L) + pauseDuration;
          session.setAccumulatedPauseSeconds(accumulated);
          session.setTimerPausedAt(null);  // Clear paused state
          sessionRepository.save(session);
          // Use TIMER_STARTED event for resume (no separate TIMER_RESUMED enum exists)
          eventService.publish(RetroEvent.timerStarted(sessionId));
      }
  }
  ```

  **2.5 Modify `advanceToNextStep()` to reset pause state**:
  In the `advanceToNextStep()` method, find EACH occurrence of `session.setStepStartedAt(LocalDateTime.now())` (there are 3 code paths: initial start, advance within stage, and advance to new stage). After EACH `setStepStartedAt()` call, add:
  ```java
  session.setTimerPausedAt(null);
  session.setAccumulatedPauseSeconds(0L);
  ```
  
  **How to find the locations**: Search for `setStepStartedAt(LocalDateTime.now())` in `advanceToNextStep()` method - add the two reset lines immediately after each occurrence.
  
  **Session start note**: `startSession()` calls `advanceToNextStep()` which triggers the first step. The reset inserted after `setStepStartedAt()` ensures timer pause state is always cleared whenever a new step starts, including the initial start. This handles any edge cases where fields might have unexpected values.

  **2.6 Add factory methods to `RetroEvent.java`**:
  ```java
  /**
   * Create a timer paused event
   */
  public static RetroEvent<Void> timerPaused(UUID retroId) {
      return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), 
          retroId, EventType.TIMER_PAUSED, "system", Instant.now(), null);
  }
  
  /**
   * Create a timer started/resumed event (use TIMER_STARTED for both start and resume)
   */
  public static RetroEvent<Void> timerStarted(UUID retroId) {
      return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), 
          retroId, EventType.TIMER_STARTED, "system", Instant.now(), null);
  }
  ```
  
  **CRITICAL EventType guardrail**: 
  - The `EventType` referenced in these factory methods is `RetroEvent.EventType` (the nested enum inside `RetroEvent.java`)
  - Do **NOT** import or use `direct.reflect.facilitator.eventing.EventType` (the standalone enum in `EventType.java`)
  - `RetroSessionService.java` currently has an **unused** import for the standalone `EventType` - **LEAVE IT UNTOUCHED** (do not remove or modify; it has no effect on new code)
  - The factory methods are inside `RetroEvent.java`, so they reference the nested `EventType` without any import
  - **Acceptance**: `mvn compile` passes without errors; no new imports of standalone `EventType` added
  
  **Event naming clarification**: We use `TIMER_STARTED` for resume because there is no `TIMER_RESUMED` enum in the existing codebase. SSE will emit `timer_started` event, which the UI listens to. This is consistent - "starting" a paused timer is semantically resuming.

  **Must NOT do**:
  - Do NOT auto-advance when timer expires
  - Do NOT change existing `timerHasExpired()` method signature
  - Do NOT add WebSocket support
  - Do NOT create a new `TIMER_RESUMED` enum - use existing `TIMER_STARTED`

  **Parallelizable**: NO (depends on Task 1, Task 3 depends on this)

  **References**:

  **Pattern References**:
  - `src/main/java/direct/reflect/facilitator/facilitation/RetroSessionService.java` - `timerHasExpired()` method - shows existing time calculation pattern using `stepStartedAt` and `durationSeconds`
  - `src/main/java/direct/reflect/facilitator/facilitation/RetroSessionService.java` - `advanceToNextStep()` method - contains 3 occurrences of `setStepStartedAt(LocalDateTime.now())` where pause reset must be added
  - `src/main/java/direct/reflect/facilitator/eventing/RetroEvent.java` - nested `EventType` enum (lines 21-42) - `TIMER_STARTED`, `TIMER_PAUSED`, `TIMER_FINISHED` already exist here
  - `src/main/java/direct/reflect/facilitator/eventing/RetroEvent.java` - Factory methods `sessionStarted()`, `stepAdvanced()` - follow this exact pattern for new `timerPaused()` and `timerStarted()` methods
  - `src/main/java/direct/reflect/facilitator/eventing/EventService.java` - `sendToLocalEmitters()` method - SSE event name is `event.type().name().toLowerCase()` → confirms `TIMER_PAUSED` becomes `timer_paused`

  **Why Each Reference Matters**:
  - `timerHasExpired()` shows how to read `stepStartedAt` and `durationSeconds`
  - `advanceToNextStep()` already resets `stepStartedAt` - search for `setStepStartedAt(LocalDateTime.now())` and add pause reset after each occurrence
  - `RetroEvent.EventType` (the NESTED enum, not standalone `EventType.java`) has the timer events - factory methods in same file use it directly
  - `EventService` confirms SSE event naming convention

  **Acceptance Criteria**:

  **Manual Execution Verification**:
  - [ ] Add tests to existing test file: `src/test/java/direct/reflect/facilitator/facilitation/RetroSessionServiceTest.java`
    - Test `getTimerState()` returns correct remaining time
    - Test `pauseTimer()` sets `timerPausedAt`
    - Test `resumeTimer()` adds to `accumulatedPauseSeconds` and clears `timerPausedAt`
  - [ ] Run tests: `mvn test -Dtest=RetroSessionServiceTest`

  **Mockito Unit Test Strategy** (CRITICAL - matches existing test style):
  
  `RetroSessionServiceTest.java` is a **Mockito unit test** (no Spring context, no Testcontainers, mocked repositories). Tests must use stubbing and argument capture, NOT real persistence.
  
  **getCurrentStep() dependency chain** (CRITICAL for test stubbing):
  `getCurrentStep(sessionId)` calls:
  1. `getSessionById(sessionId)` → needs `sessionRepository.findById()` stub
  2. `session.getCurrentStage()` → needs `session.template` + `session.phase` set
  3. `stepRepository.findByRetroStageOrderByOrderIndexAsc(currentStage)` → needs stub
  
  ```java
  @Test
  void getTimerState_returnsCorrectRemainingSeconds() {
      // Setup: Create test objects with full dependency chain
      UUID sessionId = UUID.randomUUID();
      
      // Create step with timer
      RetroStep step = new RetroStep();
      step.setDurationSeconds(300);
      step.setOrderIndex(0);
      
      // Create stage containing the step
      RetroStage stage = new RetroStage();
      stage.setPhase(RetroPhase.SET_THE_STAGE);
      
      // Create template with stage mapping
      RetroTemplate template = new RetroTemplate();
      template.setStages(List.of(stage));
      
      // Create session with all required relationships
      RetroSession session = new RetroSession();
      session.setId(sessionId);
      session.setTemplate(template);
      session.setPhase(RetroPhase.SET_THE_STAGE);
      session.setCurrentStepIndex(0);
      session.setStepStartedAt(LocalDateTime.now().minusSeconds(60)); // 60s ago
      session.setTimerPausedAt(null);
      session.setAccumulatedPauseSeconds(0L);
      
      // Stub repositories
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
      when(stepRepository.findByRetroStageOrderByOrderIndexAsc(stage)).thenReturn(List.of(step));
      
      // Act
      TimerStateDto result = retroService.getTimerState(sessionId);
      
      // Assert with tolerance: expected ~240 seconds, allow ±2 seconds for test execution
      assertThat(result.remainingSeconds()).isBetween(238L, 242L);
      assertThat(result.state()).isEqualTo("green"); // 240s > 120s
  }
  
  @Test
  void pauseTimer_setsTimerPausedAtAndSaves() {
      // Setup
      UUID sessionId = UUID.randomUUID();
      RetroSession session = new RetroSession();
      session.setId(sessionId);
      session.setTimerPausedAt(null); // Not paused
      
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
      
      // Act
      retroService.pauseTimer(sessionId);
      
      // Assert: verify save was called with timerPausedAt set
      ArgumentCaptor<RetroSession> captor = ArgumentCaptor.forClass(RetroSession.class);
      verify(sessionRepository).save(captor.capture());
      RetroSession saved = captor.getValue();
      assertThat(saved.getTimerPausedAt()).isNotNull();
      
      // Verify event was published
      verify(eventService).publish(any(RetroEvent.class));
  }
  
  @Test
  void resumeTimer_addsToAccumulatedAndClearsPausedAt() {
      // Setup: session paused 30 seconds ago
      UUID sessionId = UUID.randomUUID();
      RetroSession session = new RetroSession();
      session.setId(sessionId);
      session.setTimerPausedAt(LocalDateTime.now().minusSeconds(30));
      session.setAccumulatedPauseSeconds(10L); // Already had 10s accumulated
      
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
      
      // Act
      retroService.resumeTimer(sessionId);
      
      // Assert: accumulated should be ~40s (10 + 30), pausedAt cleared
      ArgumentCaptor<RetroSession> captor = ArgumentCaptor.forClass(RetroSession.class);
      verify(sessionRepository).save(captor.capture());
      RetroSession saved = captor.getValue();
      assertThat(saved.getTimerPausedAt()).isNull();
      assertThat(saved.getAccumulatedPauseSeconds()).isBetween(38L, 42L); // ~40s with tolerance
  }
  ```
  
  **Key principles for Mockito tests**:
  - Use `when(...).thenReturn(...)` to stub repository reads
  - Use `ArgumentCaptor` to verify what was passed to `save()`
  - Use `verify(...)` to check method calls
  - NO `Thread.sleep` - all assertions are immediate
  - Time tolerances (±2s) account for test execution time between `now()` calls

  **Commit**: YES
  - Message: `feat(timer): add timer state calculation and pause/resume service methods`
  - Files: `RetroSessionService.java`, new `TimerStateDto.java`, `RetroEvent.java`, `RetroSessionServiceTest.java`

---

- [x] 3. Timer: Add REST API endpoints AND view fragment endpoint

  **What to do**:
  
  **3.1 Add JSON API endpoint `GET /api/retro/{retroId}/timer` in `RetroApiController`**:
  ```java
  @GetMapping("/{retroId}/timer")
  @PreAuthorize("hasAnyRole('USER', 'GUEST')")
  public ResponseEntity<TimerStateDto> getTimerState(@PathVariable UUID retroId, HttpServletRequest httpRequest) {
      // Authorization-first: verify participant has access to this session
      // getParticipantForSession() throws ParticipantNotFoundException if not authorized
      try {
          participantService.getParticipantForSession(httpRequest, retroId);
      } catch (ParticipantNotFoundException e) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      
      TimerStateDto state = retroService.getTimerState(retroId);
      if (state == null) {
          return ResponseEntity.noContent().build();  // HTTP 204 - No timer for this step
      }
      return ResponseEntity.ok(state);
  }
  ```
  
  **Note on JSON endpoint usage**: The JSON endpoint is provided for debugging/future use (e.g., external integrations). The primary UI uses server-rendered fragments; the JavaScript countdown initializes from data attributes rendered by the server, not from a JSON fetch.
  
  **HTTP 204 handling**: When `getTimerState()` returns `null` (no timer for this step, or no current step), the endpoint returns HTTP 204 No Content. UI must treat this as "No time limit" - the timer component already handles this with `th:if="${timerState == null}"` showing "No time limit".
  
  **Authorization note**: `getParticipantForSession()` **throws** `ParticipantNotFoundException` (never returns null). For new endpoints, always use try/catch pattern shown above. Do NOT copy any existing null-check patterns that may exist in older code.

  **3.2 Add `POST /api/retro/{retroId}/timer/pause` endpoint**:
  ```java
  @PostMapping("/{retroId}/timer/pause")
  @PreAuthorize("hasAnyRole('USER', 'GUEST')")
  public ResponseEntity<Void> pauseTimer(@PathVariable UUID retroId, HttpServletRequest httpRequest) {
      // Facilitator-only check
      if (!participantService.isFacilitator(httpRequest, retroId)) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      
      retroService.pauseTimer(retroId);
      return ResponseEntity.ok().build();
      // Note: No HX-Trigger needed - SSE event (timer_paused) handles UI refresh for ALL participants
  }
  ```

  **3.3 Add `POST /api/retro/{retroId}/timer/resume` endpoint**:
  ```java
  @PostMapping("/{retroId}/timer/resume")
  @PreAuthorize("hasAnyRole('USER', 'GUEST')")
  public ResponseEntity<Void> resumeTimer(@PathVariable UUID retroId, HttpServletRequest httpRequest) {
      // Facilitator-only check
      if (!participantService.isFacilitator(httpRequest, retroId)) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      
      retroService.resumeTimer(retroId);
      return ResponseEntity.ok().build();
      // Note: No HX-Trigger needed - SSE event (timer_started) handles UI refresh for ALL participants
  }
  ```

  **3.4 Add HTML fragment endpoint in `RetroViewController`** (for HTMX SSE refresh):
  ```java
  @GetMapping("/retro/{retroId}/timer-fragment")
  @PreAuthorize("@participantService.canAccessRetro(#retroId)")  // Same pattern as other fragment endpoints (getColumnResponses, getRatingHistogram)
  public String getTimerFragment(@PathVariable UUID retroId, Model model, HttpServletRequest request) {
      RetroSession session = retroService.getSessionById(retroId);
      RetroStep currentStep = retroService.getCurrentStep(retroId);
      
      // Timer only renders when there's an active step with a timer
      TimerStateDto timerState = null;
      if (currentStep != null) {
          timerState = retroService.getTimerState(retroId);
      }
      
      model.addAttribute("timerState", timerState);  // null if no current step or no timer
      model.addAttribute("isFacilitator", participantService.isFacilitator(request, retroId));
      model.addAttribute("retroSession", session);
      return "fragments/components/timer-countdown :: content";
  }
  ```

  **Authorization pattern note**: Uses `@PreAuthorize("@participantService.canAccessRetro(#retroId)")` which is the same SpEL-based authorization pattern used by other view fragment endpoints like `getColumnResponses()` (line 226) and `getRatingHistogram()` (line 287). This is preferred over the `getParticipantForSession()` approach for view controllers.

  **Must NOT do**:
  - Do NOT add WebSocket endpoints
  - Do NOT return `HX-Trigger` headers (SSE events are sufficient for multi-user sync)
  - Do NOT refactor existing endpoints' authorization patterns

  **Parallelizable**: NO (depends on Task 2, Task 4 depends on this)

  **References**:

  **Pattern References**:
  - `src/main/java/direct/reflect/facilitator/facilitation/RetroApiController.java` - `startSession()` method - shows facilitator check pattern with `isFacilitator()` (use this pattern for pause/resume endpoints)
  - `src/main/java/direct/reflect/facilitator/facilitation/ParticipantService.java` - `getParticipantForSession()` method - throws `ParticipantNotFoundException` on unauthorized access
  - `src/main/java/direct/reflect/facilitator/web/RetroViewController.java` - `getColumnResponses()` method - shows `@PreAuthorize("@participantService.canAccessRetro(#retroId)")` annotation pattern AND model attribute setup for fragment endpoints
  - `src/main/java/direct/reflect/facilitator/web/RetroViewController.java` - `getRatingHistogram()` method - another example of `@PreAuthorize` on fragment endpoint

  **Authorization pattern clarification**:
  - `retroContentFragment()` does **NOT** use `@PreAuthorize` - it relies on internal `getParticipantForSession()` call
  - The new timer fragment endpoint (`getTimerFragment()`) should use `@PreAuthorize("@participantService.canAccessRetro(#retroId)")` to match `getColumnResponses()` and `getRatingHistogram()` which are the correct patterns for view fragment endpoints
  - Do NOT copy `retroContentFragment()`'s authorization approach
  
  **ParticipantService.canAccessRetro authorization reference** (VERIFIED):
  - File: `src/main/java/direct/reflect/facilitator/facilitation/ParticipantService.java:355-368`
  - Method signature: `public boolean canAccessRetro(UUID retroId)`
  - Uses `RequestContextHolder.getRequestAttributes()` to get current request (works with SpEL `@PreAuthorize`)
  - Delegates to `isParticipating(request, retroId)` which checks if user has a `Participant` record for the session
  - Returns `true` if participating, `false` otherwise (catches exceptions)

  **Why Each Reference Matters**:
  - `startSession()` shows `isFacilitator()` check pattern to copy for pause/resume API endpoints
  - `ParticipantService` shows the actual exception-throwing behavior (not null return)
  - `getColumnResponses()` and `getRatingHistogram()` show the exact `@PreAuthorize` annotation to use for the timer fragment endpoint

  **Acceptance Criteria**:

  **Manual Execution Verification**:
  - [ ] Start app: `mvn spring-boot:run -Dspring-boot.run.profiles=import`
  - [ ] Create session and start retro via browser
  - [ ] Get cookie for curl testing:
    - Open browser DevTools → Network tab
    - Refresh page, find any request to localhost:8080
    - Copy the full `Cookie` header value (may be `JSESSIONID=xxx` or `SESSION=xxx` depending on Spring config)
    - Use this exact value in curl commands with `-H "Cookie: <copied value>"`
  - [ ] Test timer JSON endpoint: `curl -H "Cookie: <copied value>" http://localhost:8080/api/retro/{id}/timer`
    - Should return JSON: `{"remainingSeconds": N, "isPaused": false, "state": "green"}`
  - [ ] Test timer fragment endpoint: `curl -H "Cookie: <copied value>" http://localhost:8080/retro/{id}/timer-fragment`
    - Should return HTML fragment with timer display
  - [ ] Test pause (as facilitator): `curl -X POST -H "Cookie: <copied value>" http://localhost:8080/api/retro/{id}/timer/pause`
    - Should return 200 OK
  - [ ] Test timer endpoint again: `isPaused` should be `true`
  - [ ] Test resume: `curl -X POST -H "Cookie: <copied value>" http://localhost:8080/api/retro/{id}/timer/resume`
    - Should return 200 OK

  **Commit**: YES
  - Message: `feat(timer): add REST and view endpoints for timer state and pause/resume`
  - Files: `RetroApiController.java`, `RetroViewController.java`

---

- [x] 4. Timer: Create frontend countdown component with SSE + JavaScript integration

  **What to do**:
  
  **IMPORTANT: Order of changes matters.** Add model attributes (4.1) BEFORE modifying templates (4.2-4.3) to avoid Thymeleaf evaluation errors.
  
  **4.1 Add `timerState` to BOTH controller methods in `RetroViewController` FIRST**:
  
  See step 4.1 later in this task for controller changes. Ensure these are done BEFORE template changes.
  
  **4.2 Create `timer-countdown.html` Thymeleaf fragment**:
  Location: `src/main/resources/templates/fragments/components/timer-countdown.html`
  
  This follows the same directory pattern as `multi-column-board.html` at `src/main/resources/templates/fragments/components/`.
  
  **Thymeleaf template resolution verification**: The path `fragments/components/timer-countdown` will resolve correctly because:
  - Existing components use same pattern: `fragments/components/multi-column-board.html`, `fragments/components/histogram-chart.html`
  - Thymeleaf prefix is `classpath:/templates/` (Spring Boot default)
  - The `th:replace="~{fragments/components/timer-countdown :: content}"` syntax matches existing usage
  
  ```html
  <div th:fragment="content"
       id="timer-container"
       th:attr="hx-get=@{/retro/{retroId}/timer-fragment(retroId=${retroSession.id})}"
       hx-trigger="sse:timer_paused from:body, sse:timer_started from:body, sse:step_advanced from:body"
       hx-swap="outerHTML">
      
      <!-- No timer for this step -->
      <div th:if="${timerState == null}" class="text-gray-500 text-sm">
          No time limit
      </div>
      
      <!-- Timer display -->
      <div th:if="${timerState != null}" class="flex items-center justify-center space-x-2">
          <!-- Timer Icon -->
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                    d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          
          <!-- Countdown Display (JS will update this) -->
          <span id="timer-display"
                th:data-remaining="${timerState.remainingSeconds()}"
                th:data-paused="${timerState.isPaused()}"
                th:data-state="${timerState.state()}"
                th:classappend="${timerState.state() == 'green'} ? 'text-green-600' : 
                                 (${timerState.state() == 'yellow'} ? 'text-yellow-600' : 
                                 (${timerState.state() == 'red'} ? 'text-red-600 font-bold' : 'text-gray-600'))"
                class="text-sm font-mono">
              <!-- Initial value from server, JS will take over -->
              <span th:text="${timerState.isPaused()} ? 'PAUSED' : 
                             T(String).format('%02d:%02d', ${timerState.remainingSeconds()} / 60, ${timerState.remainingSeconds()} % 60)">
                  05:00
              </span>
          </span>
          
          <!-- Pause/Resume Buttons (Facilitator only) -->
          <div th:if="${isFacilitator}" class="ml-2">
              <button th:if="${!timerState.isPaused()}"
                      th:attr="hx-post=@{/api/retro/{retroId}/timer/pause(retroId=${retroSession.id})}"
                      hx-swap="none"
                      class="text-xs px-2 py-1 bg-gray-200 hover:bg-gray-300 rounded"
                      title="Pause timer">
                  ⏸️
              </button>
              <button th:if="${timerState.isPaused()}"
                      th:attr="hx-post=@{/api/retro/{retroId}/timer/resume(retroId=${retroSession.id})}"
                      hx-swap="none"
                      class="text-xs px-2 py-1 bg-green-200 hover:bg-green-300 rounded"
                      title="Resume timer">
                  ▶️
              </button>
          </div>
      </div>
      
      <!-- JavaScript Countdown (inline, manages its own lifecycle) -->
      <script th:if="${timerState != null}" th:inline="javascript">
          (function() {
              // Prevent duplicate intervals on HTMX swap
              if (window.facilitatorTimerInterval) {
                  clearInterval(window.facilitatorTimerInterval);
                  window.facilitatorTimerInterval = null;
              }
              
              var display = document.getElementById('timer-display');
              if (!display) return;
              
              var remaining = parseInt(display.dataset.remaining);
              var isPaused = display.dataset.paused === 'true';
              
              function updateDisplay() {
                  var innerSpan = display.querySelector('span');
                  if (!innerSpan) return;
                  
                  if (remaining <= 0) {
                      innerSpan.textContent = '00:00';
                      display.className = 'text-sm font-mono text-red-600 font-bold animate-pulse';
                      if (window.facilitatorTimerInterval) {
                          clearInterval(window.facilitatorTimerInterval);
                          window.facilitatorTimerInterval = null;
                      }
                      return;
                  }
                  
                  var mins = Math.floor(remaining / 60);
                  var secs = remaining % 60;
                  innerSpan.textContent = String(mins).padStart(2, '0') + ':' + String(secs).padStart(2, '0');
                  
                  // Update color based on remaining time
                  if (remaining <= 30) {
                      display.className = 'text-sm font-mono text-red-600 font-bold';
                  } else if (remaining <= 120) {
                      display.className = 'text-sm font-mono text-yellow-600';
                  } else {
                      display.className = 'text-sm font-mono text-green-600';
                  }
              }
              
              // Only run countdown if not paused
              if (!isPaused && remaining > 0) {
                  window.facilitatorTimerInterval = setInterval(function() {
                      remaining--;
                      updateDisplay();
                  }, 1000);
              }
          })();
      </script>
  </div>
  ```
  
  **JS snippet note**: This is copy-paste ready JavaScript. Uses `var` for broader compatibility. Verify syntax by checking browser console for errors after implementation.

  **Non-facilitator view**: Non-facilitators see the same timer display (MM:SS or "PAUSED" text) but without the pause/resume buttons (`th:if="${isFacilitator}"` hides them). When paused, ALL participants see "PAUSED" text via the `timerState.isPaused()` check.

  **4.3 Replace static timer text in `retro.html`** (AFTER controller changes in 4.1):
  
  **EXACT LOCATION**: `src/main/resources/templates/fragments/retro.html` **lines 88-95** (inside the `th:if="${currentStep != null}"` block at line 82).
  
  Find and replace the static timer display:
  ```html
  <!-- FIND THIS (lines 88-95): -->
  <!-- Step Timer (clean version from controller) -->
  <div th:if="${stepDurationMinutes != null}" class="flex items-center justify-center space-x-2 text-sm text-gray-600 mb-4">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
      </svg>
      <span th:text="${stepDurationMinutes} + ' minutes for this step'">10 minutes for this step</span>
  </div>
  
  <!-- REPLACE WITH: -->
  <!-- Dynamic Timer Countdown Component -->
  <div th:replace="~{fragments/components/timer-countdown :: content}"></div>
  ```
  
  **IMPORTANT**: This replacement is INSIDE the `th:if="${currentStep != null}"` block (line 82), which means:
  - Timer component ONLY renders when there's an active step (not in lobby)
  - The timer component's "No time limit" fallback will show when `timerState == null` (step has no timer)
  - In lobby phase (`currentStep == null`), the entire center content area doesn't render, so timer doesn't show at all
  
  **Timer visibility note**: The timer component only renders meaningfully when `page == "retro"` (not lobby) and `currentStep != null`. In the lobby phase (`page == "lobby"`), the timer won't show because there's no active step. The component handles `timerState == null` gracefully by showing "No time limit".

  **Rendering/Swap Contract** (CRITICAL for HTMX stability):
  
  1. **Timer location in DOM**: The timer container (`id="timer-container"`) is placed **inside** the `inner-content` fragment of `retro.html`, in the header area where the static timer text currently lives.
  
  2. **Content refresh behavior**: When SSE event `step_advanced` fires, HTMX triggers `hx-get="/retro/{id}/content"` on `#retro-content`, which returns `fragments/retro :: inner-content`. This re-renders the entire inner-content including the timer container, resetting it with fresh `timerState` from `retroContentFragment()`.
  
  3. **Timer-specific refresh**: When SSE events `timer_paused` or `timer_started` fire, HTMX triggers `hx-get="/retro/{id}/timer-fragment"` on `#timer-container` only, which returns `fragments/components/timer-countdown :: content`. The `hx-swap="outerHTML"` replaces the entire `#timer-container` div with the fresh fragment.
  
  4. **ID stability**: The timer fragment's root element MUST be `id="timer-container"` to ensure:
     - SSE-triggered refreshes target the correct element
     - `outerHTML` swap replaces the exact container
     - JS countdown reinitializes correctly after swap (script re-executes)
  
  5. **JS interval cleanup**: The script uses `window.facilitatorTimerInterval` and clears any existing interval before creating a new one, preventing duplicate intervals after HTMX swaps.

  (4.1 is now the controller changes - see above)
  
  In `retroView()` method, add:
  ```java
  model.addAttribute("timerState", retroService.getTimerState(retroId));
  ```
  
  In `retroContentFragment()` method, add:
  ```java
  model.addAttribute("timerState", retroService.getTimerState(retroId));
  ```
  
  **Why both?** `retroView()` is for initial page load, `retroContentFragment()` is for SSE-triggered HTMX refreshes (on `step_advanced`). Both need `timerState` for the timer component to render correctly.
  
  **Model attribute contract for timer component** (CRITICAL):
  
  | Render Context | Required Attributes | Where Set |
  |---------------|---------------------|-----------|
  | Initial page load (`retroView()`) | `retroSession`, `timerState`, `isFacilitator` | `retroView()` - `retroSession` and `isFacilitator` already exist, ADD `timerState` |
  | Content refresh (`retroContentFragment()`) | `retroSession`, `timerState`, `isFacilitator` | `retroContentFragment()` - `retroSession` and `isFacilitator` already exist, ADD `timerState` |
  | Timer fragment endpoint (`getTimerFragment()`) | `retroSession`, `timerState`, `isFacilitator` | `getTimerFragment()` - sets all three (see Task 3.4) |
  
  **Note**: `retroSession` and `isFacilitator` are ALREADY present in `retroView()` and `retroContentFragment()` (verified in existing code). We only ADD `timerState`. The `th:replace` will work because all required attributes will exist.
  
  **4.2 Create `timer-countdown.html` Thymeleaf fragment** (AFTER model attributes added):

  **How SSE + JS Countdown Works Together**:
  1. **Initial render**: Server provides `timerState` with `remainingSeconds` via data attributes
  2. **JS countdown**: JavaScript reads data attributes and decrements display every second (client-side, for smooth UX)
  3. **SSE sync events**: When facilitator pauses/resumes, SSE event triggers HTMX to refresh the component
     - `sse:timer_paused` → `hx-get` fetches fresh HTML from `/retro/{id}/timer-fragment`
     - `sse:timer_started` (resume) → same refresh
     - `sse:step_advanced` → timer resets with new step's duration (existing event, already fires)
  4. **Duplicate interval prevention**: Script clears `window.timerInterval` before creating new one

  **Must NOT do**:
  - Do NOT play sound when timer expires (just visual pulse animation)
  - Do NOT auto-advance (expired state is just visual)
  - Do NOT add complex timer presets

  **Parallelizable**: YES (with Task 5, 6 after API is ready)

  **References**:

  **Pattern References**:
  - `src/main/resources/templates/fragments/retro.html` - Search for `stepDurationMinutes` to find the static timer display section to replace (contains "minutes for this step" text)
  - `src/main/resources/templates/fragments/retro.html` - Search for `th:if="${isFacilitator}"` to find the facilitator-only button pattern (Next button)
  - `src/main/resources/templates/fragments/components/multi-column-board.html` - Search for `hx-trigger="sse:` to find the SSE trigger pattern: `hx-trigger="..., sse:note_added from:body"` with `hx-get` to fetch HTML fragment
  - `src/main/java/direct/reflect/facilitator/web/RetroViewController.java` - `retroView()` method - add `timerState` model attribute here for initial page load
  - `src/main/java/direct/reflect/facilitator/web/RetroViewController.java` - `retroContentFragment()` method - add `timerState` model attribute here for SSE-triggered refreshes

  **Why Each Reference Matters**:
  - The `stepDurationMinutes` section shows exactly what to replace (static "X minutes for this step" text)
  - The facilitator-only button shows the `th:if="${isFacilitator}"` pattern for conditional UI
  - `multi-column-board.html` shows the exact pattern: SSE event triggers `hx-get` to fetch HTML fragment
  - Both controller methods need the model attribute for timer to render correctly

  **Acceptance Criteria**:

  **Manual Browser Verification** (no Playwright - use two browser windows):
  - [ ] Open browser window 1 as facilitator:
    - Navigate to: `http://localhost:8080/home`
    - Create new retro session
    - Start the retrospective (advance to a step with timer)
    - Verify: Timer shows in MM:SS format (e.g., "05:00")
    - Verify: Timer counts down every second
    - Verify: Pause button (⏸️) is visible
    - Click: Pause button
    - Verify: Timer shows "PAUSED" text
    - Verify: Timer stopped counting
    - Click: Resume button (▶️)
    - Verify: Timer resumes from paused position
    - Wait until timer reaches <30s
    - Verify: Timer text turns red and bold
  - [ ] Open browser window 2 (incognito) as non-facilitator participant:
    - Join the same session using session ID
    - Verify: Timer shows (synced with facilitator)
    - Verify: Pause/Resume buttons NOT visible
    - When facilitator pauses: Verify participant sees "PAUSED" (via SSE refresh)

  **Commit**: YES
  - Message: `feat(timer): add real-time countdown component with pause/resume UI`
  - Files: new `timer-countdown.html`, `retro.html`, `RetroViewController.java` (add model attributes to both methods)

---

- [x] 5. Input mechanism: Add 10-input limit validation per step

  **What to do**:
  
  **5.1 Add repository query to count participant's responses**:
  In `ParticipantResponseRepository.java`:
  ```java
  /**
   * Count responses submitted by a specific participant for a specific step.
   * Used to enforce 10-input limit per step for MULTI_COLUMN_BOARD.
   * 
   * Note: Includes explicit session constraint to match existing repository query style
   * (see countDistinctParticipantsBySessionAndStep which uses r.participant.session = :session).
   * While Participant has a composite key (participantId + session), explicit session
   * scoping is the established pattern in this repository.
   */
  @Query("SELECT COUNT(r) FROM ParticipantResponse r WHERE r.participant = :participant AND r.participant.session = :session AND r.retroStep = :step")
  Long countByParticipantSessionAndStep(@Param("participant") Participant participant, @Param("session") RetroSession session, @Param("step") RetroStep step);
  ```
  
  **Usage in ResponseService**: When calling this query, pass `participant`, `participant.getSession()`, and `step` to match the existing query pattern. This is slightly redundant but maintains consistency with other repository queries.
  
  **JPA Mapping Verification** (VERIFIED with exact file references):
  
  **File 1**: `src/main/java/direct/reflect/facilitator/facilitation/response/ParticipantResponse.java` (lines 45-50):
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns({
      @JoinColumn(name = "participant_id", referencedColumnName = "participant_id", nullable = false),
      @JoinColumn(name = "session_id", referencedColumnName = "session_id", nullable = false)
  })
  private Participant participant;
  ```
  
  **File 2**: `src/main/java/direct/reflect/facilitator/facilitation/Participant.java` (lines 9-12):
  ```java
  @Entity
  @Table(name = "participants")
  @IdClass(ParticipantId.class)
  public class Participant { ... }
  ```
  
  **File 3**: `src/main/java/direct/reflect/facilitator/facilitation/ParticipantId.java` (entire 51-line file):
  - Implements `Serializable`
  - Has fields: `UUID participantId`, `UUID session` (both parts of composite key)
  - Implements `equals()` and `hashCode()` using both fields
  
  **Conclusion**: `Participant` is a proper JPA entity with composite key (`@IdClass(ParticipantId.class)`). The `@ManyToOne @JoinColumns` mapping in `ParticipantResponse` creates a proper foreign key relationship. JPQL entity equality `r.participant = :participant` will use the composite key for comparison.

  **5.2 Create `InputLimitExceededException`**:
  Location: `src/main/java/direct/reflect/facilitator/common/exception/InputLimitExceededException.java`
  ```java
  package direct.reflect.facilitator.common.exception;

  /**
   * Thrown when a participant attempts to submit more than the allowed number of inputs per step.
   */
  public class InputLimitExceededException extends RuntimeException {
      private final long inputsSubmitted;
      private final int inputLimit;

      public InputLimitExceededException(long inputsSubmitted, int inputLimit) {
          super(String.format("Input limit exceeded. You have submitted %d of %d allowed inputs for this step.", 
              inputsSubmitted, inputLimit));
          this.inputsSubmitted = inputsSubmitted;
          this.inputLimit = inputLimit;
      }

      public long getInputsSubmitted() { return inputsSubmitted; }
      public int getInputLimit() { return inputLimit; }
  }
  ```

  **5.3 Add validation in `ResponseService.submitResponseInternal()`**:
  At the beginning of the method, after the null check and before any component-type-specific logic, add:
  ```java
  // Enforce 10-input limit for MULTI_COLUMN_BOARD (not for RATING_SCALE which has 1-per-participant)
  if (step.getComponentType() == ComponentType.MULTI_COLUMN_BOARD) {
      RetroSession session = participant.getSession();
      Long existingCount = responseRepository.countByParticipantSessionAndStep(participant, session, step);
      int inputLimit = 10;  // Could be made configurable in componentConfig later
      if (existingCount >= inputLimit) {
          throw new InputLimitExceededException(existingCount, inputLimit);
      }
  }
  ```
  **Why this placement?** The validation is before save, so it only checks submissions, not edits. Edits go through `updateResponse()` which is a separate method and won't trigger this validation.
  
  **Per-step 10-input limit semantics clarification**:
  - The limit is enforced **per stepId** when submitting via `/api/retro/{id}/step/{stepId}/response/column`
  - Each step in a stage has its own separate 10-input limit
  - Even though `RetroViewController.getColumnResponses()` displays responses **stage-wide** (for clustering/voting), the submission endpoint is **step-specific**
  - If a later step in the same stage also allows input (`capabilities.allowInput: true`), it gets its own fresh 10-input limit
  - This matches the user's requirement: "10-input limit is **per-step** (not per-phase)"

  **5.4 Handle exception in `RetroApiController.submitColumnResponse()`**:
  
  **CHOSEN APPROACH: Simple `alert()` via HTMX `hx-on::after-request`**
  
  This is the simplest approach that works with the existing `hx-swap="none"` pattern.
  
  Change return type from `ResponseEntity<Void>` to `ResponseEntity<String>`:
  ```java
  @PostMapping("/{retroId}/step/{stepId}/response/column")
  @PreAuthorize("hasAnyRole('USER', 'GUEST')")
  public ResponseEntity<String> submitColumnResponse(  // Changed return type to String for error body
          @PathVariable UUID retroId,
          @PathVariable Long stepId,
          @Valid @ModelAttribute ColumnResponseDto dto,
          HttpServletRequest httpRequest) {

      log.debug("Submitting column response for retro: {}, step: {}, column: {}",
          retroId, stepId, dto.columnId());

      try {
          responseService.submitResponse(retroId, stepId, dto, httpRequest);
          log.debug("Submitted column response for step: {}", stepId);

          // Success: return empty body with HX-Trigger header
          // Using .body("") for explicit empty string body with String return type
          return ResponseEntity.ok()
              .header("HX-Trigger", "responseSubmitted")
              .body("");

      } catch (InputLimitExceededException e) {
          log.debug("Input limit exceeded for retro {}: {}", retroId, e.getMessage());
          // Error: return error message as body (displayed via alert in HTMX handler)
          return ResponseEntity.status(HttpStatus.BAD_REQUEST)
              .body(e.getMessage());
      } catch (RetroSessionNotFoundException e) {
          log.debug("Session not found: {}", retroId);
          return ResponseEntity.notFound().build();
      } catch (Exception e) {
          log.error("Error submitting column response: ", e);
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
      }
  }
  ```
  
  **Return type clarification**: `ResponseEntity<String>` is used consistently. On success, return `.body("")` (empty string) to satisfy the String type while still allowing the `HX-Trigger` header to work. On error, return `.body(e.getMessage())` with the error message.

  **5.5 Modify form in `multi-column-board.html` to show error**:
  
  Change the existing form (around line 23-49) to handle errors:
  ```html
  <form th:attr="hx-post=@{/api/retro/{retroId}/step/{stepId}/response/column(retroId=${retroSession.id}, stepId=${currentStep.id})}"
        hx-swap="none"
        hx-trigger="submit"
        hx-on::after-request="if(event.detail.successful) { this.reset(); } else if(event.detail.xhr.status === 400) { alert(event.detail.xhr.responseText); }"
        class="bg-white p-2 rounded-lg border border-gray-300 hover:border-blue-400 hover:shadow-md transition-all">
  ```
  
  **Explanation**:
  - On success (`event.detail.successful`): reset the form (existing behavior)
  - On 400 error: show `alert()` with the error message from response body
  - The existing `hx-swap="none"` is preserved
  - This merges both behaviors in one `hx-on::after-request` handler

  **Must NOT do**:
  - Do NOT apply limit to RATING_SCALE (already has 1-per-participant logic in existing code)
  - Do NOT count edits as new inputs (validation is in `submitResponseInternal()`, edits use `updateResponse()`)
  - Do NOT add client-side validation only (server must enforce)
  - Do NOT use `log.warn` or `log.info` for debugging per AGENTS.md logging policy

  **Parallelizable**: YES (independent of timer work)

  **References**:

  **Pattern References**:
  - `src/main/java/direct/reflect/facilitator/facilitation/response/ParticipantResponseRepository.java` - `countDistinctParticipantsBySessionAndStep()` method - shows existing count query pattern to adapt for per-participant count
  - `src/main/java/direct/reflect/facilitator/common/exception/VoteLimitExceededException.java` - **VERIFIED EXISTS** - Exception class (25 lines) - copy this exact pattern for the new `InputLimitExceededException`:
    ```java
    // Reference pattern from VoteLimitExceededException.java
    public class VoteLimitExceededException extends RuntimeException {
        private final long votesUsed;
        private final int voteLimit;
        public VoteLimitExceededException(long votesUsed, int voteLimit) {
            super(String.format("Vote limit exceeded. You have used %d of %d votes.", votesUsed, voteLimit));
            this.votesUsed = votesUsed;
            this.voteLimit = voteLimit;
        }
        // getters...
    }
    ```
  - `src/main/java/direct/reflect/facilitator/facilitation/response/ResponseService.java` - `submitResponseInternal()` method - add validation at the beginning, after null checks but before component-type-specific logic
  - `src/main/java/direct/reflect/facilitator/facilitation/RetroApiController.java` - `toggleVote()` method - shows how to catch exception and return `ResponseEntity.status(BAD_REQUEST).body(e.getMessage())`
  - `src/main/resources/templates/fragments/components/multi-column-board.html` - Search for `<form` to find the form element - modify `hx-on::after-request` attribute
  - AGENTS.md "Logging Policy" - DO NOT add INFO/WARN logs for debugging, use DEBUG/TRACE

  **Why Each Reference Matters**:
  - Repository query shows existing count pattern to adapt for per-participant count
  - `VoteLimitExceededException` is the exact pattern to follow for the new exception
  - `submitResponseInternal()` is where validation should be added (before the save operation)
  - `toggleVote()` shows how to catch and return `ResponseEntity.status(BAD_REQUEST).body(e.getMessage())`
  - Form element shows exact location and current `hx-on::after-request` to modify
  - AGENTS.md logging policy prevents inappropriate log levels

  **Acceptance Criteria**:

  **Manual Browser Verification**:
  - [ ] Start app and navigate to MULTI_COLUMN_BOARD step (e.g., Mad Sad Glad)
  - [ ] Submit 10 sticky notes quickly using the form
  - [ ] Verify: All 10 notes appear in the column
  - [ ] Attempt to submit 11th note
  - [ ] Verify: Browser `alert()` appears with message containing "10" and "limit"
  - [ ] Verify: 11th note was NOT saved (count stays at 10)
  - [ ] Test RATING_SCALE is NOT affected:
    - Navigate to rating step (e.g., Happiness Histogram)
    - Submit rating (should work - no limit)
    - Change rating (should work - just updates existing)
    - Verify: No error about input limits

  **Commit**: YES
  - Message: `feat(input): add 10-input limit per step for MULTI_COLUMN_BOARD`
  - Files: `ParticipantResponseRepository.java`, new `InputLimitExceededException.java`, `ResponseService.java`, `RetroApiController.java`, `multi-column-board.html`

---

- [x] 6. Guided Facilitation: CSS fix to lock sidebar visible

  **What to do**:
  - In `retro.html` around line 43, modify the left sidebar div:
    - Current: `class="w-80 bg-gray-900 flex flex-col"`
    - Change to: `class="w-80 min-w-80 flex-shrink-0 bg-gray-900 flex flex-col"`
  
  **Explanation of CSS changes**:
  - `min-w-80`: Sets minimum width to 320px (20rem), prevents shrinking below this
  - `flex-shrink-0`: Prevents the flex container from shrinking this child element

  **Tailwind utility availability**: This project uses Tailwind CDN (`layout.html:12` - `https://cdn.tailwindcss.com`) which includes all standard utilities. Both `min-w-80` and `flex-shrink-0` are standard Tailwind utilities available in the CDN version.

  **Must NOT do**:
  - Do NOT add JavaScript
  - Do NOT change sidebar content or structure
  - Do NOT add responsive breakpoints (desktop fix only)
  - Do NOT use custom CSS (Tailwind utilities only)

  **Parallelizable**: YES (completely independent)

  **References**:

  **Pattern References**:
  - `src/main/resources/templates/fragments/retro.html:43` - Left sidebar div: `class="w-80 bg-gray-900 flex flex-col"`
  - `src/main/resources/templates/layout.html:12` - **VERIFIED Tailwind CDN include**: `<script src="https://cdn.tailwindcss.com"></script>`
    - Tailwind CDN includes ALL standard utilities including `min-w-80` and `flex-shrink-0`
    - These are part of Tailwind's default configuration

  **External References**:
  - Tailwind CSS: `min-w-80` = `min-width: 20rem` (320px)
  - Tailwind CSS: `flex-shrink-0` = `flex-shrink: 0`

  **Why Each Reference Matters**:
  - Line 43 shows exact element to modify
  - Line 12 of layout.html confirms CDN availability

  **Acceptance Criteria**:

  **Manual Browser Verification**:
  - [ ] Start app and create/start a retro session
  - [ ] Navigate to any step with content
  - [ ] Resize browser window to various widths (1200px, 1000px, 800px)
  - [ ] Verify: Left sidebar maintains 320px width at all sizes
  - [ ] Verify: Sidebar does NOT collapse or shrink below 320px
  - [ ] Verify: Center content area adjusts (may scroll) but sidebar stays fixed width

  **Commit**: YES
  - Message: `fix(ui): prevent sidebar collapse with flex-shrink-0 and min-w-80`
  - Files: `retro.html`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(timer): add pause tracking fields to RetroSession entity` | RetroSession.java | App starts, logs show no Hibernate errors |
| 2 | `feat(timer): add timer state calculation and pause/resume service methods` | RetroSessionService.java, TimerStateDto.java, RetroEvent.java, RetroSessionServiceTest.java | Unit tests pass |
| 3 | `feat(timer): add REST and view endpoints for timer state and pause/resume` | RetroApiController.java, RetroViewController.java | curl tests pass |
| 4 | `feat(timer): add real-time countdown component with pause/resume UI` | timer-countdown.html, retro.html, RetroViewController.java | Manual browser test |
| 5 | `feat(input): add 10-input limit per step for MULTI_COLUMN_BOARD` | Repository, Exception, Service, Controller, multi-column-board.html | Manual browser test |
| 6 | `fix(ui): prevent sidebar collapse with flex-shrink-0 and min-w-80` | retro.html | Manual browser test |

---

## Success Criteria

### Verification Commands
```bash
# Start application
mvn spring-boot:run -Dspring-boot.run.profiles=import

# Run tests
mvn test

# Check logs for errors
tail -f /tmp/facilitator.log | grep -i error
```

### Final Checklist
- [ ] Timer shows MM:SS countdown format
- [ ] Timer color changes: green → yellow → red
- [ ] Facilitator can pause/resume timer
- [ ] Non-facilitators see "PAUSED" text when timer is paused (no buttons)
- [ ] Timer state persists across page refresh
- [ ] SSE events (`timer_paused`, `timer_started`) sync timer state across all participants
- [ ] 10-input limit enforced for MULTI_COLUMN_BOARD
- [ ] RATING_SCALE not affected by input limit
- [ ] Error alert displayed when input limit exceeded
- [ ] Left sidebar maintains fixed 320px width
- [ ] All commits follow conventional commit format
- [ ] No Java compilation errors
- [ ] Logging follows AGENTS.md policy (DEBUG for debugging, ERROR for unexpected failures)
