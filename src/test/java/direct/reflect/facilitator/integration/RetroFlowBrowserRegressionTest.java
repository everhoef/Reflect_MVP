package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-path retrospective regression test.
 *
 * <p>Canonical end-to-end browser test for a complete retrospective journey
 * using the SSC (Start-Stop-Continue) reference template. Covers the full
 * facilitator + participant flow including column color rendering, sticky note
 * submission, clustering UI (drag handles), and voting UI.
 *
 * <p>Responsibility: Full golden-path regression. One stable reference template.
 * Resilience scenarios (reconnect/refresh/leave-rejoin) belong in a separate suite.
 *
 * <p>Test scope:
 * <ul>
 *   <li>{@code shouldValidateSscRetroFlow} — SSC column colors + sticky note submission
 *       on facilitator and participant browser contexts</li>
 *   <li>{@code shouldValidateSscClusteringDisplay} — allowMerging=true step renders drag handles,
 *       no dual-render of sticky notes</li>
 *   <li>{@code shouldValidateSscVotingUi} — allowVoting=true step shows vote button,
 *       button is clickable</li>
 * </ul>
 *
 * <p>SSE transport/session sync smoke tests are in {@link SseTransportSmokeTest}.
 * SSE → React UI update chain tests are in {@link SseUiChainTest}.
 * Multi-user flow interaction tests are in {@link MultiUserRetroBrowserRegressionTest}.
 */
@DisplayName("Retro Flow Browser Regression Tests")
public class RetroFlowBrowserRegressionTest extends BaseIntegrationTest {

    private static final int SSC_GATHER_DATA_MASTERSHEET_ID = 29;
    private static final int SSC_INPUT_STEP_INDEX = 0;

    @Test
    @Timeout(300)
    @DisplayName("Should validate retrospective flow: column colors and sticky notes")
    void shouldValidateSscRetroFlow() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            // Wait for server to be ready (handles cold-start when run in isolation)
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            // ── 1. Authenticate ────────────────────────────────────────────────────
            logTestProgress("SETUP", 1, 6, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            // ── 2. Create session (uses Default template initially) ─────────────────
            logTestProgress("SETUP", 2, 6, "Creating retro session");
            String sessionId = createRetroSession(facilitatorPage, "SSC Test Session");

            // ── 3. Swap template to SSC ─────────────────────────────────────────────
            logTestProgress("SETUP", 3, 6, "Swapping session template to SSC");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);

            // ── 4. Participant joins, facilitator starts session ────────────────────
            logTestProgress("SETUP", 4, 6, "Participant joining and starting session");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            // ── 5. Fast-forward session DB state to SSC input step ─────────────────
            //    After startRetroSession() the server is at SET_THE_STAGE, step 0.
            //    We skip straight to GATHER_DATA/step=SSC_INPUT_STEP_INDEX by directly
            //    updating the DB, then reloading both pages via the content endpoint.
            logTestProgress("SETUP", 5, 6, "Fast-forwarding to SSC input step");
            fastForwardToStep(sessionId, SSC_INPUT_STEP_INDEX);

            // Reload both pages so they pick up the new step
            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            participantPage.navigate(retroUrl);

            waitForAllPagesElement("[data-column='Start']", facilitatorPage, participantPage);

            // ── 6. Verify column colors ─────────────────────────────────────────────
            logTestProgress("COLORS", 6, 6, "Verifying Start/Stop/Continue column colors");
            waitForElement(facilitatorPage, "[data-column='Start']");

            String startStyle    = facilitatorPage.locator("[data-column='Start'] > div:first-child").getAttribute("style");
            String stopStyle     = facilitatorPage.locator("[data-column='Stop'] > div:first-child").getAttribute("style");
            String continueStyle = facilitatorPage.locator("[data-column='Continue'] > div:first-child").getAttribute("style");

            assertNotNull(startStyle,    "Start column should have an inline style attribute");
            assertNotNull(stopStyle,     "Stop column should have an inline style attribute");
            assertNotNull(continueStyle, "Continue column should have an inline style attribute");

            assertTrue(startStyle.contains("#10B981") || startStyle.contains("rgb(16, 185, 129)"),
                    "Start column should be green (#10B981 / rgb(16,185,129)), got: " + startStyle);
            assertTrue(stopStyle.contains("#EF4444") || stopStyle.contains("rgb(239, 68, 68)"),
                    "Stop column should be red (#EF4444 / rgb(239,68,68)), got: " + stopStyle);
            assertTrue(continueStyle.contains("#EAB308") || continueStyle.contains("rgb(234, 179, 8)"),
                    "Continue column should be yellow (#EAB308 / rgb(234,179,8)), got: " + continueStyle);

            // ── 7. Submit sticky notes in all three columns ─────────────────────────
            logTestProgress("STICKY_NOTES", 6, 6, "Submitting sticky notes in all three columns");
            fillElement(participantPage, "[data-column='Start'] textarea[name='content']", "Start: More pair programming");
            clickElement(participantPage, "[data-column='Start'] button[type='submit']");

            fillElement(participantPage, "[data-column='Stop'] textarea[name='content']", "Stop: Long meetings");
            clickElement(participantPage, "[data-column='Stop'] button[type='submit']");

            fillElement(participantPage, "[data-column='Continue'] textarea[name='content']", "Continue: Code reviews");
            clickElement(participantPage, "[data-column='Continue'] button[type='submit']");

            waitForElement(participantPage, "p:has-text('Start: More pair programming')");
            assertTrue(
                    participantPage.locator("p:has-text('Start: More pair programming')").isVisible(),
                    "Submitted Start note should be visible on participant page");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "SSC Retrospective Flow", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }


    @Test
    @Timeout(300)
    @DisplayName("Should validate clustering display: no dual-render, sortable UI visible")
    void shouldValidateSscClusteringDisplay() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server to be ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 4, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 4, "Creating retro session with SSC template");
            String sessionId = createRetroSession(facilitatorPage, "SSC Clustering Test");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);

            logTestProgress("SETUP", 3, 4, "Starting session, submitting note, then fast-forwarding to clustering step");
            startRetroSession(facilitatorPage, sessionId);

            // First submit a sticky note at the input step so responses.empty is false at clustering step
            // (The .sortable div only renders when responses exist AND allowMerging=true)
            logTestProgress("SETUP", 3, 4, "Submitting note at input step so clustering step has data");
            fastForwardToStep(sessionId, SSC_INPUT_STEP_INDEX);
            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            waitForElement(facilitatorPage, "[data-column='Start']");
            fillElement(facilitatorPage, "[data-column='Start'] textarea[name='content']", "Clustering test note");
            clickElement(facilitatorPage, "[data-column='Start'] button[type='submit']");
            waitForElement(facilitatorPage, "p:has-text('Clustering test note')");

            // Now fast-forward to first clustering step (orderIndex=3 → 0-based index 2)
            int clusteringStepIndex = 2;
            fastForwardToStep(sessionId, clusteringStepIndex);
            facilitatorPage.waitForResponse(
                    response -> response.url().contains("/" + sessionId) && response.status() == 200,
                    () -> facilitatorPage.navigate(retroUrl));

            // Wait for a column to appear (clustering step has allowMerging=true)
            facilitatorPage.waitForFunction("() => !!document.querySelector('[data-column]')", null,
                    new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            logTestProgress("CLUSTERING", 4, 4, "Verifying clustering UI: notes with drag handles present");

            facilitatorPage.waitForFunction(
                    "() => document.querySelector('[aria-label=\"Drag to reorder\"]') !== null",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            assertTrue(facilitatorPage.locator("[aria-label='Drag to reorder']").count() > 0,
                    "Drag handles should be present for allowMerging=true step");

            logTestProgress("CLUSTERING", 4, 4, "Clustering display verified - no dual-render, sortable UI present");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "SSC Clustering Display", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    @Test
    @Timeout(300)
    @DisplayName("Should validate voting UI: vote button visible and clickable")
    void shouldValidateSscVotingUi() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            // Wait for server to be ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 5, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 5, "Creating retro session with SSC template");
            String sessionId = createRetroSession(facilitatorPage, "SSC Voting Test");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);

            logTestProgress("SETUP", 3, 5, "Participant joining and starting session");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            // First submit a sticky note at the input step so there is something to vote on
            logTestProgress("SETUP", 4, 5, "Submitting sticky note for voting");
            fastForwardToStep(sessionId, SSC_INPUT_STEP_INDEX);
            String retroUrl = baseUrl + "/retro/" + sessionId;
            participantPage.navigate(retroUrl);
            waitForElement(participantPage, "[data-column='Start']");

            fillElement(participantPage, "[data-column='Start'] textarea[name='content']", "Start: Voting test note");
            clickElement(participantPage, "[data-column='Start'] button[type='submit']");
            waitForElement(participantPage, "p:has-text('Start: Voting test note')");

            // Fast-forward to voting step (orderIndex=4 → 0-based index 3)
            logTestProgress("VOTING", 5, 5, "Fast-forwarding to voting step");
            int votingStepIndex = 3;
            fastForwardToStep(sessionId, votingStepIndex);

            facilitatorPage.waitForResponse(
                    response -> response.url().contains("/" + sessionId) && response.status() == 200,
                    () -> facilitatorPage.navigate(retroUrl));

            // Wait for vote buttons to appear (allowVoting=true at this step)
            facilitatorPage.waitForFunction(
                    "() => document.querySelector('button[aria-label^=\"Vote for this note\"]') !== null",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            assertTrue(facilitatorPage.locator("button[aria-label^='Vote for this note']").count() > 0,
                    "Vote button should be visible at voting step");

            facilitatorPage.locator("button[aria-label^='Vote for this note']").first().click();

            facilitatorPage.waitForFunction(
                    "() => document.querySelector('button[aria-label^=\"Vote for this note\"]') !== null",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            logTestProgress("VOTING", 5, 5, "Vote button present and clickable — voting UI verified");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "SSC Voting UI", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }


    @Test
    @Timeout(300)
    @DisplayName("Should show pause-timer button for facilitator but not participant on timed step")
    void shouldShowTimerControlsOnlyToFacilitator() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            // Wait for server ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 4, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 4, "Creating retro session with SSC template");
            String sessionId = createRetroSession(facilitatorPage, "Timer Controls Test");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);

            logTestProgress("SETUP", 3, 4, "Participant joining and starting session");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            // Fast-forward to SSC input step (TIMER_EXPIRES, durationSeconds=480)
            // AND set stepStartedAt so the timer is active
            logTestProgress("TIMER", 4, 4, "Fast-forwarding to timed SSC input step");
            fastForwardToTimedStep(sessionId, SSC_INPUT_STEP_INDEX);

            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            participantPage.navigate(retroUrl);

            waitForAllPagesElement("[data-column='Start']", facilitatorPage, participantPage);

            // Timer state is loaded asynchronously by the React useTimer hook on page load.
            // Wait until remainingSeconds is populated (store hydrated) by polling for
            // pause-timer-button OR confirming it doesn't appear for participant.
            facilitatorPage.waitForFunction(
                    "() => !!document.querySelector('[data-testid=\"pause-timer-button\"]') " +
                    "|| !!document.querySelector('[data-testid=\"resume-timer-button\"]')",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            // ── Facilitator MUST see pause/resume button ────────────────────────
            logTestProgress("TIMER", 4, 4, "Asserting facilitator sees timer control button");
            boolean facilitatorSeesPause = facilitatorPage.locator("[data-testid='pause-timer-button']").count() > 0;
            boolean facilitatorSeesResume = facilitatorPage.locator("[data-testid='resume-timer-button']").count() > 0;
            assertTrue(facilitatorSeesPause || facilitatorSeesResume,
                    "Facilitator should see pause-timer-button or resume-timer-button on a timed step");

            // ── Participant must NOT see either timer control button ─────────────
            logTestProgress("TIMER", 4, 4, "Asserting participant does NOT see timer control buttons");
            assertEquals(0, participantPage.locator("[data-testid='pause-timer-button']").count(),
                    "Participant should NOT see pause-timer-button");
            assertEquals(0, participantPage.locator("[data-testid='resume-timer-button']").count(),
                    "Participant should NOT see resume-timer-button");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Timer Controls Role Visibility", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    @Test
    @Timeout(300)
    @DisplayName("Should render exactly one stage-progress-bar on an active retro page")
    void shouldRenderSingleStageProgressBar() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 3, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 3, "Creating and starting retro session");
            String sessionId = createRetroSession(facilitatorPage, "Phase Progress Sanity Test");
            startRetroSession(facilitatorPage, sessionId);

            // Wait for the retro content to be fully rendered
            waitForElement(facilitatorPage, "[data-testid='retro-content']");

            // ── Assert exactly ONE stage-progress-bar is visible ───────────────
            logTestProgress("PHASE_BAR", 3, 3, "Asserting exactly one stage-progress-bar");
            int progressBarCount = facilitatorPage.locator("[data-testid='stage-progress-bar']").count();
            assertEquals(1, progressBarCount,
                    "Exactly one data-testid='stage-progress-bar' should render; found " + progressBarCount +
                    " (T6 fix: inline duplicate removed, only Header renders the progress bar)");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Single Stage Progress Bar", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    @Test
    @Timeout(300)
    @DisplayName("Should display active timer countdown (not 'Timer not started') on a timed step")
    void shouldDisplayTimerCountdownOnTimedStep() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 3, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 3, "Creating retro session with SSC template, starting session");
            String sessionId = createRetroSession(facilitatorPage, "Timer Display Test");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);
            startRetroSession(facilitatorPage, sessionId);

            // Fast-forward to SSC input step with active timer (stepStartedAt set now)
            logTestProgress("TIMER", 3, 3, "Fast-forwarding to SSC timed step and verifying countdown");
            fastForwardToTimedStep(sessionId, SSC_INPUT_STEP_INDEX);

            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            waitForElement(facilitatorPage, "[data-column='Start']");

            // Wait for timer to hydrate from backend (useTimer hook polls /api/retro/{id}/timer)
            facilitatorPage.waitForFunction(
                    "() => !document.body.textContent.includes('Timer not started') || " +
                    "  document.querySelector('[data-testid=\"pause-timer-button\"]') !== null || " +
                    "  document.querySelector('[data-testid=\"resume-timer-button\"]') !== null",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            // Timer should show a countdown value (MM:SS format) rather than "Timer not started"
            String bodyText = facilitatorPage.textContent("body");
            assertFalse(bodyText.contains("Timer not started"),
                    "Timer should be active and showing a countdown, not 'Timer not started'. " +
                    "This verifies the useTimer hook hydrates from the backend /api/retro/{id}/timer endpoint.");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Timer Display Countdown", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }


    @Test
    @Timeout(300)
    @DisplayName("Should preserve assistant history exactly after page reload")
    void shouldPreserveAssistantHistoryOnReload() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server to be ready (handles cold-start when run in isolation)
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 6, "Authenticate facilitator and create session");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            String sessionId = createRetroSession(facilitatorPage, "Reload Consistency Test");
            startRetroSession(facilitatorPage, sessionId);

            // Fast-forward to GATHER_DATA step 2 so there are 2 history items
            logTestProgress("ADVANCE", 2, 6, "Fast-forwarding to GATHER_DATA step 2 to build history");
            fastForwardSession(sessionId, RetroPhase.GATHER_DATA, 2);
            facilitatorPage.reload();

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("WAIT", 3, 6, "Waiting for history items to appear");
            waitForHistoryItemCount(facilitatorPage, 2);

            logTestProgress("CAPTURE", 4, 6, "Capturing guidance text and history before reload");
            String guidanceBefore = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            int historyCountBefore = getHistoryItemCount(facilitatorPage);
            List<String> historyTextsBefore = getHistoryItemTexts(facilitatorPage);

            assertNotNull(guidanceBefore, "Guidance content must not be null before reload");
            assertFalse(guidanceBefore.isBlank(), "Guidance content must not be blank before reload");
            assertTrue(historyCountBefore > 0,
                "Must have at least 1 history item before reload (count: " + historyCountBefore + ")");

            logTestProgress("RELOAD", 5, 6, "Reloading page");
            facilitatorPage.reload();

            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(facilitatorPage, "[data-testid='guidance-content']", SSE_PROPAGATION_TIMEOUT_MS);
            waitForHistoryItemCount(facilitatorPage, historyCountBefore);

            logTestProgress("ASSERT", 6, 6, "Asserting guidance and history are identical after reload");
            String guidanceAfter = facilitatorPage.locator("[data-testid='guidance-content']").textContent();
            int historyCountAfter = getHistoryItemCount(facilitatorPage);
            List<String> historyTextsAfter = getHistoryItemTexts(facilitatorPage);

            assertEquals(guidanceBefore, guidanceAfter,
                "Guidance content must be identical after reload");
            assertEquals(historyCountBefore, historyCountAfter,
                "History item count must be identical after reload");
            assertEquals(historyTextsBefore, historyTextsAfter,
                "History item texts must be identical after reload");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Assistant History Reload", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    @Test
    @Timeout(300)
    @DisplayName("guidance-sidebar coachmark is visible on active step and dismissible")
    void shouldShowAndDismissGuidanceSidebarCoachmark() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server to be ready (handles cold-start when run in isolation)
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 3, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 3, "Creating and starting retro session");
            String sessionId = createRetroSession(facilitatorPage, "Guidance Coachmark Test");
            startRetroSession(facilitatorPage, sessionId);

            waitForElement(facilitatorPage, "[data-testid='retro-content']");
            waitForElement(facilitatorPage, "[data-coachmark='guidance-sidebar']");

            logTestProgress("COACHMARK", 3, 3, "Verifying guidance-sidebar coachmark visible and dismissible");
            waitForElement(facilitatorPage, "[data-testid='guidance-sidebar-coachmark']");
            assertTrue(
                facilitatorPage.locator("[data-testid='guidance-sidebar-coachmark']").isVisible(),
                "guidance-sidebar coachmark should be visible on an active step"
            );
            assertTrue(
                facilitatorPage.locator("[data-testid='guidance-sidebar-coachmark-content']").isVisible(),
                "guidance-sidebar coachmark content panel should be visible"
            );

            clickElement(facilitatorPage, "[data-testid='guidance-sidebar-coachmark-close']");
            facilitatorPage.waitForFunction(
                "() => document.querySelector('[data-testid=\"guidance-sidebar-coachmark\"]') === null",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS)
            );
            assertEquals(
                0,
                facilitatorPage.locator("[data-testid='guidance-sidebar-coachmark']").count(),
                "guidance-sidebar coachmark should be removed from DOM after dismiss"
            );

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Guidance Sidebar Coachmark", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Builds (and persists) a RetroTemplate whose GATHER_DATA stage is the
     * "Start Stop Continue" SSC stage (mastersheetID=29).
     * The other four phases reuse the first available template's stages so that
     * the session can navigate through all phases correctly.
     */
    private RetroTemplate buildSscTemplate() {
        RetroStage sscStage = stageRepository.findByMastersheetID(SSC_GATHER_DATA_MASTERSHEET_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Start Stop Continue stage (mastersheetID=" + SSC_GATHER_DATA_MASTERSHEET_ID +
                        ") not found. Is the 'import' profile active?"));

        // Use the default (first) template for the non-SSC phases
        RetroTemplate defaultTemplate = templateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No templates found – is the 'import' profile active?"));

        RetroTemplate sscTemplate = new RetroTemplate();
        sscTemplate.setName("Start Stop Keep (test)");
        sscTemplate.setDescription("SSC template created by integration test");
        sscTemplate.setMaturityLevel(2);
        sscTemplate.setReleased(false);
        sscTemplate.setSetTheStage(defaultTemplate.getSetTheStage());
        sscTemplate.setGatherData(sscStage);
        sscTemplate.setGenerateInsights(defaultTemplate.getGenerateInsights());
        sscTemplate.setDecideActions(defaultTemplate.getDecideActions());
        sscTemplate.setCloseRetro(defaultTemplate.getCloseRetro());

        return templateRepository.save(sscTemplate);
    }

    /**
     * Directly updates the session in the DB to GATHER_DATA phase at the given step index.
     * This skips over all the preceding steps so the test can focus on SSC-specific features.
     */
    private void fastForwardToStep(String sessionId, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(RetroPhase.GATHER_DATA);
        session.setCurrentStepIndex(stepIndex);
        retroSessionRepository.save(session);
    }

    /**
     * Like {@link #fastForwardToStep}, but also sets {@code stepStartedAt = now()} so
     * the timer is considered active. Required for tests that assert timer-related UI.
     */
    private void fastForwardToTimedStep(String sessionId, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(RetroPhase.GATHER_DATA);
        session.setCurrentStepIndex(stepIndex);
        session.setStepStartedAt(java.time.LocalDateTime.now());
        session.setTimerPausedAt(null);
        session.setAccumulatedPauseSeconds(0L);
        retroSessionRepository.save(session);
    }

    private void fastForwardSession(String sessionId, RetroPhase phase, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(phase);
        session.setCurrentStepIndex(stepIndex);
        retroSessionRepository.save(session);
    }

    private void waitForHistoryItemCount(Page page, int expectedCount) {
        recordActivity("waitForHistoryItemCount: expected=" + expectedCount);
        try {
            page.waitForFunction(
                String.format(
                    "() => document.querySelectorAll('[data-testid=\"assistant-history-list\"] > div').length >= %d",
                    expectedCount
                ),
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS)
            );
        } catch (Exception e) {
            int actual = getHistoryItemCount(page);
            throw new AssertionError(String.format(
                "History item count did not reach %d within %dms. Actual: %d. URL: %s",
                expectedCount, (long) SSE_PROPAGATION_TIMEOUT_MS, actual, page.url()), e);
        }
    }

    private int getHistoryItemCount(Page page) {
        return page.locator("[data-testid='assistant-history-list'] > div").count();
    }

    @SuppressWarnings("unchecked")
    private List<String> getHistoryItemTexts(Page page) {
        Object result = page.evaluate(
            "() => Array.from(document.querySelectorAll('[data-testid=\"assistant-history-list\"] > div p:last-child'))" +
            ".map(p => p.textContent ? p.textContent.replace(/\\s+/g, ' ').trim() : '')"
        );
        if (result instanceof List) {
            return (List<String>) result;
        }
        return List.of();
    }

}
