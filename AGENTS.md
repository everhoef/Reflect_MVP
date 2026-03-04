# AGENTS.md

This file provides guidance to AI agents (Claude, Cursor, etc.) when working with the Facilitator codebase - a Spring Boot application for team retrospectives.

**Source of Truth for Stories & Roadmap**: Notion (stories, BDD scenarios, implementation status, timelines)
**Source of Truth for Technical Guidance**: This file (architecture, patterns, conventions)

---

## Product Vision & Mission

### The Problem We're Solving

Many teams struggle with retrospectives on multiple fronts:
- **Poor Preparation**: Teams come unprepared, leading to superficial discussions
- **Low Quality Outcomes**: Vague problem definitions and non-actionable takeaways
- **Time Investment**: Retrospectives drag on without clear structure or purpose
- **Facilitation Skills Gap**: Effective facilitation requires training most teams don't have
- **No Follow-Through**: Action items get lost, problems recur without visibility
- **Management Blindness**: Leaders lack insight into team health and recurring bottlenecks

### Our Solution

**Facilitator** is an app-driven retrospective platform that improves retrospective quality while reducing the burden on teams:

1. **Guided Facilitation**: The app acts as the facilitator with embedded coaching, so human facilitators just click "Next"
2. **Structured Process**: Multi-step flows ensure teams follow proven retrospective patterns (5-phase approach)
3. **Clear Problem Definitions**: Guided activities help teams articulate specific, actionable issues
4. **SMART Action Points**: System guides creation of Specific, Measurable, Achievable, Relevant, Time-bound actions
5. **Minimal Time Investment**: Streamlined process keeps retrospectives focused and efficient
6. **Zero Facilitation Skills Required**: App provides step-by-step instructions, eliminating need for certified scrum masters

### Management Dashboard Vision

Beyond improving individual retrospectives, the application provides organizational insights:

- **Team Health Monitoring**: Anonymized sentiment trends show which teams are thriving vs. struggling
- **Bottleneck Detection**: AI analyzes recurring problems across teams to identify systemic issues
- **Action Point Tracking**: Completion rates reveal follow-through gaps and help prioritize interventions
- **Performance Benchmarking**: Cross-team comparisons highlight best practices and areas for improvement
- **Contextual Recommendations**: System suggests where managers can help, backed by full retrospective context
- **Trend Analysis**: Long-term patterns reveal organizational health and culture shifts

---

## How Retrospectives Work (Domain Knowledge)

Understanding how retrospectives work in practice helps translate human facilitation into digital components.

### Five-Phase Retrospective Structure (Derby & Larsen)

All effective retrospectives follow this proven approach:

1. **Set the Stage (Check-in)**: Establish psychological safety and emotional baseline
   - Example: Happiness Histogram (rate 1-10 with explanation)
   - Digital translation: RATING pattern with privacy controls

2. **Gather Data**: Collect specific events and observations
   - Example: Mad, Sad, Glad (categorize emotions into 3 columns)
   - Digital translation: CATEGORICAL pattern with sticky notes

3. **Generate Insights**: Analyze data for patterns and root causes
   - Example: The Original Four (What did we do well? What did we learn? What should we do differently? What still puzzles us?)
   - Digital translation: FREEFORM pattern with timed responses and discussion

4. **Decide Actions**: Identify specific improvements to try next sprint
   - Example: Circle of Questions (chained Q&A building on each other)
   - Digital translation: Chained Q&A system + action item templates

5. **Close Retro (Closure)**: Reflect on the session and appreciate the team
   - Example: Feedback Door Smileys (rate the retro experience)
   - Digital translation: Simple feedback collection for meta-analysis

### Retrospective Flow Patterns

#### Pattern 1: Private Input → Public Reveal → Discussion
**Used in**: Happiness Histogram, Mad Sad Glad
```
1. Facilitator explains activity (app shows guidance tooltip)
2. Participants submit responses privately (others can't see)
3. Facilitator reveals all results simultaneously (click "Next Step")
4. Team discusses patterns and observations (guided prompts)
```

#### Pattern 2: Timed Sequential Responses → Discussion
**Used in**: The Original Four
```
1. Facilitator poses question (app shows timer: 2 minutes)
2. Participants post keywords/phrases (countdown visible)
3. After time expires, 5-minute discussion (guided questions)
4. Repeat for each question
```

#### Pattern 3: Chained Question-Answer
**Used in**: Circle of Questions
```
1. Facilitator posts first question
2. Participant A answers and posts new question for next person
3. Participant B answers A's question and posts new question
4. Continue until all participants have participated
5. Team summarizes patterns/common themes
```

#### Pattern 4: Clustering and Naming
**Used in**: Mad Sad Glad, Start/Stop/Continue
```
1. Create sticky notes (private or public depending on step)
2. Reveal all notes (app unblurs content)
3. Participants cluster similar items (drag-and-drop)
4. Team names clusters together (collaborative naming)
5. Discuss patterns and meanings
```

### Privacy and Visibility Controls

Activities require different visibility modes to prevent groupthink and ensure psychological safety:
- **PRIVATE**: Participants see only their own responses, others see "Waiting for others..." or blurred content
- **PUBLIC**: All responses visible to everyone in real-time

Facilitator controls when to transition from PRIVATE to PUBLIC visibility with a single click.

### SMART Action Points

The app guides teams from vague intentions to SMART action points:

**Bad action point**: "Improve communication"
**SMART transformation**:
- **Specific**: What exactly? → "Daily standup sync with design team"
- **Measurable**: How to verify? → "Attendance logged, 5 days/week"
- **Achievable**: Realistic? → "Yes, 15-min meeting"
- **Relevant**: Why important? → "Reduce design rework from miscommunication"
- **Time-bound**: Deadline? → "Start next Monday, review in 2 weeks"

**SMART action point**: "Start daily 15-min standup with design team every morning at 9:30am, track attendance, review effectiveness in 2 weeks"

---

## Technical Architecture

### Project Overview

Spring Boot 3.5.3 application built with Java 21, Spring MVC (not WebFlux), Spring Security, Spring Data JPA, PostgreSQL, Redis, and Thymeleaf + HTMX.

### Core Technology Stack
- **Backend**: Spring Boot 3.5.3, **Spring MVC** (Web MVC, not WebFlux), Spring Security, Spring Data JPA
- **Database**: PostgreSQL with Hibernate
- **Session Management**: Redis for session storage
- **Frontend**: Thymeleaf templates with HTMX for dynamic interactions
- **Real-time Updates**: Server-Sent Events (SSE) for targeted component reloading
- **Testing**: JUnit 5, Testcontainers, Spring Boot Test
- **Tools**: Lombok for boilerplate reduction

### Design Principles (GRASP)

The application follows **function-oriented modular architecture** organized around business capabilities:

- **Information Expert**: Assign responsibility to the class that has the information needed to fulfill it
- **Creator**: Assign responsibility for creating objects to classes that closely use those objects
- **Controller**: Keep controllers concise and focused - they should only handle HTTP concerns and delegate to business services
- **Low Coupling**: Minimize dependencies between modules through interfaces and dependency injection
- **High Cohesion**: Group related functionality within the same module

### Module Organization (by business function)
- **configurator**: Template/stage/step definitions, CSV import, participant responses
- **facilitation**: Sessions, participants, retrospective flow control
- **eventing**: Real-time SSE event streaming and notifications
- **auth**: Authentication (OIDC + guest mode with CookieAuthenticationToken)
- **web**: View controllers and template data services (Thymeleaf rendering)
- **common**: Shared utilities, exceptions, configurations

### Domain Model

**Core Entities:**
- **RetroSession**: Main entity representing a retrospective session with phases (CREATED → LOBBY → SET_THE_STAGE → GATHER_DATA → GENERATE_INSIGHTS → DECIDE_ACTIONS → CLOSE_RETRO → COMPLETED)
- **Participant**: Users participating in sessions with composite primary key (participantId + sessionId)
- **RetroTemplate/RetroStage/RetroStep**: Template system defining retrospective flow and activities
  - **RetroStep** is the core navigation unit - each step represents one page in the wizard flow
  - Steps use declarative configuration with ComponentType and pure JSON config
- **ParticipantResponse**: Flexible entity storing user inputs as JSON (responseData field)
- **RetroPhase**: Enum defining the lifecycle states of a retrospective
- **ComponentType**: Enum for UI components (MULTI_COLUMN_BOARD, RATING_SCALE, HISTOGRAM_CHART, GUIDANCE_MESSAGE, VISUAL_LAYOUT)
- **AdvancementTrigger**: Enum for when to allow advancement (FACILITATOR_CLICK, ALL_RESPONDED, TIMER_EXPIRES, AUTO)

### Controller Separation

The application maintains **strict separation** between three types of controllers:

- **View Controllers** (`*ViewController`): Handle Thymeleaf template rendering and web page navigation
- **API Controllers** (`*ApiController`): Handle REST API endpoints for data operations
- **Event Controllers** (`*EventController`): Handle Server-Sent Events (SSE) streaming

**Never mix responsibilities** - each controller type has a single purpose.

### Authentication System

Custom cookie-based authentication supporting two modes:

#### Authenticated Mode
- Users log in with OIDC credentials for persistent identity
- Session maintained across browser sessions
- USER role automatically assigned to OIDC authenticated users

#### Guest Mode
- Similar to Zoom/Teams guest participants
- No account required - enter display name only
- Temporary session-based identity
- Guest authentication handled via `CookieAuthenticationToken`

#### Facilitator Role
- The user who **creates** a lobby automatically becomes the **Facilitator**
- Facilitator has control over retrospective flow progression
- Only facilitator can advance participants to next phases/steps

---

## Real-time Collaboration (SSE)

### Event Types (RetroEvent.EventType)
- `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT` - Participant list updates
- `SESSION_STARTED` - Lobby → Active retro transition
- `STEP_ADVANCED` - Facilitator advances to next step
- `NOTE_ADDED` / `NOTE_UPDATED` / `NOTE_DELETED` - Response changes
- `VOTE_ADDED` / `VOTE_REMOVED` - Voting updates

### Multi-User Real-Time Collaboration Flow

When a participant submits a response, **ALL participants** in that session see the update in real-time:

1. **Client submits**: User fills form → HTMX POST to `/api/retro/{retroId}/step/{stepId}/{pattern}`
   - Form includes `hx-swap="none"` (no HTML returned to submitter)

2. **Server processes**:
   - Controller validates and delegates to ResponseService
   - ResponseService saves response to database
   - ResponseService publishes SSE event to ALL participants via EventService

3. **All clients update** (HTMX-driven, no JavaScript needed):
   - HTML elements have `hx-trigger="sse:note_added from:body"`
   - When SSE event arrives, HTMX automatically triggers GET request to refresh that component
   - HTMX swaps the fresh HTML fragment into place

**Example** (category lane auto-refresh):
```html
<div id="category-lane-Mad"
     hx-get="/retro/{retroId}/step/{stepId}/responses/categorical?category=Mad"
     hx-trigger="sse:note_added from:body, sse:note_updated from:body"
     hx-swap="innerHTML">
    <!-- Sticky notes render here -->
</div>
```

### Transactional Event Publishing

The application uses **transaction-aware event publishing** to prevent race conditions where SSE clients receive events before database changes are visible.

EventService uses Spring's `@TransactionalEventListener` to automatically delay event broadcasting until AFTER the transaction commits:

```java
@Service
public class EventService {
    private final ApplicationEventPublisher applicationEventPublisher;

    // Public API - automatically transaction-aware
    public void publish(RetroEvent<?> event) {
        applicationEventPublisher.publishEvent(event);
    }

    // Listener fires AFTER transaction commits
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetroEventAfterCommit(RetroEvent<?> event) {
        broadcastToRedis(event);  // Now safe to broadcast
    }
}
```

---

## Simplified Wizard Pattern Architecture

The system follows a **Wizard/Multi-Step Form pattern** where each RetroStep represents one page in a linear flow. This approach is declarative and configuration-driven.

### Core RetroStep Entity

```java
@Entity
class RetroStep {
    Long id;
    RetroStage retroStage;      // Which of 5 phases
    Integer orderIndex;          // Sequence within stage

    // What to display
    ComponentType componentType;  // MULTI_COLUMN_BOARD, RATING_SCALE, etc.

    @Type(JsonType.class)
    Map<String, Object> componentConfig;  // All UI configuration as JSON

    // When to advance
    AdvancementTrigger advancementTrigger;  // FACILITATOR_CLICK, ALL_RESPONDED, etc.
    Integer durationSeconds;

    // Facilitator guidance (shown in left sidebar)
    String guidance;
}
```

### Component Configuration Examples

All configuration is stored as pure JSON in the `componentConfig` field. No Java config classes needed.

**Multi-Column Board (Mad/Sad/Glad):**
```json
{
  "columns": [
    {"id": "mad", "title": "Mad", "emoji": "😠", "color": "#EF4444"},
    {"id": "sad", "title": "Sad", "emoji": "😢", "color": "#3B82F6"},
    {"id": "glad", "title": "Glad", "emoji": "😊", "color": "#10B981"}
  ],
  "capabilities": {
    "allowInput": true,
    "allowVoting": false,
    "allowMerging": false
  },
  "display": {
    "showVotes": false,
    "showAuthor": false
  },
  "cardConfig": {
    "maxLength": 500,
    "placeholder": "What made you feel this way?"
  }
}
```

**Rating Scale (Happiness Histogram):**
```json
{
  "min": 1,
  "max": 10,
  "step": 1,
  "labels": ["Unhappy", "Very Happy"],
  "inputType": "radio",
  "allowComment": true,
  "commentMaxLength": 500
}
```

### Facilitator Override Principle

Facilitators can ALWAYS advance - the system shows warnings but never blocks:

```html
<!-- Next button ALWAYS enabled for facilitator -->
<button th:if="${isFacilitator}"
        hx-post="/api/retro/{retroId}/next"
        class="btn-primary">
    Next
</button>

<!-- Warning shown if blocking condition not met -->
<div th:if="${!canAdvance}" class="warning">
    ⚠️ Only 3 of 5 participants responded.
    You can still proceed if needed.
</div>
```

**Why this matters**: Trust facilitator judgment over rigid system rules. Prevents bugs from blocking the entire team.

---

## UI/UX Design Philosophy

### Layout Structure
- **Golden header bar**: Clean progress indicators for 5 stages (Complete/In Progress/To Do)
- **Three-column layout**:
  - **Left sidebar**: Video/guidance area with overlay tooltips containing step-by-step facilitation instructions
  - **Center area**: Clean, focused main content for activities (minimal clutter)
  - **Right sidebar**: Participants list and results display (categorical responses, action points)

### Embedded Facilitation Coaching
- **App-driven facilitation**: The app acts as the facilitator, human facilitator just clicks "Next"
- **Flexible guidance tooltips**: Support for text, video, or audio guidance (starting with text)
- **Contextual instructions**: Guidance appears in small overlays, not cluttering the main interface
- **Quality boost without annoyance**: Detailed enough to improve facilitation quality, concise enough to avoid cognitive overload

### Design Philosophy
- **Clean and straightforward**: Professional appearance inspired by system-ui mockups
- **Minimal cognitive load**: Clear visual hierarchy, focused content areas, embedded guidance
- **Zero facilitation skills required**: Step-by-step instructions eliminate need for training

### Design Reference
The `system-ui/` folder contains UI design screenshots with mock data that illustrate the intended visual direction. Note: mockups serve as initial design inspiration but may become outdated as implementation evolves.

---

## Code Conventions

### Java Coding Standards
- Use Lombok annotations (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate
- Use `@Slf4j` for logging in all classes
- **Logging Policy**: DO NOT add INFO/WARN logs for debugging. Use DEBUG/TRACE level and adjust logging configuration. Only use INFO/WARN for genuinely important production events.
- Prefer constructor injection over field injection
- Use `@Service` for all business function implementations
- Keep business logic independent of Spring Boot framework code
- Use `@Transactional` for service methods that modify data
- Implement global exception handling with `@ControllerAdvice`
- Use UUID v7 for primary keys via custom `@GeneratedUuidV7` annotation
- **Always use imports for class types** - Never use fully-qualified class names in code

### Controller Separation Standards
- **Strict separation** between View, API, and Event controllers
- **View Controllers**: Only Thymeleaf rendering and web navigation
- **API Controllers**: Only REST endpoints returning JSON
- **Event Controllers**: Only SSE streaming for real-time updates
- **Never mix responsibilities**

### Testing Patterns
- Use Testcontainers for integration tests requiring database/Redis
- Mock external dependencies in unit tests
- Use `@SpringBootTest` with appropriate test slices
- **CRITICAL - DO NOT HIDE PROBLEMS WITH TIMEOUTS**: When tests fail with timeouts, investigate the root cause — do not increase timeout durations or use them to mask underlying problems.
- **Test failures are feedback** - they tell you something is wrong with the implementation, not the test
- **NEVER skip, disable, or ignore failing tests**: If a test fails, it is your responsibility to fix the root cause. Do not use `@Disabled`, `@Ignore`, `@Tag("flaky")`, or any mechanism to skip tests.
- **Flaky tests are bugs**: A flaky test indicates a real problem — race condition, missing synchronization, incorrect assumptions. Treat flaky tests as P1 bugs and fix immediately.
- **Test reliability is non-negotiable**: All tests must pass reliably on every run. If a test passes "most of the time", it is broken and must be fixed.

### Security Considerations
- Always validate user permissions before operations
- Use SecurityContext for authentication checks
- Handle both authenticated users and guest participants via `CookieAuthenticationToken`

---


## Feature Delivery & Quality Framework

This section defines how features MUST be delivered, verified, and handed off. ALL rules here are MANDATORY for every feature delivery.

### Delivery Pipeline

Every feature delivery MUST follow this 5-step process:

1. **PLAN**: User and AI discuss scope via Prometheus interview (~15 min). User defines what to build; AI creates the work plan.

2. **BUILD**: AI agents execute the plan autonomously. No user involvement during this phase. Agent writes BOTH implementation code AND test code (integration tests + Playwright E2E tests).

3. **VERIFY**: Automated gate — ALL of the following MUST pass:
   - `./mvnw clean test` — zero failures. This single command runs ALL tests: unit tests, integration tests, AND Playwright E2E tests. There is NO separate Playwright step.
   - BDD "Critical" scenarios from the Notion story guide test coverage (but NOT 1:1 mapping — see BDD Verification subsection below)
   - After all tests pass, AI agent MUST update the Notion story status to `Needs review` via MCP tool call: `mcp_notion-hosted_notion-update-page` with `command: update_properties`, property `Status: Needs review`

4. **REVIEW**: User reviews delivery artifacts:
   - Playwright screenshots/evidence (visual verification without running browser)
   - `git diff` of all changes
   - BDD verification report (which scenarios are covered, with evidence)
   - User moves Notion story to `Complete` after satisfaction, or requests changes

5. **MERGE**: User merges the feature branch with confidence. All gates passed, all evidence reviewed.

### Definition of Done

A feature is READY FOR REVIEW when ALL of the following conditions are true:

- [ ] All BDD "Critical" scenarios from the Notion story are covered by tests (NOT necessarily 1:1 — a single test may cover multiple scenarios)
- [ ] Every new API/View/Event endpoint has an integration test
- [ ] All frontend functionality is verified via Playwright E2E tests (tests MUST live in `src/test/java/.../integration/`)
- [ ] `./mvnw clean test` passes with zero failures (this runs everything: unit + integration + Playwright)
- [ ] No `@Disabled`, `@Ignore`, or `@Tag("flaky")` annotations added
- [ ] No suppressed errors (`@SuppressWarnings`, empty catch blocks, `as any`)
- [ ] Notion story status updated to `Needs review`
- [ ] Code follows existing patterns in this AGENTS.md (GRASP, controller separation, Lombok, etc.)

### Test Requirements

**Cross-reference**: See Testing Patterns in Code Conventions above for test reliability rules (no `@Disabled`, no flaky tests, etc.). The rules there are MANDATORY and are NOT repeated here.

Additional REQUIRED rules for feature delivery:

- **Integration tests**: REQUIRED for every new endpoint (API, View, or Event controller). Use `@SpringBootTest` + Testcontainers. Follow patterns in existing test classes.
- **Playwright E2E tests**: REQUIRED for every user-facing feature. Tests MUST live in `src/test/java/.../integration/` alongside existing tests (e.g., `SscRetroFlowTest`). MUST capture screenshot evidence.
- **Regression gate**: `./mvnw clean test` MUST show zero failures TOTAL — not just new tests passing, but ALL existing tests still passing.

**Definitions**:
- **User-facing feature**: Any change visible in the browser UI (new page, new component, changed behavior, new interaction). REQUIRES Playwright E2E tests.
- **Backend endpoint**: API endpoint, service logic, or data model change with no direct UI impact. REQUIRES integration test only (no Playwright required).
- **Pure refactor**: No behavior change. Existing tests MUST still pass. New tests ONLY if coverage gaps are discovered.

### BDD Verification

- Notion is the **source of truth** for BDD scenarios (declared in this file's header)
- Before BUILD begins, agent MUST fetch the Notion story and read ALL BDD scenarios
- Scenarios tagged `("Critical")` are the MANDATORY acceptance contract — every Critical scenario MUST be covered by at least one test
- There is NO required 1:1 mapping between BDD scenarios and Playwright tests. A single test class may verify multiple BDD scenarios. The goal is: all frontend functionality verified via Playwright, all Critical BDD scenarios covered.
- Non-critical scenarios SHOULD have test coverage but are NOT blocking delivery
- If a Notion story has NO BDD scenarios: agent MUST flag this to the user and MUST NOT proceed without clarification
- If a BDD scenario conflicts with technical constraints: agent MUST document the conflict and ask the user for resolution before proceeding

### Notion Workflow

- **Notion database**: User Stories (source of truth for stories and roadmap)
- **Status flow**: `To do` → `In progress` → `Needs review` → `Complete`
- AI agent MUST set status to `In progress` at the start of BUILD phase
- AI agent MUST set status to `Needs review` at the end of VERIFY phase (after ALL gates pass)
- User sets status to `Complete` after REVIEW phase approval
- If Notion MCP is unavailable: delivery proceeds, agent MUST inform user to update Notion status manually
- **Tool call pattern**: `mcp_notion-hosted_notion-update-page(page_id, command: update_properties, properties: {Status: 'Needs review'})`

### Edge Cases

- **Story with no BDD scenarios**: MUST NOT build. Agent MUST flag this to user. BDD is the acceptance contract.
- **Pure backend story (no UI)**: Integration tests only. No Playwright E2E required.
- **Story spanning multiple commits**: Set `Needs review` ONLY on final delivery, NOT on partial deliveries.
- **Notion MCP unavailable**: Proceed with delivery. Agent MUST notify user to update status manually.
- **BDD scenario technically infeasible**: Agent MUST document the conflict and ask user for resolution before proceeding.

---

## Key Technical Rules

### Thymeleaf Reserved Words
- Never use `session` as variable name (reserved in web context). Always use `retroSession`.

### SpEL Expressions
- Avoid complex SpEL in templates. Use Thymeleaf utilities (`#aggregates.sum()`, `#numbers.sequence()`) instead of stream operations.

### DTO Pattern
- Always convert entities to DTOs before template rendering
- DTOs implement `ComponentResponseDto.toResponseData()`

### Error Handling
- No null returns from services - throw exceptions instead (fail-fast principle)
- Custom exceptions: `InvalidSessionStateException`, `InvalidStepException`, `VoteLimitExceededException`
- Authorization-first validation (prevents session ID enumeration)

### Type Safety
- **Never suppress type errors** with `as any`, `@ts-ignore`, `@ts-expect-error`
- **Never use empty catch blocks** `catch(e) {}`

---

## Build and Development Commands

### Maven Commands
- **Build the project**: `mvn clean compile`
- **Run tests**: `mvn test`
- **Run specific test**: `mvn test -Dtest=ClassName#methodName`
- **Run the application**: `mvn spring-boot:run -Dspring-boot.run.profiles=import`
- **Package the application**: `mvn clean package`

### Docker Dependencies
- **Start services** (PostgreSQL + Redis): `docker compose up -d`
- **Stop services**: `docker compose down`

### Testing with Testcontainers
- Tests use Testcontainers for PostgreSQL and Redis - no manual setup needed
- Containers managed automatically during test execution
- Simply run `mvn test` to execute integration tests

### Important Notes
- **Always use `import` profile** when running the application
- This loads templates, retro stages, and steps from CSV files in `src/main/resources/`
- Without the `import` profile, the database will be empty

---

## Claude-Specific Instructions

### Application Management

**User runs the application in a separate iTerm2 terminal tab.**

#### How It Works
- User runs: `mvn spring-boot:run -Dspring-boot.run.profiles=import` in dedicated terminal
- Spring Boot logs to **both** console (for user) **and** `/tmp/facilitator.log` (for Claude)
- User sees live logs in their terminal, Claude reads from log file

#### Starting the Application
1. **Check if running**:
   ```bash
   curl -s http://localhost:8080/ > /dev/null && echo "✅ Running" || echo "❌ Not running"
   ```

2. **If not running**, tell the user:
   > "Please start the application in a separate iTerm2 tab:
   > ```bash
   > mvn spring-boot:run -Dspring-boot.run.profiles=import
   > ```"

3. Wait for user confirmation before proceeding

#### Monitoring Logs (Claude)
```bash
# Follow live logs
tail -f /tmp/facilitator.log

# Filter for specific events
tail -f /tmp/facilitator.log | grep -i "ERROR\|Publishing\|step_advanced"

# Check last N lines
tail -50 /tmp/facilitator.log

# Search for patterns
grep "SSE" /tmp/facilitator.log | tail -20
```

#### Stopping the Application
```bash
pkill -f "spring-boot:run"
```

#### Checking Status
```bash
# Quick HTTP check
curl -s http://localhost:8080/ > /dev/null && echo "Running" || echo "Not running"

# Port check
lsof -i :8080

# Process check
ps aux | grep -i "[m]vn spring-boot:run"
```

### Task Management
- Use the TodoWrite tool to track multi-step tasks
- Mark tasks as completed immediately after finishing
- Only have one task in_progress at a time

### Code Generation Patterns
- Follow Spring MVC patterns (not WebFlux - we use standard Spring MVC)
- Use Lombok annotations consistently
- Implement proper error handling with custom exceptions
- Use UUID v7 with `@GeneratedUuidV7` annotation for primary keys

### Planning Before Coding
- Always plan before generating code
- Think like a senior developer about implications
- Consider architecture, maintainability, and future extensibility
- Refer to GRASP principles and domain-driven design

### Important Reminders
- Do what has been asked; nothing more, nothing less
- NEVER create files unless absolutely necessary
- ALWAYS prefer editing existing files to creating new ones
- NEVER proactively create documentation files unless explicitly requested
- Make sure that MCP is used for browser tests to test the facilitation flow if it works in an 'agent' / automated way so that I dont need to do this myself
