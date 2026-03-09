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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SSC Retrospective Flow Integration Tests")
public class SscRetroFlowTest extends BaseIntegrationTest {

    private static final int SSC_GATHER_DATA_MASTERSHEET_ID = 21;
    private static final int SSC_INPUT_STEP_INDEX = 2;

    @Test
    @Timeout(300)
    @DisplayName("Should validate SSC retrospective flow: column colors and sticky notes")
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
                } catch (Exception ignored) {}
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
            startRetroSession(facilitatorPage);

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
            assertTrue(continueStyle.contains("#3B82F6") || continueStyle.contains("rgb(59, 130, 246)"),
                    "Continue column should be blue (#3B82F6 / rgb(59,130,246)), got: " + continueStyle);

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
    @DisplayName("Should validate SSC clustering display: no dual-render, sortable UI visible")
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
                } catch (Exception ignored) {}
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
            startRetroSession(facilitatorPage);

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

            // Now fast-forward to first clustering step (orderIndex=10 → 0-based index 9)
            int clusteringStepIndex = 9;
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
    @DisplayName("Should validate SSC voting UI: vote button visible and clickable")
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
                } catch (Exception ignored) {}
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
            startRetroSession(facilitatorPage);

            // First submit a sticky note at the input step so there is something to vote on
            logTestProgress("SETUP", 4, 5, "Submitting sticky note for voting");
            fastForwardToStep(sessionId, SSC_INPUT_STEP_INDEX);
            String retroUrl = baseUrl + "/retro/" + sessionId;
            participantPage.navigate(retroUrl);
            waitForElement(participantPage, "[data-column='Start']");

            fillElement(participantPage, "[data-column='Start'] textarea[name='content']", "Start: Voting test note");
            clickElement(participantPage, "[data-column='Start'] button[type='submit']");
            waitForElement(participantPage, "p:has-text('Start: Voting test note')");

            // Fast-forward to voting step (orderIndex=22 → 0-based index 21)
            logTestProgress("VOTING", 5, 5, "Fast-forwarding to voting step");
            int votingStepIndex = 21;
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


    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Builds (and persists) a RetroTemplate whose GATHER_DATA stage is the
     * "Start Stop Keep" SSC stage (mastersheetID=21).
     * The other four phases reuse the first available template's stages so that
     * the session can navigate through all phases correctly.
     */
    private RetroTemplate buildSscTemplate() {
        RetroStage sscStage = stageRepository.findByMastersheetID(SSC_GATHER_DATA_MASTERSHEET_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Start Stop Keep stage (mastersheetID=" + SSC_GATHER_DATA_MASTERSHEET_ID +
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

}
