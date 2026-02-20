package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SSC Retrospective Flow Integration Tests")
@Slf4j
public class SscRetroFlowTest extends BaseIntegrationTest {

    private static final int SSC_GATHER_DATA_MASTERSHEET_ID = 21;
    private static final int SSC_INPUT_STEP_INDEX = 2;

    @Autowired
    private RetroStepRepository stepRepository;

    @Test
    @Timeout(300)
    @DisplayName("Should validate SSC retrospective flow: column colors, sticky notes, and action items")
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
            log.info("Template swapped to SSC: {}", sscTemplate.getName());

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

            // ── 8. Fast-forward to action items step ────────────────────────────────
            logTestProgress("ACTION_ITEMS", 6, 6, "Jumping to action items step");
            int actionItemsStepIndex = findActionItemsStepIndex();
            log.info("Dynamically found action items step index: {}", actionItemsStepIndex);
            fastForwardToStep(sessionId, actionItemsStepIndex);
            facilitatorPage.navigate(retroUrl);
            facilitatorPage.locator("textarea[name='what']")
                    .waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(20000));
            facilitatorPage.locator("textarea[name='what']").scrollIntoViewIfNeeded();

            assertTrue(
                    facilitatorPage.locator("textarea[name='what']").isVisible(),
                    "Action item WHAT field should be visible on the action items step");

            fillElement(facilitatorPage, "textarea[name='what']", "Implement daily standups");
            clickElement(facilitatorPage, "button:has-text('Add Action Item')");

            facilitatorPage.locator("text=Implement daily standups")
                    .waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(10000));
            assertTrue(
                    facilitatorPage.locator("text=Implement daily standups").isVisible(),
                    "Submitted action item should appear in the list");

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
        log.info("Fast-forwarded session {} to GATHER_DATA / stepIndex={}", sessionId, stepIndex);
    }

    @SuppressWarnings("unchecked")
    private int findActionItemsStepIndex() {
        RetroStage sscStage = stageRepository.findByMastersheetID(SSC_GATHER_DATA_MASTERSHEET_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "SSC stage (mastersheetID=" + SSC_GATHER_DATA_MASTERSHEET_ID + ") not found"));

        List<RetroStep> steps = stepRepository.findByRetroStageOrderByOrderIndexAsc(sscStage);
        log.info("SSC stage has {} steps in DB", steps.size());

        return IntStream.range(0, steps.size())
                .filter(i -> {
                    Map<String, Object> config = steps.get(i).getComponentConfig();
                    if (config == null) return false;
                    Object caps = config.get("capabilities");
                    if (!(caps instanceof Map)) return false;
                    Object allowActionItems = ((Map<String, Object>) caps).get("allowActionItems");
                    return Boolean.TRUE.equals(allowActionItems);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No step with allowActionItems=true found in SSC stage (mastersheetID=" +
                        SSC_GATHER_DATA_MASTERSHEET_ID + "). Steps in DB: " + steps.size()));
    }
}
