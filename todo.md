# Facilitator - Development Roadmap

**Last Updated**: 2025-12-24
**Source of Truth**: Notion User Stories Database (a2d07350-84f9-41f3-ac64-fc4ed68f7bdd)

---

## 🔴 CRITICAL BLOCKERS

### Spring Boot 4.0 Migration - Integration Test Failures

**Status**: 🔴 **BLOCKED**
**Date Added**: 2025-12-05

**Problem**: 7 out of 92 integration tests failing after Spring Boot 4.0 and Java 25 migration.

**Error Pattern 1 - Session Creation Timeouts** (4 tests):
- Test clicks "Create Session" button but HTMX redirect never happens
- Timeout at `BaseIntegrationTest.createRetroSession:320` after 3000ms
- **Critical Finding**: POST `/api/retro/create` never reaches controller during tests
- Logs show "Session creation form found" but no "CREATE REQUEST START" logs

**Error Pattern 2 - Redis Context Pollution** (3 tests):
- ApplicationContext fails to start bean 'redisMessageListenerContainer'
- Only happens in batch test runs (not in isolation)
- Suggests test context caching/pollution

**Next Steps**:
1. Start application manually: `mvn spring-boot:run -Dspring-boot.run.profiles=import`
2. Test session creation via browser
3. Check browser console for JavaScript errors
4. Verify WebJar paths are correct for Spring Boot 4.0
5. Test Redis serialization with Jackson 3

**Files to Investigate**:
- src/main/java/direct/reflect/facilitator/facilitation/RetroApiController.java (RetroApiController.java:45-83)
- src/main/java/direct/reflect/facilitator/common/config/RedisConfig.java
- src/test/java/direct/reflect/facilitator/integration/BaseIntegrationTest.java:320

---

## 📊 MVP Prioritization Analysis - All 15 Notion User Stories

### Summary Statistics
- **Total User Stories**: 15
- **High Priority**: 12 stories (80%)
- **Medium Priority**: 2 stories (13%)
- **Low Priority**: 1 story (7%)
- **Total BDD Scenarios**: 233 scenarios
- **Implemented Scenarios**: ~75 scenarios (32%)
- **Missing Scenarios**: ~158 scenarios (68%)

### MVP Recommendation Matrix

| User Story | Priority | Scenarios | Implemented | MVP Status | Complexity |
|------------|----------|-----------|-------------|------------|------------|
| Five Step Flow | High | 25 | 20 (80%) | ✅ DONE | Low |
| Input Mechanism | High | 25 | 18 (72%) | 🟡 IMPORTANT | Low |
| Numeric Rating Input | High | 6 | 6 (100%) | ✅ DONE | Low |
| Voting Mechanism | High | 35 | 5 (14%) | 🔴 CRITICAL | Medium |
| Timer | High | 14 | 4 (29%) | 🔴 CRITICAL | Medium |
| Smart Improvement Story | High | 19 | 0 (0%) | 🔴 CRITICAL | High |
| Action Item Management | High | 4 | 0 (0%) | 🔴 CRITICAL | Medium |
| Guided Facilitation | High | 25 | 3 (12%) | 🟡 IMPORTANT | Medium |
| Customized Length | High | 13 | 0 (0%) | 🟡 IMPORTANT | Medium |
| Forced to Next Step | High | 14 | 4 (29%) | 🟡 IMPORTANT | Low |
| Visual Clue Stage | High | 16 | 3 (19%) | 🟢 NICE-TO-HAVE | Low |
| Clustering & Grouping | High | 4 | 0 (0%) | 🟡 IMPORTANT | High |
| Format Catalog | Medium | 23 | 1 (4%) | 🟢 NICE-TO-HAVE | High |
| Anonymous Login | Medium | 7 | 4 (57%) | 🟢 NICE-TO-HAVE | Low |
| Statistical Calculations | Low | 3 | 0 (0%) | 🟢 NICE-TO-HAVE | Low |

---

## 📋 User Story Implementation Status

### 1. Five Step Flow (Priority: High, 25 scenarios) ✅ MOSTLY COMPLETE

**As a**: Facilitator
**I want**: Retrospective structured in 5 proven phases
**So that**: We follow Derby & Larsen best practices for effective retrospectives

**Notion Priority**: High
**Status**: In progress
**MVP Classification**: ✅ **DONE** - Core structure implemented
**Implementation Complexity**: Low (mostly complete)
**Dependencies**: None

#### Implementation Status: 80% (20 of 25 scenarios)

**✅ Implemented Scenarios (20)**:
- [x] 5 RetroPhase enum values (SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO)
- [x] Phase progression logic (RetroPhase.next() method)
- [x] 34-step CSV configuration covering all 5 phases
- [x] Phase-based navigation in RetroSessionService
- [x] SSE events for phase transitions (SESSION_STARTED, STEP_ADVANCED)
- [x] Basic 5-circle progress indicator in UI
- [x] Phase states tracked (CREATED, LOBBY, active phases, COMPLETED, PAUSED, ABANDONED)
- [x] Active phase validation (isActivePhase() method)
- [x] Most divergent/convergent step patterns (brainstorm → vote → discuss)

**❌ Missing Scenarios (5)**:
- [ ] Explicit divergent/convergent step markers in UI
- [ ] Phase timing breakdown display (show estimated time per phase)
- [ ] Phase completion validation (ensure minimum participation before advancing)
- [ ] Phase-specific help tooltips (contextual guidance for each phase)
- [ ] Phase summary screen (recap of each phase before advancing)

**Why Mostly Complete**: The 5-phase structure is fully operational with proper enum states, progression logic, and CSV configuration. Missing features are UX enhancements, not blocking functionality.

**Files Already Implemented**:
- `RetroPhase.java` - Complete with all phases and transitions
- `RetroSessionService.java` - Phase advancement logic
- `retrospective_steps.csv` - 34 steps across 5 phases
- `retro.html` - 5-circle progress indicator

**Files to Modify** (for remaining 5 scenarios):
- `retro.html` - Add divergent/convergent indicators, phase timing, summary screens
- `RetroStep.java` - Add divergent/convergent boolean flags

**Estimated Effort**: 1-2 days for remaining polish

---

### 2. Input Mechanism (Priority: High, 25 scenarios) 🟡 MOSTLY COMPLETE

**As a**: Team member
**I want**: To submit responses in various formats (text, categories, ratings)
**So that**: I can participate effectively in all retrospective activities

**Notion Priority**: High
**Status**: In progress
**MVP Classification**: 🟡 **IMPORTANT** - Core working, missing some capabilities
**Implementation Complexity**: Low (mostly complete)
**Dependencies**: None

#### Implementation Status: 72% (18 of 25 scenarios)

**✅ Implemented Scenarios (18)**:
- [x] MULTI_COLUMN_BOARD component (1-N columns)
- [x] Card submission with HTMX
- [x] Real-time updates via SSE (NOTE_ADDED events)
- [x] Privacy controls (isVisible flag for PRIVATE → PUBLIC reveal)
- [x] Edit functionality for own cards (inline editing)
- [x] RATING_SCALE component (1-10 numeric input)
- [x] Optional comment field for ratings
- [x] HISTOGRAM_CHART visualization
- [x] Category-specific responses (column-based)
- [x] Response data stored as JSONB
- [x] Character limit enforcement (maxLength capability)
- [x] Placeholder text per column
- [x] Anonymous submission (no author shown until revealed)
- [x] Facilitator reveal control (manual PUBLIC switch)
- [x] Response counting for participation tracking
- [x] Response editing with timestamps (editedAt field)
- [x] Vote storage in response data
- [x] Participant-specific response filtering

**❌ Missing Scenarios (7)**:
- [ ] Rich text formatting (bold, italic, bullet lists)
- [ ] Emoji picker integration
- [ ] Card templates (pre-filled common responses)
- [ ] Bulk import/export for responses
- [ ] Response drafts (save without submitting)
- [ ] Response length indicator (show "X/500 characters")
- [ ] Keyboard shortcuts for quick submission (Ctrl+Enter)

**Why Important for MVP**: Core input mechanisms work perfectly. Missing features are productivity enhancements, not blocking.

**Files Already Implemented**:
- `multi-column-board.html` (191 lines) - Card-based input
- `rating-scale.html` - Numeric ratings
- `histogram-chart.html` - Visualization
- `ResponseService.java` - Polymorphic submitResponse()
- `ParticipantResponse.java` - JSONB storage
- `ColumnResponseDto.java`, `RatingResponseDto.java` - Type-safe DTOs

**Files to Modify** (for remaining 7 scenarios):
- `multi-column-board.html` - Add rich text toolbar, emoji picker, character counter
- `ResponseService.java` - Add draft save functionality
- Create: `response-import-export.html` - Bulk operations UI

**Estimated Effort**: 2-3 days for enhancements

---

### 3. Numeric Rating Input (Priority: High, 6 scenarios) ✅ COMPLETE

**As a**: Team member
**I want**: To rate my experience on a 1-10 scale
**So that**: We can quantify team sentiment

**Notion Priority**: High
**Status**: To do (marked, but actually COMPLETE)
**MVP Classification**: ✅ **DONE** - Fully implemented
**Implementation Complexity**: Low
**Dependencies**: None

#### Implementation Status: 100% (6 of 6 scenarios)

**✅ All Scenarios Implemented (6)**:
- [x] Scenario 1: Display 1-10 rating scale with radio buttons
- [x] Scenario 2: Submit rating with optional comment
- [x] Scenario 3: Rating stored in responseData as integer
- [x] Scenario 4: Ratings aggregate into histogram visualization
- [x] Scenario 5: Comments displayed alongside ratings
- [x] Scenario 6: Real-time rating submission via HTMX

**Why Complete**: RATING_SCALE component fully functional with all features. Used in Happiness Histogram and ROTI formats.

**Files Already Implemented**:
- `rating-scale.html` - Radio button input (1-10 scale)
- `histogram-chart.html` - SVG visualization
- `RatingResponseDto.java` - Type-safe DTO
- `ResponseService.java` - Rating submission logic
- `retrospective_steps.csv` - Steps 2, 8 use RATING_SCALE

**No Changes Needed**: This story is complete.

---

### 4. Voting Mechanism (Priority: High, 35 scenarios) 🔴 CRITICAL

**As a**: Team member
**I want**: To vote on team members' responses
**So that**: We can identify which items to focus on

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🔴 **CRITICAL** - Core retrospective activity
**Implementation Complexity**: Medium (UI work, no new backend logic needed)
**Dependencies**: None (voting backend already exists)

#### Implementation Status: 14% (5 of 35 scenarios)

**✅ Implemented Scenarios (5)**:
- [x] Scenario 3: Vote allocation is fixed and format-specific (numberOfVotes in CSV)
- [x] Scenario 4: Dot voting allows vote distribution (toggleVote() in ResponseService)
- [x] Scenario 18: Participants cannot exceed vote allocation (VoteLimitExceededException)
- [x] Scenario 19: All participants have equal voting power (no privileged voting)
- [x] Scenario 31: Limited vote budget allocation (enforced in ResponseService:262)

**❌ Missing Scenarios (30)** - Organized by Theme:

**Vote Period Management (8 scenarios)**:
- [ ] Scenario 6: Voting period has countdown timer
- [ ] Scenario 8: Facilitator can start voting period
- [ ] Scenario 9: Facilitator can stop voting period early
- [ ] Scenario 10: Voting automatically closes when timer expires
- [ ] Scenario 13: Participants can only change votes during active period
- [ ] Scenario 14: Votes locked after voting period ends
- [ ] Scenario 23: Voting status clearly indicated
- [ ] Scenario 29: Visual notification when voting starts/ends

**Vote Visualization (7 scenarios)**:
- [ ] Scenario 17: Vote counts displayed with dots and numbers (●●● 3)
- [ ] Scenario 27: Vote count shown with voter attribution
- [ ] Scenario 31: Limited vote budget allocation UI (show "X votes remaining")
- [ ] Scenario 32: Vote redistribution within budget UI
- [ ] Scenario 33: Stack multiple votes on single item UI
- [ ] Scenario 34: Real-time vote tally with budget constraints
- [ ] Scenario 35: Vote budget enforcement across formats UI

**Vote Sorting & Display (3 scenarios)**:
- [ ] Scenario 15: Items sorted by vote count
- [ ] Scenario 16: Tied items marked and sorted chronologically
- [ ] Scenario 24: Zero votes is valid outcome (show "0" not blank)

**Vote Attribution (2 scenarios)**:
- [ ] Scenario 12: Participants can see who voted for what
- [ ] Scenario 25: Voting synchronizes across all participants (SSE event)

**Other Missing (10 scenarios)**:
- [ ] Scenarios 1-2: Voting available in phases 2, 3, 4 only (UI toggle)
- [ ] Scenarios 5, 7: Voting period is format-specific
- [ ] Scenario 11: Live vote counts visible to all (real-time UI update)
- [ ] Scenario 20: Voting results persist across phases
- [ ] Scenario 21: Each phase can have independent voting
- [ ] Scenario 22: Facilitator cannot override vote counts
- [ ] Scenario 26: Highest-voted items clearly identifiable (highlighting)
- [ ] Scenario 28: Voting period duration is format-dependent

**Why Critical for MVP**: Voting determines which topics get discussed and which actions get prioritized. Without visible vote counts, sorted results, and vote attribution, retrospectives lose their democratic prioritization mechanism. Participants need to see "3 people voted for this issue" to build consensus.

**Files to Create**:
- None (backend exists)

**Files to Modify**:
- `multi-column-board.html:112-119` - Enhance vote button UI with dots and count
- `retro.html` - Add voting period controls for facilitator
- `ResponseService.java` - Add getVoteAttribution() query
- `RetroApiController.java` - Add startVoting/stopVoting endpoints
- Create new template fragment: `voting-controls.html`

**Estimated Effort**: 2-3 days (mostly UI work)

---

### 5. Timer (Priority: High, 14 scenarios) 🔴 CRITICAL

**As a**: Facilitator
**I want**: Countdown timer for each step
**So that**: Participants know how much time remains and we stay on schedule

**Notion Priority**: High
**Status**: In progress
**MVP Classification**: 🔴 **CRITICAL** - Essential for time management
**Implementation Complexity**: Medium (UI timer + auto-advancement)
**Dependencies**: None

#### Implementation Status: 29% (4 of 14 scenarios)

**✅ Implemented Scenarios (4)**:
- [x] Scenario 4: Timer duration stored in CSV (durationSeconds field)
- [x] Scenario 8: Timer tracking logic (RetroSession.stepStartedAt field)
- [x] Scenario 10: Backend validation (RetroSessionService.timerHasExpired())
- [x] Scenario 14: TIMER_EXPIRES advancement trigger enum

**❌ Missing Scenarios (10)** - Organized by Theme:

**Timer Display (4 scenarios)**:
- [ ] Scenario 1: MM:SS countdown display visible to all participants
- [ ] Scenario 2: Timer shows time remaining, not elapsed time
- [ ] Scenario 3: Color states (green > 50% remaining, yellow 20-50%, red < 20%)
- [ ] Scenario 11: Timer synchronized across all participants (SSE updates)

**Timer Controls (3 scenarios)**:
- [ ] Scenario 5: Facilitator can pause timer
- [ ] Scenario 6: Facilitator can resume timer
- [ ] Scenario 7: Facilitator can extend time (+1 min, +5 min buttons)

**Auto-Submit Behavior (3 scenarios)**:
- [ ] Scenario 9: Auto-submit when timer expires (save work in progress)
- [ ] Scenario 12: 30-second warning notification before auto-submit
- [ ] Scenario 13: Grace period (10 seconds) to finish typing after warning

**Why Critical for MVP**: Without visible countdown, participants don't know how much time they have, leading to rushed submissions or time wastage. Timer color states provide at-a-glance awareness. Auto-submit ensures retrospective stays on schedule.

**Files Already Implemented**:
- `RetroStep.java` - durationSeconds field
- `RetroSessionService.java` - timerHasExpired() logic
- `AdvancementTrigger.java` - TIMER_EXPIRES enum
- `RetroSession.java` - stepStartedAt tracking

**Files to Create**:
- `src/main/resources/templates/fragments/components/countdown-timer.html` - Timer UI component
- `src/main/java/direct/reflect/facilitator/scheduling/TimerService.java` - Timer state management

**Files to Modify**:
- `retro.html` - Add timer div in header (always visible)
- `RetroApiController.java` - Add `/api/retro/{retroId}/timer/pause` and `/resume` endpoints
- `EventService.java` - Add TIMER_WARNING event (30-second warning)
- `ResponseService.java` - Add autosaveResponse() for graceful auto-submit

**Estimated Effort**: 3-4 days (Timer UI, SSE sync, auto-save logic)

---

### 6. Smart Improvement Story (Priority: High, 19 scenarios) 🔴 CRITICAL

**As a**: Facilitator
**I want**: AI-assisted service to help formulate SMART improvement goals
**So that**: We increase the chance of successful improvement

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🔴 **CRITICAL** - Core value proposition
**Implementation Complexity**: High (new AI integration, prompt engineering)
**Dependencies**: Voting Mechanism (to identify highest-voted item)

#### Implementation Status: 0% (0 of 19 scenarios)

**❌ All Scenarios Missing (19)**:

**AI Service Integration (6 scenarios)**:
- [ ] Scenario 1: AI activates automatically in phase 4
- [ ] Scenario 2: AI analyzes all retrospective inputs with emphasis on phase 4
- [ ] Scenario 8: No editing allowed - only accept or regenerate
- [ ] Scenario 9: AI considers only current retrospective data
- [ ] Scenario 17: Goal cannot proceed if no votes in phase 4
- [ ] Scenario 18: AI service automatically retries on failure

**SMART Goal Generation (6 scenarios)**:
- [ ] Scenario 3: AI generates user story from highest-voted action item
- [ ] Scenario 4: AI enforces all SMART criteria (Specific, Measurable, Achievable, Relevant, Time-bound)
- [ ] Scenario 11: BDD acceptance criteria in proper Given-When-Then format
- [ ] Scenario 12: User story includes standard attributes (As a...I want...So that...)
- [ ] Scenario 13: AI focuses on highest-voted item from phase 4
- [ ] Scenario 14: AI handles tied votes appropriately (ask facilitator or pick first)

**User Workflow (5 scenarios)**:
- [ ] Scenario 5: Single improvement goal presented to all participants
- [ ] Scenario 6: Facilitator can accept the generated goal
- [ ] Scenario 7: Facilitator can regenerate the goal
- [ ] Scenario 15: All participants see AI progress and results (loading state, final output)
- [ ] Scenario 16: Regeneration produces different but valid alternatives

**Data Persistence (2 scenarios)**:
- [ ] Scenario 10: Generated goal saved as retrospective output
- [ ] Scenario 19: Single goal per retrospective (no multiple goals)

**Why Critical for MVP**: This is the killer feature that differentiates Facilitator from basic retro tools. It transforms vague action items ("improve communication") into concrete, trackable goals with acceptance criteria. Without this, retrospectives end with good intentions but no accountability.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/ai/SmartGoalService.java` - AI orchestration
- `src/main/java/direct/reflect/facilitator/ai/AnthropicClient.java` - Claude API integration
- `src/main/java/direct/reflect/facilitator/ai/dto/SmartGoalRequest.java` - Request DTO
- `src/main/java/direct/reflect/facilitator/ai/dto/SmartGoalResponse.java` - Response DTO
- `src/main/resources/templates/fragments/components/smart-goal-generator.html` - UI component
- `src/main/resources/prompts/smart-goal-system-prompt.txt` - Claude system prompt

**Files to Modify**:
- `RetroApiController.java` - Add `/api/retro/{retroId}/generate-smart-goal` endpoint
- `ResponseService.java` - Add getHighestVotedResponse() query
- `pom.xml` - Add Anthropic SDK dependency

**Estimated Effort**: 5-7 days (AI integration, prompt engineering, UI, testing)

---

### 7. Action Item Management (Priority: High, 4 scenarios) 🔴 CRITICAL

**As a**: Team member
**I want**: To create structured action items with clear owners and deadlines
**So that**: Our retrospective decisions lead to real change

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🔴 **CRITICAL** - Core retrospective outcome
**Implementation Complexity**: Medium (new entity, CRUD operations)
**Dependencies**: None

#### Implementation Status: 0% (0 of 4 scenarios)

**❌ All Scenarios Missing (4)**:
- [ ] Scenario 1: Create structured action item (WHAT, WHO, WHEN fields)
- [ ] Scenario 2: Owner assignment and validation
- [ ] Scenario 3: Action specificity validation (reject vague actions like "improve communication")
- [ ] Scenario 4: Review and confirm action list

**Why Critical for MVP**: Action items are the primary output of phase 4 (DECIDE_ACTIONS). Without structured action items, retrospectives end without clear commitments. The WHAT/WHO/WHEN structure ensures accountability.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/actions/ActionItem.java` - Entity
- `src/main/java/direct/reflect/facilitator/actions/ActionItemRepository.java` - Repository
- `src/main/java/direct/reflect/facilitator/actions/ActionItemService.java` - Business logic
- `src/main/java/direct/reflect/facilitator/actions/ActionItemController.java` - API endpoints
- `src/main/resources/templates/fragments/components/action-item-form.html` - UI

**Files to Modify**:
- `retrospective_steps.csv` - Add ACTION_ITEM_FORM component to phase 4 steps
- `ComponentType.java` - Add ACTION_ITEM_FORM enum value

**Estimated Effort**: 3-4 days

---

### 8. Guided Facilitation (Priority: High, 25 scenarios) 🟡 PARTIALLY IMPLEMENTED

**As a**: Facilitator
**I want**: Step-by-step facilitation guidance
**So that**: I can run effective retrospectives without specialized training

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🟡 **IMPORTANT** - Basic guidance exists, enhancements needed
**Implementation Complexity**: Medium (conditional logic, multimedia support)
**Dependencies**: None

#### Implementation Status: 12% (3 of 25 scenarios)

**✅ Implemented Scenarios (3)**:
- [x] Scenario 1: Guidance text stored in CSV (RetroStep.guidance field)
- [x] Scenario 2: Guidance displayed in left sidebar (retro.html tooltip)
- [x] Scenario 8: Text-based facilitation scripts for all 34 steps

**❌ Missing Scenarios (22)** - Organized by Theme:

**Multimedia Guidance (3 scenarios)**:
- [ ] Scenario 5: Video guidance support (YouTube embed or uploaded video)
- [ ] Scenario 6: Audio playback for verbal instructions
- [ ] Scenario 7: Visual diagrams and metaphors (Sailboat, Tree, etc.)

**Conditional Guidance (5 scenarios)**:
- [ ] Scenario 21: AI-generated guidance based on participation rates ("Only 3 of 8 responded - consider...")
- [ ] Scenario 22: Context-aware prompts based on vote patterns ("Votes clustered in 'Sad' - focus discussion there")
- [ ] Scenario 23: Dynamic script adaptation (adjust guidance if team is stuck)
- [ ] Scenario 24: Facilitator alerts (red boxes for critical reminders)
- [ ] Scenario 25: Situational tips (e.g., "If discussion stalls, try asking...")

**Sidebar UI Enhancements (6 scenarios)**:
- [ ] Scenario 3: Expandable sections for detailed scripts
- [ ] Scenario 4: Step-by-step numbered checklists ("1. Explain activity 2. Start timer 3...")
- [ ] Scenario 9: "Need Help?" red alert boxes for common pitfalls
- [ ] Scenario 10: Collapsible guidance panel (minimize to give more screen space)
- [ ] Scenario 11: Guidance history (see previous steps' instructions)
- [ ] Scenario 15: Facilitator notes section (private scratchpad)

**Script Management (5 scenarios)**:
- [ ] Scenario 12: Script versioning (track changes to guidance text)
- [ ] Scenario 13: Custom scripts per template (different guidance for Mad/Sad/Glad vs Start/Stop/Continue)
- [ ] Scenario 14: Facilitator can customize guidance per session (override default text)
- [ ] Scenario 16: Guidance templates library (copy proven scripts)
- [ ] Scenario 17: Export guidance as facilitator playbook

**Chatbox-Style Guidance (3 scenarios)**:
- [ ] Scenario 18: Guidance appears as chat messages (simulates human facilitator)
- [ ] Scenario 19: Message threading (questions and answers)
- [ ] Scenario 20: Guidance persists across steps (scroll back to earlier instructions)

**Why Important for MVP**: Basic text guidance works, but facilitators need:
1. Visual/audio support for complex activities
2. Conditional logic to adapt to team dynamics
3. Better UI organization (checklists, alerts, expandable sections)

Without these enhancements, facilitators still need to interpret generic instructions and adapt on the fly.

**Files Already Implemented**:
- `RetroStep.java` - guidance field (TEXT column)
- `retro.html` - Left sidebar tooltip
- `retrospective_steps.csv` - 34 steps with guidance text

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/guidance/ConditionalGuidanceService.java` - Logic for context-aware tips
- `src/main/resources/templates/fragments/guidance-chatbox.html` - Chatbox-style UI
- `src/main/resources/templates/fragments/guidance-checklist.html` - Step-by-step checklists

**Files to Modify**:
- `retro.html` - Replace simple tooltip with rich guidance panel (video player, audio, checklists)
- `RetroStep.java` - Add mediaType and mediaUrl fields for video/audio
- `RetroViewController.java` - Add getGuidanceHistory() endpoint
- `ResponseService.java` - Add metrics for conditional guidance (participation rate, vote distribution)

**Estimated Effort**: 4-5 days (multimedia support, conditional logic, UI redesign)

---

### 9. Customized Length (Priority: High, 13 scenarios) 🟡 IMPORTANT

**As a**: Facilitator
**I want**: Retrospective session that ends within selected time
**So that**: I know the session will be effective and end on time

**Notion Priority**: High
**Status**: In progress
**MVP Classification**: 🟡 **IMPORTANT** - Improves usability but not blocking
**Implementation Complexity**: Medium (time allocation algorithm)
**Dependencies**: Format Catalog (for compatible format selection)

#### Implementation Status: 0% (0 of 13 scenarios)

**❌ All Scenarios Missing (13)**:
- [ ] Scenario 1: Facilitator selects meeting duration during retro creation (30, 60, 90, 120 min)
- [ ] Scenario 2: Duration constraints enforced (dropdown only shows valid options)
- [ ] Scenario 3: Only compatible formats shown for selected duration
- [ ] Scenario 4: Schedule breakdown displayed after format selection (per-phase timing)
- [ ] Scenario 5: System avoids similar schedules when regenerating
- [ ] Scenario 6: Facilitator can regenerate schedule
- [ ] Scenario 7: Facilitator confirms and starts retrospective with schedule
- [ ] Scenario 8: Duration cannot be changed after confirmation
- [ ] Scenario 9: Phase durations calculated to the second
- [ ] Scenario 10: Schedule breakdown shows Larsen & Derby phase names
- [ ] Scenario 11: Schedule breakdown visible to team members during session
- [ ] Scenario 12: Buffer time transparently allocated within phases
- [ ] Scenario 13: Format availability changes with duration selection

**Why Important for MVP**: Teams have different meeting constraints. A 30-minute retro needs different activities than a 120-minute retro. This enables the system to fit within real-world calendar constraints.

**Conflict Note**: Scenario 12 in Format Catalog story states "format durations are fixed" which conflicts with this story's dynamic time allocation. Need to resolve with user.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/planning/DurationPlanner.java` - Time allocation algorithm
- `src/main/resources/templates/fragments/duration-selector.html` - UI component

**Files to Modify**:
- `RetroSession.java` - Add totalDurationMinutes field
- `RetroSessionService.java` - Add createSessionWithDuration() method
- `create-retro-form.html` - Add duration dropdown

**Estimated Effort**: 4-5 days

---

### 10. Forced to Next Step (Priority: High, 14 scenarios) 🟡 IMPORTANT

**As a**: Facilitator
**I want**: Automatic shift to next phase when time box expires
**So that**: I don't have to manage time myself and we can finish within the given time box

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🟡 **IMPORTANT** - Nice-to-have automation
**Implementation Complexity**: Low (backend mostly done, need UI timer and scheduler)
**Dependencies**: None

#### Implementation Status: 29% (4 of 14 scenarios)

**✅ Implemented Scenarios (4)**:
- [x] Scenario 4: Auto-advance occurs when timer reaches zero (TIMER_EXPIRES trigger)
- [x] Scenario 9: Manual advance still available (facilitator override always works)
- [x] Scenario 10-11: Final phase shows completion screen (RetroPhase.next() logic)
- [x] Backend: durationSeconds field, timerHasExpired() validation

**❌ Missing Scenarios (10)**:
- [ ] Scenario 1: Auto-advance enabled by default
- [ ] Scenario 2-3: Facilitator can toggle auto-advance off/on (UI control)
- [ ] Scenario 5: Countdown warning before auto-advance (10 seconds)
- [ ] Scenario 6-7: Paused timer prevents/re-enables auto-advance
- [ ] Scenario 8: Work in progress is lost during auto-advance (warning dialog)
- [ ] Scenario 12: Auto-advance disabled requires manual progression
- [ ] Scenario 13: All participants see auto-advance simultaneously (SSE)
- [ ] Scenario 14: Auto-advance state persists through reconnection

**Why Important for MVP**: Keeps retrospectives on schedule without facilitator micromanagement. However, manual advancement works fine for MVP - this is quality-of-life automation.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/scheduling/StepAdvancementScheduler.java` - Background job
- `src/main/resources/templates/fragments/components/countdown-timer.html` - UI component

**Files to Modify**:
- `RetroSessionService.java` - Add autoAdvanceEnabled field, polling logic
- `retro.html` - Add countdown timer display
- `EventService.java` - Add AUTO_ADVANCE_WARNING event

**Estimated Effort**: 2-3 days

---

### 11. Clustering and Grouping System (Priority: High, 4 scenarios) 🟡 IMPORTANT

**As a**: Retrospective participant
**I want**: To group similar ideas together
**So that**: We can identify patterns and themes in feedback

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🟡 **IMPORTANT** - Enhances analysis but not blocking
**Implementation Complexity**: High (drag-and-drop UI, AI clustering)
**Dependencies**: None

#### Implementation Status: 0% (0 of 4 scenarios)

**❌ All Scenarios Missing (4)**:
- [ ] Scenario 1: Manual clustering via drag-and-drop
- [ ] Scenario 2: AI-suggested clustering with confidence scores
- [ ] Scenario 3: Collaborative cluster naming
- [ ] Scenario 4: Cluster reorganization

**Why Important for MVP**: Essential for 3 major formats (Mad Sad Glad, Perfection Game, Start Stop Continue). When teams submit 50+ sticky notes, clustering reveals patterns. Without it, teams spend 30+ minutes manually organizing notes.

**Note**: This is complex to implement (drag-and-drop, real-time collaboration, AI suggestions) but high value for team analysis.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/clustering/ResponseCluster.java` - Entity
- `src/main/java/direct/reflect/facilitator/clustering/ClusteringService.java` - Business logic
- `src/main/java/direct/reflect/facilitator/ai/ClusteringAIService.java` - AI suggestions
- `src/main/resources/templates/fragments/components/clustering-board.html` - Drag-and-drop UI

**Files to Modify**:
- `ParticipantResponse.java` - Add clusterId foreign key
- `ComponentType.java` - Add CLUSTERING_BOARD enum value

**Estimated Effort**: 7-10 days (complex drag-and-drop, real-time sync, AI)

---

### 12. Visual Clue Stage (Priority: High, 16 scenarios) 🟢 NICE-TO-HAVE

**As a**: Facilitator
**I want**: Visual clue on where I am in the overall process
**So that**: I can relate to where we are in the process

**Notion Priority**: High
**Status**: To do
**MVP Classification**: 🟢 **NICE-TO-HAVE** - Basic progress indicator exists
**Implementation Complexity**: Low (UI work only)
**Dependencies**: None

#### Implementation Status: 19% (3 of 16 scenarios)

**✅ Implemented Scenarios (3)**:
- [x] Scenario 3: Current phase is visually highlighted (basic 5-circle indicator)
- [x] Scenario 6: Map updates when phase changes (SSE real-time update)
- [x] Scenario 7: All participants see identical map state

**❌ Missing Scenarios (13)** - Metro Map Enhancement:
- [ ] Scenario 1: Underground map displayed horizontally at top (current: simple circles)
- [ ] Scenario 2: Map shows all 5 phases as connected stations (need connecting lines)
- [ ] Scenario 4: Completed phases are greyed out (need styling)
- [ ] Scenario 5: Upcoming phases displayed normally
- [ ] Scenario 8: Map shows complete retrospective journey (visual continuity)
- [ ] Scenario 9: Map is always visible and cannot be hidden
- [ ] Scenario 10: Map is non-interactive (correct - current implementation)
- [ ] Scenario 11: Map updates synchronously for all participants (already working via SSE)
- [ ] Scenario 12: Map provides contextual awareness through phase states
- [ ] Scenario 13: Map styling follows underground/metro design (SVG graphics needed)
- [ ] Scenario 14: First phase shows correct initial state
- [ ] Scenario 15: Final phase shows correct completion state
- [ ] Scenario 16: Connecting lines show journey progression

**Why Nice-to-Have for MVP**: Current 5-circle indicator works functionally. Metro map is cosmetic enhancement that improves UX but doesn't block retrospective flow.

**Files to Modify**:
- `retro.html` - Replace 5 circles with metro map SVG
- Add CSS for metro map styling
- Add SVG assets for train/station icons

**Estimated Effort**: 1-2 days (UI design work)

---

### 13. Anonymous Login (Priority: Medium, 7 scenarios) 🟢 NICE-TO-HAVE

**As a**: Team member
**I want**: To login anonymously without barriers
**So that**: I can participate easily while maintaining psychological safety

**Notion Priority**: Medium
**Status**: To do
**MVP Classification**: 🟢 **NICE-TO-HAVE** - Guest mode exists, animals are cosmetic
**Implementation Complexity**: Low (simple name generation)
**Dependencies**: None

#### Implementation Status: 57% (4 of 7 scenarios)

**✅ Implemented Scenarios (4)**:
- [x] Scenario 1: Team member joins via unique session link (working)
- [x] Scenario 3: Consistent identity within session (CookieAuthenticationToken)
- [x] Scenario 4: Re-entry preserves session state (Redis session storage)
- [x] Scenario 5: Invalid or expired session link handling (404 error page)

**❌ Missing Scenarios (3)** - Animal Pseudonyms:
- [ ] Scenario 2: Automatic pseudonym assignment with random animal name
- [ ] Scenario 6: Multiple participants with unique animal pseudonyms
- [ ] Scenario 7: No login barriers or friction (already working - just need animals)

**Why Nice-to-Have for MVP**: Guest mode fully functional with user-entered display names. Animal pseudonyms add whimsy but don't affect retrospective functionality.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/auth/AnimalPseudonymService.java` - Name generator

**Files to Modify**:
- `Participant.java` - Add animalPseudonym field (optional)
- `AuthService.java` - Generate pseudonym on guest login
- `join-retro-form.html` - Remove display name input, show assigned pseudonym

**Estimated Effort**: 1 day

---

### 14. Format Catalog & Auto-Generation Engine (Priority: Medium, 23 scenarios) 🟢 NICE-TO-HAVE

**As a**: Facilitator
**I want**: System to intelligently generate unique 5-phase retrospective flow
**So that**: I have a proven, varied retrospective without manual planning

**Notion Priority**: Medium
**Status**: In progress
**MVP Classification**: 🟢 **NICE-TO-HAVE** - Single template works for MVP
**Implementation Complexity**: High (format catalog, generation algorithm, conflict resolution)
**Dependencies**: Customized Length (for duration-based format selection)

#### Implementation Status: 4% (1 of 23 scenarios)

**✅ Implemented Scenarios (1)**:
- [x] Scenario 17: Generated flow integrates with all retrospective features (CSV template works)

**❌ Missing Scenarios (22)**:
- [ ] Scenario 1-2: Facilitator selects duration, system generates compatible format flow
- [ ] Scenario 3: Same format can appear in same phase across different flows
- [ ] Scenario 4: Display complete retrospective flow overview
- [ ] Scenario 5: Calculate and display total session duration
- [ ] Scenario 6: System respects format duration constraints
- [ ] Scenario 7: Ensure true randomness in generation
- [ ] Scenario 8: Each phase format includes all necessary configuration
- [ ] Scenario 9: View detailed format instructions before starting
- [ ] Scenario 10: Regenerate flow with uniqueness constraint
- [ ] Scenario 11: Start retrospective session with generated flow
- [ ] Scenario 12: Format durations are fixed (CONFLICTS with Customized Length)
- [ ] Scenario 13: Phase 1 formats have no voting configuration
- [ ] Scenario 14: System optimizes format selection for time constraints
- [ ] Scenario 15: Minimum catalog of 5 formats per phase (25 total)
- [ ] Scenario 16: Format catalog enables 3,125 possible combinations
- [ ] Scenario 18: Flow generation considers format compatibility
- [ ] Scenario 19: Format instructions designed for online facilitation
- [ ] Scenario 20: System handles exhausted combinations gracefully
- [ ] Scenario 21: Each format has unique identity within its phase
- [ ] Scenario 22: Generated flow creates coherent retrospective experience
- [ ] Scenario 23: Facilitator can see available formats for their duration

**Why Nice-to-Have for MVP**: Current single-template approach works fine for initial users. Format variety prevents "retro fatigue" but isn't needed for first 10-20 retrospectives.

**Conflict Note**: Scenario 12 conflicts with Customized Length story - need user decision on whether format durations are fixed or flexible.

**Files to Create**:
- `src/main/java/direct/reflect/facilitator/formats/FormatCatalog.java` - Format definitions
- `src/main/java/direct/reflect/facilitator/formats/FlowGenerator.java` - Generation algorithm
- Expand CSV to include 25 formats (5 per phase)

**Estimated Effort**: 10-14 days (complex algorithm, large catalog creation)

---

### 15. Statistical Calculations and Display (Priority: Low, 3 scenarios) 🟢 NICE-TO-HAVE

**As a**: Facilitator
**I want**: To see statistical summaries of numeric inputs
**So that**: I can quickly understand team sentiment and patterns

**Notion Priority**: Low
**Status**: To do
**MVP Classification**: 🟢 **NICE-TO-HAVE** - Basic histogram exists
**Implementation Complexity**: Low (simple calculations)
**Dependencies**: None

#### Implementation Status: 0% (0 of 3 scenarios)

**❌ All Scenarios Missing (3)**:
- [ ] Scenario 1: Calculate and display rating statistics (average, median, range, distribution chart)
- [ ] Scenario 2: Real-time statistical updates during voting
- [ ] Scenario 3: Outlier detection and display with alerts

**Why Nice-to-Have for MVP**: Current histogram shows distribution visually. Adding mean/median/range adds analytical depth but doesn't change retrospective outcomes.

**Files to Modify**:
- `histogram-chart.html` - Add statistics panel below chart
- `ResponseService.java` - Add getRatingStatistics() method

**Estimated Effort**: 0.5-1 day

---

## 🎯 MVP Implementation Roadmap

### What's Already Done ✅ (33% Implementation)

**Fully Complete (2 stories)**:
- ✅ **Five Step Flow** - 80% complete, core structure working
- ✅ **Numeric Rating Input** - 100% complete, fully functional

**Mostly Complete (2 stories)**:
- 🟡 **Input Mechanism** - 72% complete, missing rich text/emoji
- 🟡 **Anonymous Login** - 57% complete, missing animal pseudonyms only

**Partially Complete (3 stories)**:
- 🟡 **Timer** - 29% complete, backend done, missing UI
- 🟡 **Voting** - 14% complete, backend done, missing UI
- 🟡 **Visual Clue Stage** - 19% complete, basic circles, missing metro map

**Total Implemented**: ~75 of 233 scenarios (32%)

---

### 🔴 TIER 1 - Core Retrospective Flow (3-4 weeks)

**Must-Have Features for Functional MVP**:

1. **Timer** (3-4 days) - Countdown display, color states, auto-submit
   - **Why Critical**: Without visible timer, participants don't know time remaining. Teams waste time or rush submissions.
   - **Current**: Backend complete, need MM:SS display and SSE sync

2. **Voting Mechanism** (2-3 days) - Vote visualization (●●● 3), sorting, attribution
   - **Why Critical**: Voting determines which topics get discussed. Need to see "3 people voted for this" to build consensus.
   - **Current**: Backend complete, need UI enhancements

3. **Action Item Management** (3-4 days) - Structured WHAT/WHO/WHEN fields
   - **Why Critical**: Retrospectives must end with clear commitments, not vague intentions.
   - **Current**: Not started, need new entity and CRUD

4. **Smart Improvement Story** (5-7 days) - AI-powered SMART goals from votes
   - **Why Critical**: Killer feature that transforms vague action items into trackable goals with acceptance criteria.
   - **Current**: Not started, need AI integration

**TIER 1 Total**: 13-18 days (2.5-3.5 weeks)

**Why These 4 Are Critical**: They define Facilitator's value proposition:
- **Timer**: Keeps retrospectives on schedule (time management)
- **Voting**: Democratic prioritization (team consensus)
- **Action Items**: Structured commitments (accountability)
- **AI Goals**: Transforms intentions into measurable outcomes (SMART criteria)

Without these, Facilitator is just another sticky note tool.

---

### 🟡 TIER 2 - Flow Automation & Analysis (3-4 weeks)

**Important Enhancements for Polished MVP**:

5. **Guided Facilitation** (4-5 days) - Video/audio guidance, conditional tips, checklists
   - **Current**: Basic text exists (12% complete), need multimedia + conditional logic

6. **Customized Length** (4-5 days) - Duration selection (30/60/90/120 min) with compatible formats
   - **Current**: Not started, need time allocation algorithm

7. **Forced to Next Step** (2-3 days) - Auto-advancement when timer expires
   - **Current**: Backend 29% complete, need scheduler + UI controls

8. **Clustering & Grouping** (7-10 days) - Drag-and-drop grouping, AI-suggested clusters
   - **Current**: Not started, complex drag-and-drop + AI integration

**TIER 2 Total**: 17-23 days (3.5-4.5 weeks)

**Why Important**: Improves facilitator experience and team analysis quality, but not blocking for basic retrospectives.

---

### 🟢 TIER 3 - Polish & Delight (2-3 weeks)

**UX Enhancements for Delightful MVP**:

9. **Input Mechanism Enhancements** (2-3 days) - Rich text, emoji picker, keyboard shortcuts
   - **Current**: 72% complete, core working

10. **Visual Clue Stage** (1-2 days) - Metro map progress indicator
    - **Current**: 19% complete, basic circles working

11. **Anonymous Login Animals** (1 day) - Animal pseudonyms
    - **Current**: 57% complete, guest mode working

12. **Format Catalog** (10-14 days) - Multiple retrospective templates (3,125 combinations)
    - **Current**: 4% complete, single template working

13. **Statistical Calculations** (0.5-1 day) - Mean/median/range for ratings
    - **Current**: Not started, histogram exists

**TIER 3 Total**: 14.5-21 days (3-4 weeks)

**Why Nice-to-Have**: Cosmetic improvements that delight users but don't affect core retrospective outcomes.

---

### Estimated MVP Timeline

**Foundation Already Done** (Week 0):
- Five Step Flow structure ✅
- Input mechanism working ✅
- Numeric ratings working ✅
- Anonymous guest mode ✅

**Phase 1 - Critical Features** (Weeks 1-2):
- Week 1: Timer + Voting UI = TIME MANAGEMENT + PRIORITIZATION
- Week 2: Action Items + start AI integration = ACCOUNTABILITY

**Phase 2 - AI Completion** (Weeks 3-4):
- Week 3: Smart Improvement Story (AI) = SMART GOALS
- Week 4: Testing + bug fixes = **FUNCTIONAL MVP** 🎯

**Phase 3 - Flow Automation** (Weeks 5-7):
- Week 5: Guided Facilitation enhancements
- Week 6: Customized Length + Forced Next Step
- Week 7: Clustering & Grouping = **POLISHED MVP** ✨

**Phase 4 - Polish** (Weeks 8-10):
- Weeks 8-10: Tier 3 enhancements = **DELIGHTFUL MVP** 🚀

**Total Timeline**:
- **4 weeks to Functional MVP** (Tier 1 complete)
- **7 weeks to Polished MVP** (Tier 1 + 2 complete)
- **10 weeks to Delightful MVP** (all 15 stories complete)

---

## 🔗 Key Dependencies & Conflicts

### Dependencies
1. **Smart Improvement Story** requires **Voting Mechanism** (to identify highest-voted item)
2. **Format Catalog** requires **Customized Length** (to filter compatible formats)
3. **Clustering** enhances **Voting** (vote on clusters, not individual cards)

### Conflicts to Resolve
1. **Format Catalog Scenario 12 vs. Customized Length**: Are format durations fixed or flexible?
   - **Recommendation**: Make durations flexible (support Customized Length story)
   - **Rationale**: Real-world teams have varying time constraints

2. **Auto-Advancement vs. Manual Control**: Should facilitators always have override?
   - **Current Implementation**: Facilitators can always override (correct approach)
   - **Recommendation**: Keep facilitator override for all advancement triggers

---

## 🏗️ Architecture & Technical Notes

### Component-Based Architecture (Implemented)

**Core Enums**:
- `ComponentType`: MULTI_COLUMN_BOARD, RATING_SCALE, HISTOGRAM_CHART, VISUAL_LAYOUT
- `AdvancementTrigger`: FACILITATOR_CLICK, ALL_RESPONDED, TIMER_EXPIRES, AUTO

**Entity Structure**:
- RetroStep: `componentType`, `componentConfig` (JSONB), `advancementTrigger`, `durationSeconds`, `guidance`
- ParticipantResponse: `responseData` (JSONB Map<String, Object>)

**Templates**:
- `fragments/components/multi-column-board.html` (191 lines)
- `fragments/components/rating-scale.html`
- `fragments/components/histogram-chart.html`

**Key Principle**: Pure JSON configuration, no Java config classes needed. Templates read from ${config} directly.

### SSE Real-Time Collaboration (Working)

**Event Types** (RetroEvent.EventType):
- PARTICIPANT_JOINED / PARTICIPANT_LEFT
- SESSION_STARTED
- STEP_ADVANCED
- NOTE_ADDED / NOTE_UPDATED / NOTE_DELETED
- VOTE_ADDED / VOTE_REMOVED (future)

**HTMX Integration Pattern**:
```html
<div hx-ext="sse" sse-connect="/api/retro/{retroId}/events">
  <div id="component"
       hx-get="/retro/{retroId}/step/{stepId}/responses/column"
       hx-trigger="sse:note_added from:body"
       hx-swap="innerHTML">
  </div>
</div>
```

**Test Coverage**: All 8 integration tests passing (SSEConnectionIntegrationTest + RetroFlowIntegrationTest)

### Security & Validation (Implemented)

**Jakarta Bean Validation** at controller level:
- `@Valid @ModelAttribute` for automatic form binding
- Custom exceptions: `InvalidSessionStateException`, `InvalidStepException`, `VoteLimitExceededException`
- Authorization-first validation (prevents session ID enumeration)

**Test Coverage**: 21 unit tests in RetroApiControllerTest (100% pass rate)

### Important Technical Rules

1. **Thymeleaf Reserved Words**: Never use `session` as variable name (reserved in web context). Always use `retroSession`.

2. **Avoid Complex SpEL**: Use Thymeleaf utilities (`#aggregates.sum()`, `#numbers.sequence()`) instead of stream operations.

3. **DTO Pattern**: Always convert entities to DTOs before template rendering. DTOs implement `ComponentResponseDto.toResponseData()`.

4. **No Null Returns**: Services throw exceptions instead of returning null. Fail-fast principle.

5. **Facilitator Override**: Facilitator can ALWAYS advance, even if blocking conditions not met. Show warnings but never block.

---

## 📝 Development Commands

### Run Application
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=import
```
**IMPORTANT**: Always use `import` profile to load CSV templates/stages/steps.

### Build & Test
```bash
mvn clean compile
mvn test
mvn test -Dtest=ClassName#methodName
```

### Docker Services
```bash
docker compose up -d    # Start PostgreSQL + Redis
docker compose down     # Stop services
```

### Logs
Application logs to **both**:
- Console (for user)
- `/tmp/facilitator.log` (for Claude to monitor)

---

## 📚 Related Documentation

- `CLAUDE.md` - Full product vision, architecture, technical patterns
- `roadmap.md` - Detailed user stories and Gherkin scenarios (if exists)
- `system-ui/` - UI mockup screenshots (7 files)
- Notion User Stories: https://notion.so/a2d07350-84f9-41f3-ac64-fc4ed68f7bdd
- Plan File: `/Users/micheljansen/.claude/plans/happy-pondering-hennessy.md`
