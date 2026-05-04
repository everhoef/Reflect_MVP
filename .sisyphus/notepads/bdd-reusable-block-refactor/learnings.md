# Learnings — bdd-reusable-block-refactor

## [2026-05-04] Atlas pre-dispatch analysis

### Current BDD structure
- `bdd/support/PlaywrightWorld.java` — clean browser lifecycle owner, scenario-scoped, good CDN blocking, failure screenshot on @After
- `bdd/support/CucumberSpringConfiguration.java` — Spring/Testcontainers bridge
- `bdd/stepdefinitions/VisualClueStageSteps.java` — 683-line god class (MUST be refactored)
- `bdd/CucumberTestRunner.java` — JUnit Platform Suite runner
- NO support topology yet (no drivers, no selectors, no context)

### Feature file state
- `src/test/resources/features/visual-clue-stage.feature` — has NO tags at all; 10 scenarios, no @visual-clue-pilot tagging

### Header.tsx semantic hooks (already present)
- `data-testid='stage-progress-bar'` — on the nav element
- `aria-label='Retrospective stages'` — on the nav element
- `data-stage-status={status}` — values: 'complete' | 'in-progress' | 'to-do' — on each station div
- `aria-current={status === 'in-progress' ? 'step' : undefined}` — on station div

### CSS-coupled selectors that MUST be replaced (Task 4)
- `div.rounded-full` → used to identify station elements (no semantic attribute for index)
- `div.h-px` → used to identify connector elements (no semantic attribute for index)
- Connectors have NO `data-*` attributes at all
- Station divs have no `data-stage-index` attribute (only nth-child positional access)

### Pending issues in VisualClueStageSteps.java
- Missing `import java.util.Objects;` (line 595, 669 use `Objects.requireNonNullElse`)
- All browser/Playwright logic is inline (must move to drivers)
- `BoundingBox` checks at lines 129-135, 633-644 — must be replaced or removed
- `PendingException` calls at lines 132, 135, 188, 375, 462, 487, 528, 546, 547, 560, 585, 588, 654 — ALL must be removed

### BaseEndToEndTest relationship
- BDD code does NOT currently extend BaseEndToEndTest (good)
- BaseEndToEndTest is at `e2e/support/BaseEndToEndTest.java` — 1907 lines
- Bucket-1 (extract to BDD): server-ready polling, guest auth flow, session creation, start session, advance phase, waitForFunction patterns
- Bucket-2 (leave in legacy E2E): Testcontainers wiring, JUnit @BeforeAll/@AfterAll, tracing infra, multi-context setup
- Bucket-3 (revisit later): shared Testcontainers container singletons pattern

### Topology to create (Tasks 2+3)
- `RetroScenarioContext.java` (@ScenarioScope) — state: retroReady, sessionId, currentPhaseNumber, lastAdvanceTriggered
- `RetroSelectors.java` — static selector constants: RETRO_CONTENT, STAGE_PROGRESS_BAR, NEXT_STEP_BUTTON, START_RETRO_BUTTON, station(n), connector(n)
- `RetroSessionDriver.java` — waitForServerReady, authenticateAsGuest, createSession, startSession, advanceToPhase
- `ProgressBarDriver.java` — assertProgressIndicatorPresent, progressIndicator, stationCount, connectorCount, station(n), assertStationHighlighted, assertStationLooksGreyedOut, assertStationLooksUpcoming, connectorLooksGreyedOut/Upcoming
- `SyncDriver.java` — assertRetroContentLoaded, waitForPhaseOrStepChange

### PlaywrightWorld.java key facts
- EVIDENCE_DIR = ".sisyphus/evidence/bdd-pilot"
- baseUrl = "http://localhost:" + serverPort
- Creates fresh browserContext per scenario
- `createAdditionalContext()` for multi-user scenarios (not used by pilot yet)
- Failure screenshot on @After — KEEP this, extend for success artifacts in Task 6

## [2026-05-04] Pilot tag scoping
- `visual-clue-stage.feature` is now the only feature tagged with `@visual-clue-pilot`.
- Pilot verification evidence captured in `.sisyphus/evidence/task-1-pilot-contract.txt` using the required grep command.

## [2026-05-04] Task 2+3 execution learnings
- Keep BDD support composition-first: scenario state lives in `RetroScenarioContext`, browser lifecycle stays in `PlaywrightWorld`, and semantic waits/session flows move into drivers.
- New progress-bar assertions should target semantic hooks (`data-stage-index`, `data-connector-index`, `data-connector-status`) instead of Tailwind classes so step glue can stay thin.

## [2026-05-04] Task 5: VisualClueStageSteps refactor
- Rewriting the step definitions as thin glue worked once every browser concern delegated to `RetroSessionDriver`, `SyncDriver`, and `ProgressBarDriver`, leaving only small assertion loops and text normalization in the glue class.
- The tricky part was Cucumber expression matching: literal `/` and parenthesized text in step annotations needed regex-safe patterns to avoid undefined steps and incomplete annotation failures.
- Connector semantics are now aligned with `Header.tsx`: "greyed out" means connector status is anything except `in-progress`, while "upcoming" means anything except `complete`.
- Pilot result: `./mvnw -q -DskipTests compile` passed and `./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags='@visual-clue-pilot'` passed with 12 scenarios, 0 failures, 0 errors.

## [2026-05-04] Task 6: Evidence hooks
- Success screenshots now live beside failure screenshots in `.sisyphus/evidence/bdd-pilot/`, using the same safe scenario naming convention with `_success.png` / `_failure.png` suffixes.
- Fail-fast hook checks belong in `SyncDriver` so semantic selectors can fail with an immediate contract error instead of a Playwright timeout.
- Progress bar assertions should ask for semantic hook presence before reading `data-stage-status` / `data-connector-status`; this keeps the pilot readable when the DOM contract breaks.
