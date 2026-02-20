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
    // orderIndex=28 (0-based index 27) is the first step with allowActionItems=true in stage 21
    private static final int SSC_ACTION_ITEMS_STEP_INDEX = 27;

    @Test
    @Timeout(300)
    @DisplayName("Should validate SSC retrospective flow: column colors and sticky notes")
    void shouldValidateSscRetroFlow() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
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

            String startStyle    = facilitatorPage.locator("[data-column='Start']").getAttribute("style");
            String stopStyle     = facilitatorPage.locator("[data-column='Stop']").getAttribute("style");
            String continueStyle = facilitatorPage.locator("[data-column='Continue']").getAttribute("style");

            assertNotNull(startStyle,    "Start column should have an inline style attribute");
            assertNotNull(stopStyle,     "Stop column should have an inline style attribute");
            assertNotNull(continueStyle, "Continue column should have an inline style attribute");

            assertTrue(startStyle.contains("#10B981"),
                    "Start column should be green (#10B981), got: " + startStyle);
            assertTrue(stopStyle.contains("#EF4444"),
                    "Stop column should be red (#EF4444), got: " + stopStyle);
            assertTrue(continueStyle.contains("#3B82F6"),
                    "Continue column should be blue (#3B82F6), got: " + continueStyle);

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

            // ── 8. Fast-forward to action items step and submit an action item ──────
            logTestProgress("ACTION_ITEMS", 6, 6, "Fast-forwarding to action items step");
            fastForwardToStep(sessionId, SSC_ACTION_ITEMS_STEP_INDEX);

            facilitatorPage.waitForResponse(
                    response -> response.url().contains("/" + sessionId + "/action-items") && response.status() == 200,
                    () -> facilitatorPage.navigate(retroUrl));

            facilitatorPage.waitForFunction("() => !!document.querySelector(\"textarea[name='what']\")");
            facilitatorPage.evaluate("() => document.querySelector(\"textarea[name='what']\").scrollIntoView()");

            facilitatorPage.evaluate(
                    "() => { document.querySelector(\"textarea[name='what']\").value = 'Pair programming sessions every Tuesday'; }");

            String whatValue = (String) facilitatorPage.evaluate(
                    "() => document.querySelector(\"textarea[name='what']\").value");
            assertEquals("Pair programming sessions every Tuesday", whatValue,
                    "Action item 'what' textarea should have the correct value");

            facilitatorPage.evaluate(
                    "() => { const form = document.querySelector(\"form[hx-post*='action-items']\"); if (form) { htmx.trigger(form, 'submit'); } }");

            waitForElement(facilitatorPage, "text=Pair programming sessions every Tuesday", 15000);
            assertTrue(
                    facilitatorPage.locator("text=Pair programming sessions every Tuesday").isVisible(),
                    "Submitted action item should be visible on facilitator page");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "SSC Retrospective Flow", e);
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
