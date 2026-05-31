# AGENTS.md

This file provides guidance to AI agents (Claude, Cursor, etc.) when working with the Facilitator codebase - a full-stack retrospective platform with a Spring Boot backend and a React/Vite frontend.

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

Spring Boot 4.0.0 application built with Java 25, Spring MVC (not WebFlux), Spring Security, Spring Data JPA, PostgreSQL, Redis, and a React + Vite + TypeScript frontend. The React SPA serves all UI; the backend is purely API-based (REST + SSE) with server-side auth redirects for guest login and logout.

### Core Technology Stack
- **Backend**: Spring Boot 4.0.0, **Spring MVC** (Web MVC, not WebFlux), Spring Security, Spring Data JPA
- **Database**: PostgreSQL 17 with Hibernate
- **Session Management**: Redis for session storage
- **Frontend**: React 19 + Vite + TypeScript
- **Frontend libraries**: React Router, TanStack Query, Tailwind CSS v4, shadcn/ui, Zustand, React Hook Form, Zod, dnd-kit
- **Real-time Updates**: Server-Sent Events (SSE) consumed by the React frontend
- **Testing**: JUnit 5, Testcontainers, Spring Boot Test, Playwright, Vitest
- **Type generation**: TypeScript types are generated from backend models and OpenAPI
- **Tools**: Lombok for boilerplate reduction

### Design Principles (GRASP)

The application follows **function-oriented modular architecture** organized around business capabilities:

- **Information Expert**: Assign responsibility to the class that has the information needed to fulfill it
- **Creator**: Assign responsibility for creating objects to classes that closely use those objects
- **Controller**: Keep controllers concise and focused - they should only handle HTTP concerns and delegate to business services
- **Low Coupling**: Minimize dependencies between modules through interfaces and dependency injection
- **High Cohesion**: Group related functionality within the same module

### Module Organization, current implemented structure
- **Business domains**
  - **facilitation**: The most fully split backend module. Current capability packages are `session/`, `participant/`, `response/`, `actions/`, `clustering/`, and `escalation/`, plus shared `facilitation/dto/` for cross-capability response wrappers that have not been moved deeper yet.
  - **configurator**: Still mostly flat today. Templates, stages, steps, CSV import, repositories, and component contract enums still live directly under `configurator/`.
  - **organization**: Still mostly flat today. Organizations, teams, memberships, repositories, DTOs, and services still live directly under `organization/`.
- **Support modules**
  - **auth**: Partially layered today. `AuthController`, `AuthApiController`, and `AuthService` are still flat under `auth/`, while infrastructure moved under `auth/infrastructure/security/` and `auth/infrastructure/session/`.
  - **eventing**: Partially layered today. `EventService`, `RetroEventController`, `RetroEvent`, and `RetroSseEnvelope` are still flat under `eventing/`, while Redis wiring lives under `eventing/infrastructure/redis/`.
- **Shared kernel and app shell**
  - **common**: Now reduced to `common/ids/` only. Keep it tiny and policy-free.
  - **web**: Already split into `web/api/` and `web/infrastructure/` for shared HTTP glue such as `ApiExceptionHandler` and MVC config.

The original refactor plan aimed for broader `api`, `application`, `domain`, and `infrastructure` layering across every backend module. That target is only partially realized. Treat the package tree above as the source of truth for current work.

### Pragmatic Module and Layer Guardrails

- Prefer `api`, `application`, `domain`, and `infrastructure` as the internal layer vocabulary when a module already uses that split, or when a task is explicitly extending an existing seam.
- Do not create empty layer packages just for symmetry. Flat modules such as `configurator/` and `organization/` are still valid current structure.
- Keep dependency direction clear where layered packages already exist: `api` calls owned application surfaces, application coordinates domain behavior, and infrastructure supports the owning module.
- Business domains may call support modules only through narrow owned surfaces. Support modules carry transport, identity, and integration mechanics, not product rules.
- Do not import another module's repositories or JPA entities directly. Prefer a small query or service surface owned by the source module.
- `common` must stay tiny, generic, and dependency-light. In current code that means `common/ids/`, not a new dumping ground.
- When adding backend code, extend the structure that already exists. New facilitation work should usually land in one of the capability packages. New auth, eventing, configurator, or organization work should follow the current package shape unless the task explicitly finishes a deeper package move.

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

The application maintains **strict separation** between controller types:

- **Auth Controller** (`AuthController`): Handles guest login and logout via server-side redirects
- **API Controllers** (`*ApiController`): Handle REST API endpoints returning JSON for the React frontend
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

## Frontend Architecture & Patterns

The frontend treats the React component tree similarly to how a DevOps engineer treats a cluster: state is managed centrally, and components are decoupled micro-units that discover each other dynamically.

### Current frontend structure, implemented truth

- The frontend is still mostly organized by type, not by full feature module.
- Current source-of-truth directories are:
  - `pages/` for route-level page shells and composition
  - `components/retro/` for retro activity UI and facilitation-specific widgets
  - `components/ui/` for generic UI primitives
  - `hooks/api/` for API and TanStack Query hooks
  - `store/` for the current Zustand stores
  - `lib/` for shared helpers and the API client
  - `types/` for shared and generated TypeScript types
- The broader `App/`, `modules/`, and `shared/` tree from the original refactor proposal is not the current implemented structure. Do not write docs or new code as if `frontend/src/modules`, `frontend/src/shared`, or `frontend/src/app` already exist.
- Current frontend boundary enforcement is intentionally narrow. It only guards the directories that really exist today, not the broader aspirational module split from the plan.

### State Management (Zustand)
We use **Zustand** as our global state manager (currently `frontend/src/store/assistantStore.ts` and `frontend/src/store/retroStore.ts`). 
- **DevOps Analogy**: Think of Zustand as an in-memory Redis cache or `etcd` for the browser. Instead of passing data down through a deep tree of components (which is like passing configuration through 10 layers of bash scripts), components can simply subscribe directly to the "cache".
- **Usage**: Use Zustand for state that needs to be accessed by wildly different parts of the application (like the Assistant/Guidance state, which affects the sidebar, coachmarks, and main content simultaneously).
- **Rule**: Do not put *everything* in Zustand. Only global/cross-cutting concerns. Local UI state (like whether a dropdown is open) belongs in local component state (`useState`).

### Component Decoupling via Data Attributes
To prevent UI components from becoming heavily coupled, we use HTML `data-*` attributes as a "Service Discovery" mechanism.
- **Example**: The `Coachmark` (tooltip) component needs to point to a specific button. Instead of wrapping the button in a complicated higher-order component, we simply add `data-coachmark="next-step"` to the button.
- **DevOps Analogy**: This is identical to Kubernetes pod labels (`app=frontend`). The Coachmark acts like a Sidecar or DaemonSet that queries the DOM (the orchestrator) for an element with that label, calculates its X/Y coordinates, and renders itself there.
- **How to add new Coachmarks**: 
  1. Add `data-coachmark="your-new-anchor"` to any UI element.
  2. Render a `<Coachmark anchorId="your-new-anchor">` somewhere near the top-level page layout. The Coachmark will automatically "find" the anchor.

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

1. **Client submits**: The React frontend sends a POST to `/api/retro/{retroId}/step/{stepId}/...` via fetch/TanStack Query.

2. **Server processes**:
   - Controller validates and delegates to ResponseService
   - ResponseService saves response to database
   - ResponseService publishes SSE event to ALL participants via EventService

3. **All clients update**: The React frontend subscribes to the SSE stream. When an event arrives, it re-fetches the relevant data and re-renders the affected components.

**Principle**: The backend owns persistence and event broadcasting; the React frontend owns rendering for active retro flows.

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

Facilitators can ALWAYS advance - the system shows warnings but never blocks. The React frontend should always render the facilitator's "Next" control, while still warning when recommended preconditions have not been met.

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
- **Clean and straightforward**: Professional appearance with clear visual hierarchy
- **Minimal cognitive load**: Clear visual hierarchy, focused content areas, embedded guidance
- **Zero facilitation skills required**: Step-by-step instructions eliminate need for training

## Code Conventions

### Java Coding Standards
- Use Lombok by default for Java boilerplate elimination when possible. Prefer Lombok annotations such as `@Getter`, `@Setter`, `@Data`, `@RequiredArgsConstructor`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`, and `@Slf4j` unless custom behavior is required.
- Do not hand-write getters, setters, constructors, or logger fields when Lombok can express the intent clearly.
- Use `@Slf4j` for logging in all classes unless a class genuinely requires a different logger setup.
- **Logging Policy**: DO NOT add INFO/WARN logs for debugging. Use DEBUG/TRACE level and adjust logging configuration. Only use INFO/WARN for genuinely important production events.
- Prefer constructor injection over field injection
- Use `@Service` for all business function implementations
- Keep business logic independent of Spring Boot framework code
- Use `@Transactional` for service methods that modify data
- Implement global exception handling with `@ControllerAdvice`
- Use UUID v7 for primary keys via custom `@GeneratedUuidV7` annotation
- **Always use imports for class types** - Never use fully-qualified class names in code

### Controller Separation Standards
- **Strict separation** between Auth, API, and Event controllers
- **Auth Controller**: Only guest login/logout with server-side redirects
- **API Controllers**: Only REST endpoints returning JSON for the React frontend
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

#### Test Ownership and Multi-User Verification

- Lower layers own non-UI correctness where possible. Validation, authorization matrices, persistence invariants, and service guards should stay below the browser layer unless the browser is the only truthful place to prove them.
- Module-aligned tests should live under the owning top-level package. Extend an existing owner when the workflow or contract matches. Create a new test file only when ownership, runtime, or layer genuinely differs.
- Browser regressions stay narrow and high-value. Use them for collaboration, SSE propagation, and DOM behavior that lower layers cannot prove honestly.
- Isolated browser contexts or profiles are the canonical multi-user verification strategy. Shared tabs in one browser context are not valid multi-user evidence.
- The only approved fallback is one browser actor plus isolated API actors, and only when true browser isolation is unavailable in the current tool or runtime. This fallback does not replace real browser-to-browser collaboration coverage when isolated contexts are available.

#### Test Package Architecture

The test package structure mirrors `src/main/java` for unit, component, and integration tests. Browser journeys live in their own end-to-end package:

- **`e2e/`** — Playwright/browser end-to-end tests only. Shared browser helpers live under `e2e/support/`, and browser suites extend `BaseEndToEndTest`.
- **`facilitation/`** — Module-owned unit, component, and integration tests for facilitation. Prefer the capability packages `session/`, `participant/`, `response/`, `actions/`, `clustering/`, and `escalation/`. Keep a flat facilitation test only when it intentionally spans multiple facilitation capabilities, such as `AuthorizationMatrixIntegrationTest`.
- **`configurator/`** — Import, template, contract, and integration tests for configurator ownership.
- **`organization/`** — Organization, team, membership, and related lower-level tests.
- **`auth/`** — Authentication controller, service, and security-adjacent lower-level tests.
- **`eventing/`** — SSE event, transport, and contract tests below the browser layer.
- **`common/`** — Architecture guardrails and tiny shared-kernel contract tests.
- **`web/`** — Server-rendered route/controller tests when present.
- **`config/`** — Test configuration classes (`TestSecurityOverride`, `TestRedisConfig`).

#### Test Layer Taxonomy

Use the test pyramid terms literally: unit + component, integration, and end-to-end. The runtime and package should match the lowest truthful layer that proves the behavior.

**Unit + component** (module-owned packages, no browser, focused runtime):
- `ParticipantServiceTest` — service logic with Mockito and no Spring container.
- `SessionApiControllerTest`, `ParticipantApiControllerTest`, `ResponseApiControllerTest` — focused controller/component tests in the owning facilitation capability.
- `RetroTemplateContractTest` — narrow contract coverage without full browser or broad integration setup.

**Integration** (module-owned packages, Spring/Testcontainers as needed, no browser):
- `StepAdvancementApiIntegrationTest` — step index increment and 403 for non-facilitator.
- `ParticipantStateDataIntegrationTest` — participant LEFT/ACTIVE state and FK-safe response preservation.
- `ClusteringApiIntegrationTest`, `ActionItemApiIntegrationTest`, `EscalationApiIntegrationTest` — endpoint and workflow contracts in their owning capability package.
- `ClusteringDataModelTest`, `EscalationDataModelTest` — repository and persistence invariants.
- `TemplateImportIntegrationTest` — configurator import integrity and stage data contracts.
- `AuthorizationMatrixIntegrationTest` — cross-capability facilitation transport gate for `/api/**`.

**End-to-end** (`src/test/java/direct/reflect/facilitator/e2e/`, Playwright required):
- `RetroFlowEndToEndTest` — golden-path retrospective journey, column rendering, clustering UI, voting UI, and stage progress.
- `MultiUserRetroEndToEndTest` — multi-user flows, column isolation, and privacy-mode reveal.
- `SseEndToEndTest` — SSE transport and UI update chain.
- `SmartActionBuilderEndToEndTest`, `ActionReviewEndToEndTest`, `EscalationVotingEndToEndTest` — browser journeys for user-facing flows that lower layers cannot prove honestly.

#### Test Class Naming Rules

A test class name MUST communicate the layer it operates at and the behaviour it guards. Template names (`Ssc`, `MadSadGlad`, `StartStopContinue`, `HappinessHistogram`, `OriginalFour`) are **banned** in any generic end-to-end or integration test class name. They are only allowed where the class intentionally tests template-specific data integrity, for example `TemplateImportIntegrationTest`.

Suffix conventions:
- `Test` — unit or focused component test in the owning module package.
- `ApiIntegrationTest` — MockMvc or HTTP integration contract.
- `DataIntegrationTest` — SpringBootTest, real DB, no browser, service-level or multi-repository workflow contract.
- `DataModelTest` — narrower persistence contract focused on intentional repository and mapping invariants.
- `EndToEndTest` — Playwright browser journey under `e2e/`.

Do not add new `BrowserRegressionTest`, `BrowserSmokeTest`, or `BrowserTest` classes. Those names describe the old browser taxonomy that lived under `integration/`.

#### Security Testing Approach

- **Transport gate**: `anyRequest().authenticated()` in both `SecurityConfig` and `TestSecurityOverride` — unauthenticated requests to `/api/**` return 401 JSON (not 302 redirect)
- **`@WithMockUser`**: Acceptable in tests that mock `AuthService`/`ParticipantService` via `@MockitoBean`. The annotation satisfies the transport gate; the mock controls business authorization.
- **Do NOT use `@WithMockUser`** in security-critical tests that test the authorization matrix itself — use `SecurityMockMvcRequestPostProcessors.authentication()` + `MockHttpSession` with `authType`/`authenticatedUser` attributes instead.
- **`spring.security.enabled: false`** must NOT appear in test config — it silently disables all `@PreAuthorize` checks.

#### Cucumber BDD Architecture

The BDD stack uses Cucumber + Playwright to verify user-facing behavior through semantic interaction. It avoids raw Playwright waits and CSS coupling by observing the semantic shell.

| Package | Role | Key Classes |
|---|---|---|
| `bdd/` | Runner + Entry Point | `CucumberTestRunner` |
| `bdd/support/` | Scenario Lifecycle | `CucumberSpringConfiguration`, `PlaywrightWorld` |
| `bdd/support/context/` | Scenario State | `RetroScenarioContext` |
| `bdd/support/selectors/` | Semantic Selectors | `RetroSelectors` |
| `bdd/support/drivers/` | Behavior Surfaces | `RetroSessionDriver`, `ProgressBarDriver`, `SyncDriver` |
| `bdd/stepdefinitions/` | Gherkin Glue | `VisualClueStageSteps` |

**Architecture Components:**
- **`CucumberSpringConfiguration`**: Bootstraps the Spring context with Testcontainers (PostgreSQL, Redis) and test-specific security overrides.
- **`PlaywrightWorld`**: Manages the Playwright browser lifecycle. It provides isolated `BrowserContext` and `Page` instances for each scenario, supporting multi-user flows via `createAdditionalContext()`.
- **`RetroScenarioContext`**: A `@ScenarioScope` component that carries shared state (session IDs, phase numbers) across step definitions within a single scenario.
- **`RetroSelectors`**: A central library of semantic selectors. Step definitions and drivers must use these constants instead of raw CSS or XPath strings.
- **`SyncDriver`**: Observes the `[data-testid='retro-content']` semantic shell to ensure the UI is in sync with the backend. It polls for state changes based on attributes like `data-phase`, `data-step-index`, and the upcoming `data-sync-state`.
- **`RetroSessionDriver`**: Handles high-level retrospective lifecycle operations such as guest authentication, session creation, and phase advancement.
- **`ProgressBarDriver`**: Encapsulates interactions with the stage progress indicator, verifying station status, connectors, and spatial orientation.

**Extension Philosophy:**
- **New Behavior**: Add a dedicated Driver in `bdd/support/drivers/` for complex component interactions.
- **New Step**: Add Gherkin steps to existing classes in `bdd/stepdefinitions/` or create a new class for a new feature area.
- **New Selector**: Always add to `RetroSelectors` first. Never use raw selectors in step definitions.
- **New State**: Use `RetroScenarioContext` for variables that need to persist across steps in one scenario.

**Synchronization and SSE:**
The BDD stack does not use a raw SSE event driver. Instead, it observes the rendered shell state. The frontend (`useSSE` -> `RetroPage` reconciliation -> `useAppliedVersionStore`) handles the complexity of `signaledVersion` vs `appliedVersion`. Tests observe the outcome via semantic attributes on the `retro-content` container.

**Architecture Guardrails:**
`ArchitectureGuardrailTest` enforces BDD hygiene:
- No BDD class may extend `BaseEndToEndTest`.
- `PendingException` is forbidden in committed code.
- Raw Playwright waits (`waitForTimeout`, `Thread.sleep`) are forbidden in step definitions.
- CSS/Layout coupling tokens (Tailwind classes, bounding boxes) are forbidden in drivers and steps.
- Every `.feature` file must be tagged with `@facilitation` or `@visual-clue-pilot`.

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

2. **BUILD**: AI agents execute the plan autonomously. No user involvement during this phase. Agent writes BOTH implementation code AND test code, integration tests and Playwright end-to-end tests.

3. **VERIFY**: Automated gate — ALL of the following MUST pass:
   - `./mvnw clean verify` — zero failures. This single command runs ALL tests (unit, integration, Playwright) AND static analysis (Checkstyle, PMD, SpotBugs). There is no separate Playwright step.
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
- [ ] All frontend functionality is verified via Playwright end-to-end tests (tests MUST live in `src/test/java/direct/reflect/facilitator/e2e/`)
- [ ] `./mvnw clean verify` passes with zero failures (this runs everything: unit + integration + Playwright + static analysis)
- [ ] No `@Disabled`, `@Ignore`, or `@Tag("flaky")` annotations added
- [ ] No suppressed errors (`@SuppressWarnings`, empty catch blocks, `as any`)
- [ ] Notion story status updated to `Needs review`
- [ ] Code follows existing patterns in this AGENTS.md (GRASP, controller separation, Lombok-first boilerplate reduction, etc.)
- [ ] New Java code uses Lombok for boilerplate by default where appropriate, instead of manual getters, setters, constructors, or logger fields unless custom behavior is required

### Test Requirements

**Cross-reference**: See Testing Patterns in Code Conventions above for test reliability rules (no `@Disabled`, no flaky tests, etc.). The rules there are MANDATORY and are NOT repeated here.

Additional REQUIRED rules for feature delivery:

- **Integration tests**: REQUIRED for every new endpoint (API, View, or Event controller). Use `@SpringBootTest` + Testcontainers. Follow patterns in existing test classes.
- **Playwright end-to-end tests**: REQUIRED for every user-facing feature. Tests MUST live in `src/test/java/direct/reflect/facilitator/e2e/` alongside the existing browser journeys, for example `RetroFlowEndToEndTest`. They MUST capture screenshot evidence.
- **Regression gate**: `./mvnw clean verify` MUST show zero failures TOTAL — not just new tests passing, but ALL existing tests still passing.

**Definitions**:
- **User-facing feature**: Any change visible in the browser UI, new page, new component, changed behavior, new interaction. REQUIRES Playwright end-to-end tests.
- **Backend endpoint**: API endpoint, service logic, or data model change with no direct UI impact. REQUIRES integration test only, no Playwright required.
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

### Commit Message Policy

- Use **Conventional Commits** for all commit messages: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`, `ci:`, and `build:`.
- Treat Conventional Commits as the basis for release/version semantics when CI/CD automation needs to infer change type.
- Prefer **early local enforcement** through a repo-managed `commit-msg` git hook so contributors get feedback before pushing.
- Also enforce the same policy in **CI and branch protection** as a backstop; local hooks alone are not sufficient because they can be bypassed or missing.
- Tooling such as Commitizen, Husky, Lefthook, or a plain shell `commit-msg` hook is acceptable. Choose the lightest option that the repository can maintain consistently.
- At minimum, require a CI check that validates either commit messages, PR titles, or both before merge, even if local hooks are present.
- Do not merge work that bypasses the Conventional Commit policy unless the repo owner explicitly approves an exception.

### Edge Cases

- **Story with no BDD scenarios**: MUST NOT build. Agent MUST flag this to user. BDD is the acceptance contract.
- **Pure backend story (no UI)**: Integration tests only. No Playwright end-to-end coverage required.
- **Story spanning multiple commits**: Set `Needs review` ONLY on final delivery, NOT on partial deliveries.
- **Notion MCP unavailable**: Proceed with delivery. Agent MUST notify user to update status manually.
- **BDD scenario technically infeasible**: Agent MUST document the conflict and ask user for resolution before proceeding.

---

## Key Technical Rules

### DTO Pattern
- Always convert entities to DTOs before passing them to any rendering layer or API response
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

### Prerequisites
- Docker Desktop
- Java 25 (`brew install --cask temurin@25`)
- Maven wrapper is bundled (`./mvnw`) — no separate Maven install needed

### Starting the Full Stack

Spring Boot automatically starts PostgreSQL, Redis, and the frontend dev server via Docker Compose integration (`spring.docker.compose.enabled: true`). Docker Desktop must be running, but you don't need to run `docker compose up -d` manually.

```bash
# Single command starts everything (Docker services + Spring Boot backend)
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

- App: http://localhost:8080
- Frontend dev server: http://localhost:5173

If the automatic startup fails or you need to manage Docker services independently (e.g., to keep them running between backend restarts), you can start them manually as a fallback:

```bash
# Manual fallback: start Docker services separately
docker compose up -d

# Then start the backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

### Maven Commands
- **Build the project**: `./mvnw clean compile`
- **Run tests**: `./mvnw test`
- **Run specific test**: `./mvnw test -Dtest=ClassName#methodName`
- **Package the application**: `./mvnw clean package`

### Docker Commands
- **Start services manually** (fallback, or to keep services running between backend restarts): `docker compose up -d`
- **Stop services**: `docker compose down`

### Frontend Commands (inside `frontend/`)
- **Dev server**: `npm run dev`
- **Type check**: `npm run typecheck`
- **Unit tests**: `npm test`
- **Regenerate API types**: `npm run generate-types`

### Testing with Testcontainers
- Tests use Testcontainers for PostgreSQL and Redis — no manual setup needed
- Playwright end-to-end tests run as part of the Maven test suite
- `./mvnw test` runs unit, integration, and Playwright tests

### Important Notes
- **Always use `import` profile** when running the application
- This loads templates, retro stages, and steps from CSV files in `src/main/resources/`
- Without the `import` profile, the database will be empty

---

## Claude-Specific Instructions

### Application Management

**Agent may manage the application directly.**

#### How It Works
- Start the application with `./mvnw spring-boot:run -Dspring-boot.run.profiles=import`
- Check whether it is already running before starting another instance
- Use `/tmp/facilitator.log` for log inspection when startup or runtime output is being written there

#### Starting the Application
1. **Check if running**:
   ```bash
   curl -s http://localhost:8080/ > /dev/null && echo "✅ Running" || echo "❌ Not running"
   ```

2. **If not running, start it yourself**:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=import
   ```

3. Wait until the HTTP check succeeds or the logs show startup completed before proceeding

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

#### Manual Multi-User Browser QA
- Do not use multiple tabs in the same browser to simulate different users. This is fundamentally not possible due to the cookie that is set locally, so you can only have 1 user and 1 session in that browser context.
- Canonical multi-user QA uses isolated browser contexts or profiles. For manual checks, use two different browsers, two separate browser profiles, or one normal window plus one incognito window only when they are truly isolated.

### Task Management
- Use the TodoWrite tool to track multi-step tasks
- Mark tasks as completed immediately after finishing
- Only have one task in_progress at a time

### Code Generation Patterns
- Follow Spring MVC patterns (not WebFlux - we use standard Spring MVC)
- Use Lombok annotations consistently, and default to Lombok for Java boilerplate when it expresses the intent clearly
- Avoid generating manual getters, setters, constructors, or logger declarations unless the class needs custom behavior that Lombok should not hide
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

---

## Partner PR Reviewer Checklist

Use this checklist when reviewing PRs created via the `fix-bug` or `va-explain` partner skills. The goal is to catch ambient noise before merging — not to re-verify partner intent.

### Step 1: Diff Cleanliness

Run these two commands against the PR branch:

```bash
# All files in the PR diff
git diff origin/main...<branch-name> --name-only

# Full content diff
git diff origin/main...<branch-name>
```

The diff must contain **only the files the partner intended to change**. Reject anything else.

**Forbidden file classes — any of these in the diff = reject:**

| Pattern | Why it must not be here |
|---|---|
| `src/main/resources/static/` | Generated Vite build output; regenerated on every Maven build |
| `bdd-reports/` | Generated BDD analysis artifacts; transient review output only |
| `CLAUDE.md`, `.claude/`, `.agents/skills/` | Repo-owner tooling files; incidental AI side-effects belong in separate commits |
| `.sisyphus/` | Agent planning files; gitignored by design |
| `frontend/src/types/generated/` | Generated TypeScript types; changes without actual type changes are noise |
| `.env*`, `secrets/`, credential files | Never commit secrets |
| `frontend/node_modules/` | Package install output; should never be tracked |
| `.opencode/oh-my-opencode.json` | Runtime tooling config; ambient drift from OpenCode sessions |

If forbidden files are present, ask the partner to re-run the skill, or fix the branch by removing the forbidden files manually and force-pushing.

### Step 2: Test Gate

Checkout the branch and run:

```bash
git checkout <branch-name>
./mvnw clean verify
```

Expected: `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS.

Zero tolerance. If any test fails or static analysis reports violations, the PR is not ready.

After running tests, restore any drift the Maven build produces. Maven regenerates static assets (new hashed JS/CSS bundles as untracked files) and generated TypeScript types — `git restore` alone is insufficient because new untracked files won't be removed by it:

```bash
git restore src/main/resources/static/ frontend/src/types/generated/
git clean -f src/main/resources/static/
git status  # should be clean
```

### Step 3: Functional Readiness

For each intended change in the diff, ask: does this change do what the partner described?

- CSV config changes: verify the changed rows look correct and plausible
- Frontend changes: check for a Playwright screenshot or a brief browser verification note in the PR description
- Security-relevant changes (auth, redirect handling, session config): look for corresponding tests

A clean diff is necessary but not sufficient. The change still needs to be correct.

### Merge vs Reject Decision

**Merge when all of the following are true:**
- Diff scope contains only the intended files (no forbidden classes above)
- `./mvnw clean verify` passes with zero failures
- The content of the change matches the partner's stated intent
- `gh pr view <N> --json mergeStateStatus` returns `"CLEAN"`

**Request changes (do not merge) when any of the following is true:**
- Any forbidden file class appears in the diff
- Tests fail on the branch
- The diff contains unrelated changes with no explanation
- The change content appears incorrect or incomplete

**Reject outright and ask for rework when:**
- Secrets or credentials appear in the diff
- A PR contains changes to both business logic AND tooling/build files (they must be separate PRs)
- The branch is based on a stale `main` with unresolved merge conflicts

### Quick Reference Commands

```bash
# Check diff scope
git diff origin/main...<branch> --name-only

# Check review state before force-push
gh pr view <N> --json number,title,state,headRefName,reviews,reviewDecision
gh api repos/<owner>/<repo>/pulls/<N>/comments

# Run full regression gate
./mvnw clean verify

# Check merge eligibility
gh pr view <N> --json mergeStateStatus
```
