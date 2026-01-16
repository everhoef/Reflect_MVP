# CLAUDE.md

This file provides comprehensive guidance to Claude Code when working with the Facilitator codebase - a Spring Boot application for team retrospectives.

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

The goal: Give managers actionable intelligence to support their teams proactively, not reactively.

---

## How Retrospectives Work (Real-World Patterns)

Understanding how retrospectives work in practice helps translate human facilitation into digital components.

### Five-Phase Retrospective Structure

All effective retrospectives follow this proven approach:

1. **Set the Stage (Check-in)**: Establish psychological safety and emotional baseline
   - Example: Happiness Histogram (rate 1-10 with explanation)
   - Goal: Gauge team mood, open discussion channels
   - Digital translation: RATING pattern with privacy controls

2. **Gather Data**: Collect specific events and observations
   - Example: Mad, Sad, Glad (categorize emotions into 3 columns)
   - Goal: Capture both positive and challenging moments for shared understanding
   - Digital translation: CATEGORICAL pattern with sticky notes

3. **Generate Insights**: Analyze data for patterns and root causes
   - Example: The Original Four (What did we do well? What did we learn? What should we do differently? What still puzzles us?)
   - Goal: Move beyond symptoms to real issues
   - Digital translation: FREEFORM pattern with timed responses and discussion

4. **Decide Actions**: Identify specific improvements to try next sprint
   - Example: Circle of Questions (chained Q&A building on each other)
   - Goal: Prioritize impactful, realistic actions → SMART action points
   - Digital translation: Chained Q&A system + action item templates

5. **Close Retro (Closure)**: Reflect on the session and appreciate the team
   - Example: Feedback Door Smileys (rate the retro experience)
   - Goal: Continuously improve retrospective process
   - Digital translation: Simple feedback collection for meta-analysis

### Retrospective Flow Patterns

Based on proven facilitation techniques, retrospectives follow these interaction patterns:

#### Pattern 1: Private Input → Public Reveal → Discussion
**Used in**: Happiness Histogram, Mad Sad Glad
```
1. Facilitator explains activity (app shows guidance tooltip)
2. Participants submit responses privately (others can't see)
3. Facilitator reveals all results simultaneously (click "Next Step")
4. Team discusses patterns and observations (guided prompts)
```
**Why this works**: Privacy reduces groupthink, revelation creates shared context, discussion generates insights.

#### Pattern 2: Timed Sequential Responses → Discussion
**Used in**: The Original Four
```
1. Facilitator poses question (app shows timer: 2 minutes)
2. Participants post keywords/phrases (countdown visible)
3. After time expires, 5-minute discussion (guided questions)
4. Repeat for each question
```
**Why this works**: Time limits force focus, sequential structure prevents overwhelm, guided discussion ensures depth.

#### Pattern 3: Chained Question-Answer
**Used in**: Circle of Questions
```
1. Facilitator posts first question
2. Participant A answers and posts new question for next person
3. Participant B answers A's question and posts new question
4. Continue until all participants have participated
5. Team summarizes patterns/common themes (app highlights consensus)
```
**Why this works**: Chaining builds on ideas, everyone participates equally, patterns emerge organically.

#### Pattern 4: Clustering and Naming
**Used in**: Mad Sad Glad, Start/Stop/Continue
```
1. Create sticky notes (private or public depending on step)
2. Reveal all notes (app unblurs content)
3. Participants cluster similar items (drag-and-drop)
4. Team names clusters together (collaborative naming)
5. Discuss patterns and meanings (app highlights high-vote clusters)
```
**Why this works**: Visual grouping reveals patterns, collaborative naming builds shared understanding, voting prioritizes focus.

### Multi-Step Activity Structure

Complex retrospective activities require multiple sequential steps within a single stage:

**Example: Happiness Histogram (5 steps)**
1. **INSTRUCTION**: Facilitator explains activity, app shows rating scale lanes
2. **ACTIVITY (PRIVATE)**: Participants select rating 1-10 and add optional comment (private visibility)
3. **REVEAL**: Facilitator reveals overview, histogram visualization appears
4. **DISCUSSION**: Participants add thoughts in comments section (guided prompts)
5. **WRAP-UP**: Each participant shares one insight (app prompts for voice-over or text)

**Example: Mad Sad Glad (7 steps)**
1. **INSTRUCTION**: App shows 3-column structure (Mad, Sad, Glad) with emoji headers
2. **ACTIVITY (PRIVATE)**: Participants add sticky notes in 5 minutes (timer shown, notes blurred to others)
3. **REVEAL**: App unblurs all notes, everyone can read
4. **CLUSTERING**: Participants drag notes into groups (real-time collaboration)
5. **NAMING**: Team names clusters together (collaborative text fields)
6. **DISCUSSION**: Team debriefs patterns (app highlights most-mentioned themes)
7. **WRAP-UP**: Facilitator summarizes key takeaways (app suggests summary based on clusters)

### Privacy and Visibility Controls

Activities require different visibility modes to prevent groupthink and ensure psychological safety:
- **PRIVATE**: Participants see only their own responses, others see "Waiting for others..." or blurred content
- **PUBLIC**: All responses visible to everyone in real-time

Facilitator controls when to transition from PRIVATE to PUBLIC visibility with a single click.

### Generating SMART Action Points

The app guides teams from vague intentions to SMART action points:

**Bad action point**: "Improve communication"
**SMART transformation**:
- **Specific**: What exactly? → "Daily standup sync with design team"
- **Measurable**: How to verify? → "Attendance logged, 5 days/week"
- **Achievable**: Realistic? → "Yes, 15-min meeting"
- **Relevant**: Why important? → "Reduce design rework from miscommunication"
- **Time-bound**: Deadline? → "Start next Monday, review in 2 weeks"

**SMART action point**: "Start daily 15-min standup with design team every morning at 9:30am, track attendance, review effectiveness in 2 weeks"

The app provides templates and validation to ensure action points meet SMART criteria before saving.

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

### Real-time Updates with SSE

The application uses **Server-Sent Events (SSE)** to provide targeted UI component updates without full page reloads.

**Architecture**: EventService manages SSE connections and broadcasts targeted events to specific participants or entire sessions.

**Event Types** (defined in `RetroEvent.EventType`):
- `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT` - Participant list updates
- `SESSION_STARTED` - Lobby → Active retro transition
- `STEP_ADVANCED` - Facilitator advances to next step
- `NOTE_ADDED` / `NOTE_UPDATED` / `NOTE_DELETED` - Response changes (categorical/rating/freeform)
- `VOTE_ADDED` / `VOTE_REMOVED` - Voting updates (future)
- `GROUP_CREATED` / `GROUP_UPDATED` / `GROUP_DELETED` - Clustering updates (future)

**Multi-User Real-Time Collaboration Flow:**

When a participant submits a response (categorical/rating/freeform), **ALL participants** in that session must see the update in real-time:

1. **Client submits**: User fills form → HTMX POST to `/api/retro/{retroId}/step/{stepId}/{pattern}`
   - Form includes `hx-swap="none"` (no HTML returned to submitter)

2. **Server processes**:
   - Controller validates and delegates to ResponseService
   - ResponseService saves response to database
   - ResponseService publishes SSE event (e.g., `NOTE_ADDED`) to ALL participants in the session via EventService

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

**Key Principle**: Use HTMX's built-in SSE support to minimize JavaScript. SSE events trigger HTMX to fetch fresh HTML fragments, ensuring all participants see the same state simultaneously.

### Transactional Event Publishing

The application uses **transaction-aware event publishing** to prevent race conditions where SSE clients receive events before database changes are visible.

**The Problem**: When events are published directly to Redis within a `@Transactional` method, they broadcast immediately - before the transaction commits. This causes clients to query stale database state:

```java
@Transactional
public void removeParticipantFromSession(Participant participant) {
    participantRepository.save(participant);      // 1. DB write (not committed yet)
    eventService.publish(event);                  // 2. Redis broadcast (immediate!)
}                                                // 3. Transaction commits HERE (too late!)
```

**The Solution**: EventService uses Spring's `@TransactionalEventListener` to automatically delay event broadcasting until AFTER the transaction commits:

**How It Works**:
1. Service calls `eventService.publish(retroEvent)` within a `@Transactional` method
2. EventService delegates to Spring's `ApplicationEventPublisher`
3. Spring queues the event until the transaction commits
4. Transaction commits → database changes are visible
5. `@TransactionalEventListener(AFTER_COMMIT)` fires in EventService
6. EventService broadcasts event to Redis → SSE clients receive it
7. Clients query database and see consistent data ✅

**Implementation** (in EventService.java):
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

    // Internal Redis broadcasting
    private void broadcastToRedis(RetroEvent<?> event) {
        redisTemplate.convertAndSend("retro:" + event.retroId(), event);
    }
}
```

**Multi-Instance (K8s) Compatibility**:
- `ApplicationEvent` is JVM-local (not distributed across pods)
- Each pod's `@TransactionalEventListener` publishes to Redis after ITS transaction commits
- Redis broadcasts to ALL pods' SSE clients
- Database consistency guaranteed: clients always query after commit

**Usage** (service code remains unchanged):
```java
@Transactional
public void removeParticipantFromSession(Participant participant) {
    participant.setStatus(ParticipantStatus.LEFT);
    participantRepository.save(participant);  // DB write

    // Same API as always - automatically transaction-aware
    eventService.publish(RetroEvent.participantLeft(sessionId, displayName));
}  // Transaction commits → listener fires → Redis broadcasts
```

**Benefits**:
- Zero race conditions - events always published after database changes are visible
- Works seamlessly with multi-instance Kubernetes deployments
- No service code changes needed - same `eventService.publish()` API
- Consistent database state across all SSE clients

### Design Principles

The application follows **function-oriented modular architecture** organized around business capabilities:

#### GRASP Principles
- **Information Expert**: Assign responsibility to the class that has the information needed to fulfill it
- **Creator**: Assign responsibility for creating objects to classes that closely use those objects
- **Controller**: Keep controllers concise and focused - they should only handle HTTP concerns and delegate to business services
- **Low Coupling**: Minimize dependencies between modules through interfaces and dependency injection
- **High Cohesion**: Group related functionality within the same module

#### Module Organization (by business function, not technical layers)
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
  - Steps use declarative configuration with ComponentType and pure JSON config (see Simplified Wizard Pattern Architecture below)
- **ParticipantResponse**: Flexible entity storing user inputs as JSON (responseData field)
- **RetroPhase**: Enum defining the lifecycle states of a retrospective
- **ComponentType**: Enum for UI components (MULTI_COLUMN_BOARD, RATING_SCALE, HISTOGRAM_CHART, GUIDANCE_MESSAGE, VISUAL_LAYOUT)
- **AdvancementTrigger**: Enum for when to allow advancement (FACILITATOR_CLICK, ALL_RESPONDED, TIMER_EXPIRES, AUTO)

### Controller Separation

The application maintains **strict separation** between three types of controllers:

- **View Controllers** (`*ViewController`): Handle Thymeleaf template rendering and web page navigation
  - Serve HTML pages with server-side rendering
  - Handle form submissions and redirects
  - Focus on user interface concerns

- **API Controllers** (`*ApiController`): Handle REST API endpoints for data operations
  - Return JSON responses for AJAX calls
  - Handle CRUD operations
  - Focus on data exchange

- **Event Controllers** (`*EventController`): Handle Server-Sent Events (SSE) streaming
  - Manage real-time communication streams
  - Broadcast updates to connected clients
  - Focus on real-time synchronization

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

### Simplified Wizard Pattern Architecture

The system follows a **Wizard/Multi-Step Form pattern** where each RetroStep represents one page in a linear flow. This approach is declarative and configuration-driven, supporting any retrospective format through pure JSON configuration.

#### Core RetroStep Entity

```java
@Entity
class RetroStep {
    Long id;
    RetroStage retroStage;      // Which of 5 phases (SET_THE_STAGE, GATHER_DATA, etc.)
    Integer orderIndex;          // Sequence within stage

    // What to display
    ComponentType componentType;  // MULTI_COLUMN_BOARD, RATING_SCALE, HISTOGRAM_CHART, GUIDANCE_MESSAGE

    @Type(JsonType.class)
    Map<String, Object> componentConfig;  // All UI configuration as JSON

    // When to advance
    AdvancementTrigger advancementTrigger;  // FACILITATOR_CLICK, ALL_RESPONDED, TIMER_EXPIRES, AUTO
    Integer durationSeconds;

    // Facilitator guidance (shown in left sidebar)
    String guidance;
}
```

#### ParticipantResponse Entity (Simplified)

```java
@Entity
class ParticipantResponse {
    UUID id;
    UUID participantId;
    UUID sessionId;
    Long stepId;                 // References RetroStep

    @Type(JsonType.class)
    Map<String, Object> responseData;  // All response data as JSON

    Boolean isVisible;
    LocalDateTime submittedAt;
}
```

**Response data examples:**
- **Rating**: `{"rating": 8, "comment": "Great sprint!"}`
- **Categorical**: `{"category": "mad", "content": "Slow deployments"}`
- **Freeform**: `{"content": "Celebrate more wins"}`

#### Two Core Enums

**ComponentType** - Defines which UI component to render:
```java
public enum ComponentType {
    MULTI_COLUMN_BOARD,  // Card-based columns (Mad/Sad/Glad, Start/Stop/Continue)
    RATING_SCALE,        // Numeric rating input (Happiness Histogram, ROTI)
    HISTOGRAM_CHART,     // Rating distribution visualization
    GUIDANCE_MESSAGE,    // Text instructions/explanations
    VISUAL_LAYOUT        // Metaphorical layouts (Sailboat, Tree, etc.) - Phase 2
}
```

**AdvancementTrigger** - Defines when the step can advance:
```java
public enum AdvancementTrigger {
    FACILITATOR_CLICK,   // Facilitator decides when to proceed
    ALL_RESPONDED,       // Wait for all participants to submit responses
    TIMER_EXPIRES,       // Auto-advance after durationSeconds
    AUTO                 // Advance immediately (system steps, no user action)
}
```

#### Component Configuration Examples

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

**Histogram Chart (Display Results):**
```json
{
  "min": 1,
  "max": 10,
  "showComments": true,
  "groupBy": "rating"
}
```

**Templates access JSON directly via Thymeleaf:**
```html
<div th:each="column : ${config.columns}">
    <h3 th:text="${column.title}">Mad</h3>
    <form th:if="${config.capabilities.allowInput}">
        <textarea th:maxlength="${config.cardConfig.maxLength}"></textarea>
    </form>
</div>
```

#### Participation Tracking

**Reuse existing ParticipantResponse table** - no separate tracking needed:

```java
// Check if all participants responded
public boolean allParticipantsResponded(UUID sessionId, Long stepId) {
    int activeParticipants = participantRepository.countBySessionIdAndStatus(sessionId, ACTIVE);
    int respondedCount = responseRepository.countDistinctParticipantsBySessionAndStep(sessionId, stepId);
    return respondedCount >= activeParticipants;
}
```

#### Step Advancement Logic

**RetroSessionService** manages all advancement logic:

```java
@Service
public class RetroSessionService {

    // Check if current step can advance based on AdvancementTrigger
    public boolean canAdvanceCurrentStep(UUID sessionId) {
        RetroStep currentStep = getCurrentStep(sessionId);

        return switch(currentStep.getAdvancementTrigger()) {
            case FACILITATOR_CLICK -> true;
            case ALL_RESPONDED -> allParticipantsResponded(sessionId, currentStep.getId());
            case TIMER_EXPIRES -> timerHasExpired(sessionId, currentStep);
            case AUTO -> true;
        };
    }

    // Advance to next step (facilitator can ALWAYS override)
    public void advanceStep(UUID sessionId, boolean isFacilitator) {
        if (isFacilitator || canAdvanceCurrentStep(sessionId)) {
            advanceToNextStep(sessionId);
            eventService.publish(RetroEvent.stepAdvanced(sessionId));
        }
    }
}
```

**Facilitator Override Principle:**

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

#### Facilitator Controls in Layout

Facilitator-specific controls (Next button, progress tracker) are defined in the **layout template**, not in step configuration. They are the same for every step.

```html
<div class="retro-layout">
  <!-- Main activity area (everyone sees this) -->
  <div class="activity-area">
    <div th:replace="~{fragments/components/${componentType} :: content}"></div>
  </div>

  <!-- Facilitator controls (only facilitator sees this) -->
  <div th:if="${isFacilitator}" class="facilitator-controls">
    <!-- Progress tracker -->
    <div class="progress">
      <span th:text="${participantsResponded} + ' of ' + ${totalParticipants} + ' responded'">
        3 of 5 responded
      </span>
    </div>

    <!-- Next button (always enabled) -->
    <button hx-post="/api/retro/{retroId}/next">Next</button>

    <!-- Optional warning -->
    <div th:if="${!canAdvance}" class="warning">
      ⚠️ Not all participants responded. You can still proceed if needed.
    </div>
  </div>
</div>
```

**Why this works**: Configuration drives behavior through pure JSON, components are reusable, and new retrospective formats can be added without code changes - just new CSV configurations and JSON config.

### UI/UX Design Philosophy

Based on UI design screenshots in the `system-ui/` folder, the interface follows these principles:

#### Layout Structure
- **Golden header bar**: Clean progress indicators for 5 stages (Complete/In Progress/To Do)
- **Three-column layout**:
  - **Left sidebar**: Video/guidance area with overlay tooltips containing step-by-step facilitation instructions
  - **Center area**: Clean, focused main content for activities (minimal clutter)
  - **Right sidebar**: Participants list and results display (categorical responses, action points)

#### Embedded Facilitation Coaching
- **App-driven facilitation**: The app acts as the facilitator, human facilitator just clicks "Next"
- **Flexible guidance tooltips**: Support for text, video, or audio guidance (starting with text)
- **Contextual instructions**: Guidance appears in small overlays, not cluttering the main interface
- **Quality boost without annoyance**: Detailed enough to improve facilitation quality, concise enough to avoid cognitive overload

#### Design Philosophy
- **Clean and straightforward**: Professional appearance inspired by system-ui mockups
- **Minimal cognitive load**: Clear visual hierarchy, focused content areas, embedded guidance
- **Zero facilitation skills required**: Step-by-step instructions eliminate need for training

#### Design Reference
The `system-ui/` folder contains UI design screenshots with mock data that illustrate the intended visual direction:
- Visual layout and component structure
- Three-column layout implementation
- Progress indicator styling
- Guidance tooltip placement
- Activity templates (categorical, rating, freeform)
- Participant list and results display patterns

**Note**: These mockups serve as initial design inspiration, but will gradually become outdated as implementation evolves. During development, practical considerations and user feedback may lead to design decisions that diverge from the original mockups. The actual implementation takes precedence over the mockups when conflicts arise.

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

## Code Conventions

### Java Coding Standards
- Use Lombok annotations (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate
- Use `@Slf4j` for logging in all classes
- **Logging Policy**: DO NOT add INFO/WARN logs to production code for debugging purposes. Instead, use DEBUG or TRACE level logs and temporarily adjust the logging configuration in `application.yaml` (test or main) to enable them. Only use INFO/WARN for genuinely important production events (e.g., session created, user joined, major state transitions). Reserve ERROR for actual error conditions.
- Prefer constructor injection over field injection
- Use `@Service` for all business function implementations
- Keep business logic independent of Spring Boot framework code
- Use `@Transactional` for service methods that modify data
- Implement global exception handling with `@ControllerAdvice`
- Use UUID v7 for primary keys via custom `@GeneratedUuidV7` annotation
- **Always use imports for class types** - Never use fully-qualified class names like `direct.reflect.facilitator.configurator.ComponentType` or `direct.reflect.facilitator.configurator.RetroStage` in code. Exception: classes in the same package don't need imports.

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
- Handle both authenticated users and guest participants in tests
- **CRITICAL - DO NOT HIDE PROBLEMS WITH TIMEOUTS**: When tests fail with timeouts, DO NOT simply increase the timeout duration. Increasing timeouts only masks the underlying problem and makes tests slower. Instead:
  1. Investigate WHY the expected behavior isn't happening
  2. Check server logs to confirm the server-side behavior is correct
  3. Debug the client-side to understand what's actually being rendered/received
  4. Fix the root cause (e.g., missing event listeners, incorrect selectors, race conditions)
  5. Only increase timeouts if you've confirmed the behavior is correct but legitimately needs more time
- **Test failures are feedback** - they tell you something is wrong with the implementation, not the test

### Security Considerations
- Always validate user permissions before operations
- Use SecurityContext for authentication checks
- Handle both authenticated users and guest participants via `CookieAuthenticationToken`

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
User will see graceful shutdown in their terminal tab.

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