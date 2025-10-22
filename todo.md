# TODO: Fix Retrospective UI and Implement Core Functionality

**Created**: 2025-10-22
**Status**: Sprint 1 in progress

---

## Current State Analysis
- ✅ **Working**: SSE connections, lobby phase, authentication, session creation
- ❌ **Broken**: Active retrospective layout is messy, missing components, doesn't match mockups
- 🎯 **Target**: Clean 3-column layout from system-ui mockups with app-driven facilitation

---

## Phase 1: Fix Current Broken Layout (Immediate Priority)

### Issue: retro.html has wrong structure
The current `retro.html` attempts to render a complex layout with navigation bars and sidebars, but:
- Doesn't follow the mockup design
- Has missing/broken components
- Mixes multiple UI concepts (HTMX 2.x layout + old fragments)

### Fix Strategy:

**Step 1.1: Create Clean Base Template Structure**
- Simplify `retro.html` to match mockup layout:
  - **Top**: Golden header bar (brand logo, timer, stage progress indicators, user menu)
  - **Main**: Three-column layout:
    - **Left column (25%)**: Video/guidance area with coaching tooltips
    - **Center column (50%)**: Main activity area (clean, focused)
    - **Right column (25%)**: Participants list and results display
  - **Bottom**: Facilitator controls (Cancel/Next buttons)

**Step 1.2: Implement Stage Progress Indicators**
- Based on mockup: 5 circles showing stage progression
- States: "In Progress" (filled yellow), "To Do" (dotted outline), "Complete" (checkmark)
- Display stage names under each circle
- Update automatically via SSE when facilitator advances

**Step 1.3: Add Timer Display**
- Golden header shows: `01 | 09 | 30` (session time or step timer)
- Updates in real-time for timed activities

**Step 1.4: Create Guidance Tooltip System**
- Left sidebar shows video player or text instructions
- Closeable overlay with facilitation guidance
- Examples from mockup:
  - "Reflect & Choose Your Score" with video
  - "Need Help?" red alert box for critical tips
  - Step-by-step numbered instructions

---

## Phase 2: Implement 3-Pattern Data System (Foundation)

Based on roadmap.md "3-pattern retrospective data system":

### Step 2.1: RATING Pattern (Happiness Histogram)
**Why first?** Mockups show this, simplest to implement, proves architecture

**Backend** (already partially done):
- ✅ `ParticipantResponse` entity with `rating` field
- ✅ `DataPattern.RATING` enum
- ✅ `ResponseService.submitRatingResponse()`

**Frontend UI Components Needed**:
- Rating scale selector (1-10 radio buttons or dropdown)
- Optional comment textarea
- Histogram visualization for results (bar chart showing distribution)
- Privacy toggle (PRIVATE input → PUBLIC reveal by facilitator)

**Template Structure**:
```html
<div class="three-column-layout">
  <!-- LEFT: Guidance -->
  <div class="guidance-sidebar">
    <div class="video-player"><!-- Video guidance --></div>
    <div class="instructions">Reflect on your experience...</div>
  </div>

  <!-- CENTER: Activity -->
  <div class="activity-area">
    <h2>Stage 1: Happiness Histogram</h2>
    <p>What is your current hapiness?</p>
    <select name="rating"><!-- 1-10 options --></select>
    <!-- Submit with HTMX POST -->
  </div>

  <!-- RIGHT: Results (shown after reveal) -->
  <div class="results-sidebar">
    <!-- Histogram chart (initially hidden) -->
    <div id="histogram"><!-- SVG bar chart --></div>
  </div>
</div>
```

**Multi-Step Flow** (from roadmap.md):
1. INSTRUCTION step: Show guidance
2. ACTIVITY step (PRIVATE): Participants select rating
3. REVEAL step: Facilitator clicks reveal, histogram appears
4. DISCUSSION step: Comments section opens
5. WRAP-UP step: Next stage

### Step 2.2: CATEGORICAL Pattern (Mad Sad Glad)
**Why second?** Common format, tests multi-column layout

**Frontend UI Components**:
- 3-column grid with category headers (Mad/Sad/Glad with emojis)
- Text input areas for sticky notes in each column
- Real-time note display (privacy-aware: blurred when PRIVATE)
- Clustering UI (future: drag-and-drop grouping)

### Step 2.3: FREEFORM Pattern (One Word, Kudos)
**Why third?** Simplest, wraps up basic patterns

**Frontend UI Components**:
- Single text input or multiple text areas
- Word cloud visualization (results phase)
- List display for longer responses

---

## Phase 3: Multi-Step Flow System

### Step 3.1: StepType Rendering
Current `RetroStep` has `stepType` enum (INSTRUCTION, ACTIVITY, DISCUSSION):
- **INSTRUCTION**: Show guidance only, no input
- **ACTIVITY**: Show input UI based on `dataPattern`
- **DISCUSSION**: Show results + comment areas
- **REVEAL**: Transition step (facilitator-triggered)

### Step 3.2: Privacy Controls
- Backend: `isPrivate` flag on `RetroStep`
- Frontend: When PRIVATE:
  - Participants see only their own responses
  - Others see "Waiting for responses..." or blurred content
- Facilitator control: "Reveal All" button (POST `/api/retro/{id}/step/{stepId}/reveal`)
- SSE event: `STEP_REVEALED` triggers UI update for all participants

### Step 3.3: Step Advancement
- Facilitator sees "Next" button (golden color from mockup)
- POST `/api/retro/{id}/next` advances to next step
- SSE event `STEP_ADVANCED` updates all participant UIs
- Progress indicators update automatically

---

## Phase 4: Polish and UX Details

### Step 4.1: Visual Design Match
- Golden header bar (`bg-amber-500` or custom yellow)
- Clean white content areas
- Progress circles with proper states
- Button styling (Cancel white, Next golden)

### Step 4.2: Responsive Behavior
- Three-column layout collapses on mobile
- Guidance tooltips become expandable accordion
- Maintain usability on tablets

### Step 4.3: Error States
- Handle disconnections gracefully
- Show reconnection UI
- Preserve user input during brief disconnects

---

## Implementation Order (Recommended)

### Sprint 1: Foundation & Immediate Fixes (Week 1) ⬅️ **CURRENT SPRINT**
1. 🔄 **Day 1-2**: Fix retro.html base template structure
   - Create clean 3-column layout
   - Add golden header with progress indicators
   - Add timer display
   - Test with empty/mock content

2. 🔲 **Day 3-4**: Implement guidance sidebar system
   - Video player component
   - Text instruction cards
   - "Need Help?" alert boxes
   - Closeable tooltips

3. 🔲 **Day 5**: Add facilitator controls
   - Cancel/Next buttons at bottom
   - Wire up to existing `/api/retro/{id}/next` endpoint
   - Test step advancement

### Sprint 2: RATING Pattern Implementation (Week 2)
4. 🔲 **Day 1-2**: Happiness Histogram activity UI
   - Rating scale selector (1-10)
   - Comment textarea
   - HTMX form submission
   - Integration with ResponseService

5. 🔲 **Day 3-4**: Histogram visualization
   - Bar chart component (SVG or Chart.js)
   - Results display in right sidebar
   - Privacy toggle (private input → public reveal)

6. 🔲 **Day 5**: Multi-step flow for Happiness Histogram
   - INSTRUCTION → ACTIVITY → REVEAL → DISCUSSION → WRAP-UP
   - Test with real participants

### Sprint 3: CATEGORICAL Pattern (Week 3)
7. 🔲 Mad Sad Glad implementation
8. 🔲 Privacy controls and reveal functionality
9. 🔲 Real-time collaborative updates

### Sprint 4: FREEFORM Pattern & Polish (Week 4)
10. 🔲 One Word / Kudos implementation
11. 🔲 Visual design final touches
12. 🔲 Cross-browser testing
13. 🔲 Performance optimization

---

## Key Success Criteria

### Must Have (MVP):
- ✅ Clean 3-column layout matching mockups
- ✅ Stage progress indicators working
- ✅ One complete pattern (RATING) with multi-step flow
- ✅ Privacy controls (private input → public reveal)
- ✅ Facilitator advancement controls
- ✅ Real-time synchronization via SSE

### Should Have:
- ✅ All 3 patterns working (RATING, CATEGORICAL, FREEFORM)
- ✅ Guidance tooltip system
- ✅ Timer display
- ✅ Participant list in right sidebar
- ✅ Mobile-responsive layout

### Nice to Have (Future):
- Clustering/grouping for CATEGORICAL
- Voting system
- Action item management
- Analytics dashboard

---

## Design References

### System UI Mockups Location
- `system-ui/Screenshot 2025-08-27 at 13.07.33.png` - Happiness Histogram input screen
- `system-ui/Screenshot 2025-08-27 at 13.08.01.png` - Histogram results/insights screen
- `system-ui/Screenshot 2025-08-27 at 13.07.47.png` - Additional mockups
- See all 7 mockup screenshots in `system-ui/` directory

### Key Design Elements from Mockups
- **Golden header**: Brand logo left, timer center (`01 | 09 | 30`), user menu right
- **Progress circles**: 5 stages with states (In Progress=filled yellow, To Do=dotted, Complete=checkmark)
- **Left sidebar**: Video player with dark overlay + closeable guidance cards
- **Center area**: Clean white background, focused activity UI, minimal clutter
- **Right sidebar**: (Not shown in current mockups - reserved for participants/results)
- **Bottom controls**: "Cancel" (white) and "Next" (golden) buttons

---

## Questions to Resolve

1. **CSV Import**: Should we prioritize CSV import for RetroSteps, or manually configure first template in code?
   - **Decision**: Start with hardcoded template in Java, add CSV later

2. **Video Guidance**: Do we have actual videos, or placeholder for now?
   - **Decision**: Start with text instructions, design for video support

3. **Chart Library**: Use Chart.js, D3.js, or SVG for histogram?
   - **Decision**: Simple SVG bars first, Chart.js if time permits

4. **Clustering UI**: Drag-and-drop library (SortableJS, DnD Kit)?
   - **Decision**: Phase 2 feature, skip for MVP

---

## Important Technical Notes

### Thymeleaf Reserved Words
- **NEVER use `session` as a variable name** - it's reserved in Thymeleaf web context
- Always use `retroSession` for the RetroSession model object
- Model attribute: `model.addAttribute("retroSession", session);`
- Template parameter: `th:fragment="content(retroSession)"`
- Template usage: `th:replace="~{fragments/retro :: content(retroSession=${retroSession})}"`

### SSE Event Types (from RetroEvent.EventType)
- `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT` - Update participant lists
- `SESSION_STARTED` - Lobby → Active retro transition
- `STEP_ADVANCED` - Facilitator moves to next step (triggers full UI update)
- `NOTE_ADDED` / `NOTE_UPDATED` / `NOTE_DELETED` - Response changes
- `VOTE_ADDED` / `VOTE_REMOVED` - Voting updates (future)
- `GROUP_CREATED` / `GROUP_UPDATED` / `GROUP_DELETED` - Clustering (future)

### HTMX SSE Integration Pattern
```html
<!-- SSE connection setup (in fragment root) -->
<div hx-ext="sse" th:attr="sse-connect=@{/api/retro/{retroId}/events(retroId=${retroSession.id})}"></div>

<!-- Component that updates on SSE event -->
<div id="participants-list"
     th:attr="hx-get=@{/retro/{retroId}/participants(retroId=${retroSession.id})}"
     hx-trigger="sse:participant_joined from:body, sse:participant_left from:body"
     hx-swap="innerHTML">
  <!-- Content updated via HTMX GET when SSE event fires -->
</div>
```

---

## File Structure Reference

### Key Templates
- `src/main/resources/templates/layout.html` - Master layout
- `src/main/resources/templates/fragments/retro.html` - Active retrospective UI (TO BE REFACTORED)
- `src/main/resources/templates/fragments/lobby.html` - Lobby UI (working)
- `src/main/resources/templates/fragments/sse-connection.html` - Shared SSE handlers

### Key Controllers
- `RetroViewController.java` - Thymeleaf template rendering
- `RetroApiController.java` - REST endpoints for HTMX
- `RetroEventController.java` - SSE streaming

### Key Services
- `RetroSessionService.java` - Session lifecycle management
- `ParticipantService.java` - Participant management
- `ResponseService.java` - Response submission and retrieval
- `EventService.java` - SSE event broadcasting

---

## Progress Tracking

### 🎯 Current Priorities (Ordered by Urgency)

1. ✅ **COMPLETED: SSE Auto-Reload** - Fixed and verified working
   - SSE connection persists across step advances
   - Real-time collaboration working

2. **🔵 NEXT: Sprint 2 Day 1-2: Happiness Histogram Activity UI** (Recommended)
   - Rating scale selector (1-10) - currently has radio buttons
   - HTMX form submission integration
   - Test response submission flow
   - **Rationale**: UI already has rating inputs, makes sense to complete the flow

3. **Sprint 1 Day 3-4: Guidance System** (Optional enhancement)
   - Basic guidance already working
   - Could enhance with video player component
   - Add closeable tooltips
   - "Need Help?" alert boxes

4. **Sprint 1 Day 5: Facilitator Controls**
   - Wire up Back button
   - Test full multi-step flow
   - Verify all step types render correctly

---

### Sprint 1 - Day 1-2: Base Template Structure ✅ COMPLETED
- [x] Analyze current retro.html structure
- [x] Remove debug content from center area (retro.html:214-220)
- [x] Remove duplicate header (retro.html:9-101)
- [x] Fix typo: "Hapiness" → "Happiness" (CSV + tests)
- [x] Verify UI renders cleanly with 3-column layout
- [x] Verify stage progress indicators (5 circles working)
- [x] Test Next button functionality
- [x] Verify SSE connection works
- [x] Test step advancement (INSTRUCTION → ACTIVITY)

**Started**: 2025-10-22
**Completed**: 2025-10-22

**Findings**:
- ✅ UI layout is already well-structured (no major refactor needed)
- ✅ 3-column layout working correctly
- ✅ Stage progress indicators displaying properly
- ✅ Backend step advancement working
- ⚠️ **CRITICAL ISSUE FOUND**: SSE auto-reload not triggering after step advancement

**Files Modified**:
- `retro.html` - Removed debug content and duplicate header
- `retrospective_stages.csv` - Fixed typo
- `CsvImporterServiceTest.java` - Updated test expectations

### ✅ RESOLVED: SSE Auto-Reload Issue

**Status**: ✅ **COMPLETELY RESOLVED** - 2025-10-22

#### Root Causes Identified and Fixed

**Three separate issues were causing SSE connection problems:**

1. **HTMX 2.x SSE Extension Requirement Violation** (layout.html)
   - **Problem**: SSE listeners MUST be children of the element with `hx-ext="sse"` and `sse-connect`
   - **Symptom**: SSE events not triggering HTMX automatically
   - **Fix**: Wrapped lobby/retro fragments **inside** the SSE connection div
   - **File**: `layout.html` lines 48-58

2. **Fragment Returning Full Wrapper** (RetroViewController.java + retro.html)
   - **Problem**: `/content` endpoint returned entire `#retro-content` wrapper, causing nested divs and SSE reconnection
   - **Symptom**: SSE connection closed and recreated on every content swap
   - **Fix**: Created `inner-content` fragment containing only the actual content (not the wrapper div)
   - **Files**:
     - `retro.html` line 8: Added `<div th:fragment="inner-content">`
     - `RetroViewController.java` line 183: Changed return to `fragments/retro :: inner-content`

3. **Forced Page Reload on SSE Events** (retro.html)
   - **Problem**: JavaScript listening for `htmx:sseMessage` and calling `window.location.reload()` on `step_advanced`
   - **Symptom**: Full page reload triggered, creating new SSE connection
   - **Fix**: Removed obsolete reload code (HTMX handles updates automatically via `hx-trigger`)
   - **File**: `retro.html` lines 378-384 (removed)

#### Verification Results

**Test Session**: "Final SSE Fix Test" (019a0bee-ea86-7bba-b01d-857ac176dff8)
- ✅ SSE connection established once: `ccced824-4a79-4c43-a15c-2ab498728d6e`
- ✅ Step advances Stage 1 → 2 → 3 with NO reconnection
- ✅ Same connection ID used throughout entire session
- ✅ No "SSE connection requested" after step advances
- ✅ Predictable connection count (exactly 1 active connection)
- ✅ Real-time updates work automatically via HTMX SSE triggers

#### Files Modified

1. **layout.html** - Restructured SSE div to wrap content properly
2. **retro.html** - Added `inner-content` fragment, removed reload code
3. **RetroViewController.java** - Updated `/content` endpoint to return inner fragment

#### Key Learning

Always consult HTMX extension documentation (https://htmx.org/extensions/sse/) when implementing SSE. The parent-child relationship requirement is critical for HTMX 2.x SSE extension functionality.

---

## Related Documentation
- `CLAUDE.md` - Full product vision, architecture, and technical patterns
- `roadmap.md` - Detailed user stories and Gherkin scenarios
- `system-ui/` - UI mockup screenshots
