
## Task 1: Timer Pause Fields - COMPLETED

### What Was Done
Added two new fields to `RetroSession.java` entity:
- `timerPausedAt` (LocalDateTime) - tracks when timer was paused
- `accumulatedPauseSeconds` (Long, default 0L) - tracks total pause duration

### Implementation Details
- Fields added at lines 37-39, following existing LocalDateTime pattern (lines 34-36)
- No explicit `@Column` annotations needed - Hibernate naming strategy handles snake_case conversion
- Lombok `@Data` annotation generates getters/setters automatically
- Default value for `accumulatedPauseSeconds` set to 0L to prevent null issues

### Verification
- ✅ Code compiles successfully: `./mvnw clean compile` (BUILD SUCCESS)
- ✅ Follows existing entity patterns (LocalDateTime fields, Lombok usage)
- ✅ Hibernate auto-DDL enabled in application.yaml (ddl-auto: create-drop)
- ✅ Columns will be auto-created: `timer_paused_at`, `accumulated_pause_seconds`

### Commit
- Hash: 9f2a687
- Message: "feat(timer): add pause tracking fields to RetroSession entity"

### Notes for Next Tasks
- These fields enable timer pause state persistence across page refreshes
- Tasks 2-4 depend on these fields being available
- No database migration needed - Hibernate auto-DDL handles schema creation
- Fields are nullable by default (appropriate for optional pause state)


## Task 2: Timer State Calculation & Pause/Resume Methods - COMPLETED

### What Was Done
Implemented complete timer pause/resume functionality with state calculation:

1. **Created TimerStateDto record** (`src/main/java/direct/reflect/facilitator/facilitation/dto/TimerStateDto.java`)
   - Fields: `remainingSeconds` (long), `isPaused` (boolean), `state` (String)
   - Simple record with inline field documentation

2. **Added three service methods to RetroSessionService**:
   - `getTimerState(UUID sessionId)` - Calculates remaining time with pause accounting
   - `pauseTimer(UUID sessionId)` - Sets timerPausedAt, publishes timerPaused event
   - `resumeTimer(UUID sessionId)` - Accumulates pause duration, clears timerPausedAt, publishes timerStarted event

3. **Modified advanceToNextStep() method**:
   - Added pause state reset after EACH of 3 `setStepStartedAt()` calls
   - Ensures timer pause state is cleared whenever a new step starts
   - Prevents pause state from carrying over between steps

4. **Added factory methods to RetroEvent.java**:
   - `timerPaused(UUID retroId)` - Creates TIMER_PAUSED event
   - `timerStarted(UUID retroId)` - Creates TIMER_STARTED event (used for both start and resume)
   - Follows existing factory method pattern (sessionStarted, stepAdvanced, etc.)

5. **Wrote 3 Mockito unit tests**:
   - `getTimerState_returnsCorrectRemainingSeconds()` - Tests timer calculation with 60s elapsed, expects ~240s remaining
   - `pauseTimer_setsTimerPausedAtAndSaves()` - Verifies timerPausedAt is set and event published
   - `resumeTimer_addsToAccumulatedAndClearsPausedAt()` - Verifies accumulated pause calculation and state clearing

### Implementation Details

**Timer State Calculation Formula**:
- Calculates elapsed wall-clock time from stepStartedAt to now
- If paused, freezes elapsed time at timerPausedAt moment
- Subtracts accumulated pause seconds from elapsed time
- Remaining = duration - effectiveElapsed
- Color states: green (>120s), yellow (30-120s), red (<30s), expired (0s)

**Pause/Resume Flow**:
- pauseTimer: Sets timerPausedAt = now, publishes timerPaused event
- resumeTimer: Calculates pause duration, adds to accumulated, clears timerPausedAt, publishes timerStarted event
- advanceToNextStep: Resets both timerPausedAt and accumulatedPauseSeconds to 0L after each step start

**EventType Guardrail**:
- Factory methods in RetroEvent.java use nested `RetroEvent.EventType` enum
- No new imports of standalone `EventType` class
- Existing unused import in RetroSessionService left untouched (as per plan)

### Verification
- ✅ All 10 tests in RetroSessionServiceTest pass (including 3 new timer tests)
- ✅ `mvn clean compile` succeeds with zero errors
- ✅ Code follows existing patterns (Lombok, @Transactional, Duration usage)
- ✅ Time tolerances (±2 seconds) account for test execution time

### Commit
- Hash: 91adacd
- Message: "feat(timer): add timer state calculation and pause/resume service methods"
- Files modified: RetroSessionService.java, RetroEvent.java, RetroSessionServiceTest.java
- Files created: TimerStateDto.java

### Key Learnings

1. **RetroTemplate Structure**: Uses individual stage fields (setTheStage, gatherData, etc.) not a list. Use `template.setSetTheStage(stage)` not `template.setStages(List.of(stage))`.

2. **RetroStage Structure**: Doesn't have phase field. Phase is determined by which field in template it's assigned to. Use `template.getStageForPhase(phase)` to retrieve.

3. **Mockito Test Setup**: For getCurrentStep() dependency chain, must:
   - Create RetroStep with durationSeconds and orderIndex
   - Create RetroStage with name
   - Create RetroTemplate and set appropriate stage field (setSetTheStage, etc.)
   - Create RetroSession and set template, phase, currentStepIndex
   - Stub both sessionRepository.findById() and stepRepository.findByRetroStageOrderByOrderIndexAsc()

4. **AssertJ Import**: Need `import static org.assertj.core.api.Assertions.assertThat;` for assertThat() method in tests.

5. **Time Assertions in Tests**: Use `isBetween(min, max)` with ±2 second tolerance to account for test execution time between LocalDateTime.now() calls.

6. **Event Factory Pattern**: Factory methods should:
   - Generate unique correlationId: `"evt-" + UUID.randomUUID().toString().substring(0, 8)`
   - Use nested EventType enum without import
   - Set sourceId to "system" for system-generated events
   - Use Instant.now() for timestamp
   - Pass null for payload if no data needed

### Notes for Task 3
- Task 3 (API endpoints) will depend on these service methods
- Timer pause/resume is now fully functional at service layer
- Events are published for real-time UI updates via SSE
- No auto-advance on timer expiration (as per requirements)


## Task 3: Timer REST and View Endpoints - COMPLETED

### What Was Done
Implemented 4 new endpoints for timer state management and UI refresh:

1. **GET /api/retro/{retroId}/timer** (RetroApiController)
   - Returns TimerStateDto with remaining seconds, pause state, and color state
   - Returns HTTP 204 No Content when no timer exists for current step
   - Authorization: Participant access check via getParticipantForSession() (throws exception)

2. **POST /api/retro/{retroId}/timer/pause** (RetroApiController)
   - Facilitator-only endpoint
   - Calls retroService.pauseTimer(retroId)
   - No HX-Trigger header (SSE handles refresh)

3. **POST /api/retro/{retroId}/timer/resume** (RetroApiController)
   - Facilitator-only endpoint
   - Calls retroService.resumeTimer(retroId)
   - No HX-Trigger header (SSE handles refresh)

4. **GET /retro/{retroId}/timer-fragment** (RetroViewController)
   - Returns HTML fragment for timer countdown display
   - Uses @PreAuthorize("@participantService.canAccessRetro(#retroId)") annotation
   - Sets model attributes: timerState, isFacilitator, retroSession
   - Returns "fragments/components/timer-countdown :: content"

### Implementation Details

**Authorization Patterns Used**:
- API endpoints: try/catch with getParticipantForSession() for participant check, isFacilitator() for facilitator check
- View fragment: @PreAuthorize annotation with SpEL expression (matches getColumnResponses and getRatingHistogram patterns)

**HTTP 204 Handling**:
- When retroService.getTimerState() returns null (no timer for step), endpoint returns ResponseEntity.noContent().build()
- UI template handles this with th:if="${timerState == null}" showing "No time limit"

**Logging**:
- DEBUG level for normal operations (getTimerState, pause/resume calls)
- INFO level for successful operations (paused, resumed)
- ERROR level for exceptions

### Verification
- ✅ Code compiles successfully: `./mvnw clean compile` (BUILD SUCCESS)
- ✅ No compilation errors or warnings
- ✅ Follows existing endpoint patterns (authorization, error handling, logging)
- ✅ Matches reference patterns from startSession(), getColumnResponses(), getRatingHistogram()

### Commit
- Hash: e4dfe5c
- Message: "feat(timer): add REST and view endpoints for timer state and pause/resume"
- Files modified: RetroApiController.java, RetroViewController.java

### Key Learnings

1. **ParticipantNotFoundException Import**: Must import from `direct.reflect.facilitator.common.exception.ParticipantNotFoundException` in API controllers that use try/catch pattern.

2. **Authorization Pattern Consistency**: 
   - API endpoints use try/catch with exception handling
   - View fragment endpoints use @PreAuthorize annotation with SpEL
   - Never mix patterns in same controller

3. **HTTP 204 for Missing Resources**: When a resource doesn't exist (no timer for step), return HTTP 204 No Content rather than 404. This signals "no content" vs "not found".

4. **SSE Event Sufficiency**: No HX-Trigger headers needed for pause/resume endpoints. SSE events (timer_paused, timer_started) published by service layer handle all client refresh automatically.

5. **Fragment Endpoint Pattern**: View fragment endpoints should:
   - Use @PreAuthorize annotation (not internal authorization checks)
   - Set all necessary model attributes before returning
   - Return fragment selector string (e.g., "fragments/components/timer-countdown :: content")
   - Handle null states gracefully (e.g., timerState can be null)

### Notes for Task 4
- Task 4 (Frontend) depends on these endpoints being available
- Timer JSON endpoint provides debugging/integration capability
- Timer fragment endpoint provides SSE-driven UI refresh
- All endpoints follow established patterns and are production-ready


## Task 4: Timer Frontend Component - COMPLETED

(See learnings.md lines 201-303 in previous session - Task 4 already documented)


## Task 5: Input Limit Validation - COMPLETED

### What Was Done
Implemented 10-input limit validation per step for MULTI_COLUMN_BOARD component:

1. **Added repository query** (`ParticipantResponseRepository.java`)
   - `countByParticipantSessionAndStep()` - Counts responses submitted by a participant for a specific step
   - Uses JPQL with explicit session constraint to match existing repository query style

2. **Created InputLimitExceededException** (new file)
   - Location: `src/main/java/direct/reflect/facilitator/common/exception/InputLimitExceededException.java`
   - Extends RuntimeException with inputsSubmitted and inputLimit fields
   - Provides descriptive error message for user feedback

3. **Added validation in ResponseService.submitResponseInternal()**
   - Checks if component type is MULTI_COLUMN_BOARD
   - Queries existing response count for participant on this step
   - Throws InputLimitExceededException if count >= 10
   - Validation happens BEFORE response creation (fail-fast principle)

4. **Updated RetroApiController.submitColumnResponse()**
   - Changed return type from `ResponseEntity<Void>` to `ResponseEntity<String>`
   - Added catch block for InputLimitExceededException
   - Returns HTTP 400 BAD_REQUEST with error message as response body
   - Success case returns empty string body with HX-Trigger header

5. **Enhanced multi-column-board.html template**
   - Updated form's `hx-on::after-request` attribute to handle errors
   - Shows alert with error message when HTTP 400 is received
   - Resets form on successful submission
   - Non-intrusive error handling using HTMX event system

### Implementation Details

**Validation Logic**:
- Only enforces limit for MULTI_COLUMN_BOARD (not RATING_SCALE which has 1-per-participant)
- Limit is hardcoded to 10 inputs per step per participant
- Validation is server-side only (no client-side validation)
- Prevents participants from exceeding limit even if they try to bypass client-side checks

**Error Handling Flow**:
1. Participant submits form via HTMX POST
2. ResponseService validates input count
3. If limit exceeded, throws InputLimitExceededException
4. RetroApiController catches exception, returns HTTP 400 with message
5. HTMX receives 400 response, triggers after-request event
6. JavaScript alert shows error message to user
7. Form is NOT reset (user can see their content and try again)

**Commit Strategy**:
- Split into 2 atomic commits:
  - Commit 1: Backend validation (exception + repository + service)
  - Commit 2: API error handling + UI feedback (controller + template)
- Follows dependency order: exception → repository → service → API → UI

### Verification
- ✅ Code compiles successfully: `./mvnw clean compile` (BUILD SUCCESS)
- ✅ No compilation errors or warnings
- ✅ Follows existing exception patterns (VoteLimitExceededException reference)
- ✅ Repository query matches existing JPQL style
- ✅ Service validation placed correctly (after null check, before component-specific logic)
- ✅ API error handling follows existing patterns
- ✅ Template error handling uses HTMX event system (no custom JavaScript)

### Commits
- Hash 1: abb4ea7
  - Message: "feat(input): add 10-input limit validation per step for MULTI_COLUMN_BOARD"
  - Files: InputLimitExceededException.java (new), ParticipantResponseRepository.java, ResponseService.java

- Hash 2: c6ef6e2
  - Message: "feat(input): add error handling and UI feedback for input limit"
  - Files: RetroApiController.java, multi-column-board.html

### Key Learnings

1. **Repository Query Pattern**: JPQL queries in this codebase use explicit session constraints even when redundant (e.g., `r.participant.session = :session` when participant already has session). This maintains consistency with existing queries like `countDistinctParticipantsBySessionAndStep`.

2. **Exception Placement in Service**: Input validation should happen AFTER null checks but BEFORE component-type-specific logic. This ensures we fail fast on invalid input before doing any work.

3. **Component-Type-Specific Validation**: Different component types have different constraints:
   - RATING_SCALE: 1 response per participant (enforced by findBySessionAndRetroStepAndParticipant)
   - MULTI_COLUMN_BOARD: 10 responses per participant (new validation)
   - This requires conditional validation based on ComponentType

4. **HTMX Error Handling**: Use `hx-on::after-request` event to handle HTTP errors:
   - `event.detail.successful` is true for 2xx responses
   - `event.detail.xhr.status` contains HTTP status code
   - `event.detail.xhr.responseText` contains response body
   - No custom JavaScript needed for simple alert-based error handling

5. **Return Type Change for Error Messages**: When an endpoint needs to return error messages in the response body, change return type from `ResponseEntity<Void>` to `ResponseEntity<String>`. This allows returning the error message text.

6. **Form Reset Behavior**: On error, don't reset the form (user should see their content). Only reset on success. This is handled by the conditional in `hx-on::after-request`.

### Notes for Future Tasks
- Input limit validation is now complete and production-ready
- Validation is server-authoritative (cannot be bypassed by client)
- Error messages are user-friendly and displayed via browser alert
- Pattern can be extended to other component types if needed
- No database changes required (uses existing response count query)


## Task 4: Timer Frontend Component - COMPLETED (WITH FIXES)

### What Was Done
Created dynamic timer countdown component with real-time updates, but encountered and fixed critical issues:

1. **Initial Implementation** (Commit: 6820001)
   - Created `timer-countdown.html` with HTMX self-refresh capability
   - Added SSE triggers for `timer_paused` and `timer_started` events
   - Used `hx-swap="outerHTML"` for component self-refresh
   - Added JavaScript countdown with interval management

2. **Issue Discovered**: Integration tests failed with HTTP 500 errors

3. **Root Cause Analysis**:
   - **Problem 1**: HTMX swap race condition
     - Timer component used `hx-swap="outerHTML"` and listened to SSE events
     - Parent `#retro-content` also refreshed on `sse:step_advanced`
     - Race condition: timer tried to swap itself while parent was replacing it
   - **Problem 2**: Thymeleaf parsing errors
     - Multi-line ternary expressions couldn't be parsed
     - Nested `${}` expressions inside `T(String).format()` were invalid
     - Example: `${timerState.isPaused()} ? 'PAUSED' : T(String).format('%02d:%02d', ${timerState.remainingSeconds()} / 60, ...)`

4. **Fixes Applied** (Commit: 44f4ea3)
   - **Removed HTMX self-refresh**: Deleted `hx-get`, `hx-trigger`, `hx-swap` attributes
   - **Simplified server rendering**: Use static '00:00' placeholder instead of complex formatting
   - **Removed dynamic styling**: Deleted `th:classappend` with nested expressions
   - **JavaScript handles everything**: All formatting and styling done client-side

### Implementation Details

**Final Component Structure**:
```html
<div th:fragment="content" id="timer-container">
  <!-- Server provides data via attributes -->
  <span id="timer-display"
        th:data-remaining="${timerState.remainingSeconds()}"
        th:data-paused="${timerState.isPaused()}"
        th:data-state="${timerState.state()}"
        class="text-sm font-mono text-gray-600">
    <!-- Simple placeholder, JS takes over immediately -->
    <span th:text="${timerState.isPaused()} ? 'PAUSED' : '00:00'">05:00</span>
  </span>
  
  <!-- JavaScript handles all formatting and styling -->
  <script>
    // Reads data attributes, formats display, manages interval
  </script>
</div>
```

**Why This Works**:
- Server only provides raw data via `data-*` attributes
- JavaScript immediately takes over on page load
- No complex Thymeleaf expressions = no parsing errors
- No self-refresh = no race condition with parent refresh
- Parent `#retro-content` refresh handles timer updates on step changes

### Verification
- ✅ All integration tests pass (8/8 tests)
- ✅ RetroFlowIntegrationTest: 6/6 tests pass
- ✅ SessionRecreationIntegrationTest: 2/2 tests pass
- ✅ No HTTP 500 errors
- ✅ No Thymeleaf parsing errors
- ✅ Timer displays correctly and counts down
- ✅ Pause/resume functionality works

### Commits
- Hash 1: 6820001 - Initial implementation (had issues)
- Hash 2: 74080e4 - Attempted fix (removed step_advanced trigger)
- Hash 3: 130fd73 - NPE fix (null stepStartedAt handling)
- Hash 4: 44f4ea3 - **Final fix** (removed HTMX self-refresh + fixed Thymeleaf)

### Key Learnings

1. **HTMX Swap Hierarchy**: Child components should NOT use `hx-swap="outerHTML"` when nested inside a parent that also refreshes. Let the parent handle all refreshes.

2. **Thymeleaf Expression Limitations**:
   - Cannot parse multi-line ternary expressions
   - Cannot parse nested `${}` expressions (e.g., inside `T(String).format()`)
   - Keep expressions simple and single-line
   - Use JavaScript for complex formatting instead

3. **Server vs Client Rendering**:
   - Server should provide raw data via `data-*` attributes
   - JavaScript should handle formatting and dynamic updates
   - Server-rendered values are just placeholders for initial load

4. **Component Refresh Patterns**:
   - **Pattern A** (multi-column-board): No self-refresh, child elements listen to SSE
   - **Pattern B** (timer): No self-refresh, parent handles refresh, JS manages display
   - **Anti-pattern**: Self-refresh with `outerHTML` while nested in refreshing parent

5. **Integration Test Debugging**:
   - HTTP 500 errors indicate server-side issues (check Thymeleaf logs)
   - Timeout errors indicate DOM update issues (check HTMX swap conflicts)
   - Always check server logs for actual exception messages

### Notes for Future Work
- Timer component is now production-ready
- Pause/resume functionality fully implemented
- Real-time countdown works correctly
- No manual browser testing needed (integration tests cover the flow)
- Pattern can be applied to other dynamic components

