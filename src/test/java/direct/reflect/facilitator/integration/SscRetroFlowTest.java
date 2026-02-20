package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.RetroSession;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SSC Retrospective Flow Integration Tests")
@Slf4j
public class SscRetroFlowTest extends BaseIntegrationTest {

    /**
     * SSC template ID (Start Stop Keep / Start Stop Continue).
     * Template ID 21 corresponds to the "Start Stop Keep" stages in the CSV.
     */
    private static final long SSC_TEMPLATE_ID = 21L;

    /**
     * Step index of the sticky note input step within the SSC template.
     * Steps 0+1 are AUTO-advancing intro steps; step 2 (orderIndex 3) is the
     * TIMER_EXPIRES sticky-note input step with all three columns.
     */
    private static final int SSC_INPUT_STEP_INDEX = 2;

    /**
     * Step index of the first action items step within the SSC template.
     * Step 27 (orderIndex 28) has allowActionItems=true.
     */
    private static final int SSC_ACTION_ITEMS_STEP_INDEX = 27;

    @Test
    @Timeout(300)
    @DisplayName("Should validate SSC retrospective flow: column colors, sticky notes, and action items")
    void shouldValidateSscRetroFlow() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

        try {
            // ── SETUP ────────────────────────────────────────────────────────────
            logTestProgress("SETUP", 1, 7, "Authenticating facilitator and participant");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");
            authenticateAsGuest(participantPage, "Bob");

            logTestProgress("SETUP", 2, 7, "Creating SSC retro session");
            String sessionId = createRetroSession(facilitatorPage, "SSC Test Session");

            // Swap the session's template to SSC (ID 21) BEFORE starting, so that
            // startRetroSession() navigates into the SSC stage/step sequence.
            logTestProgress("SETUP", 3, 7, "Swapping template to SSC (ID 21)");
            RetroTemplate sscTemplate = templateRepository.findById(SSC_TEMPLATE_ID)
                    .orElseThrow(() -> new IllegalStateException("SSC template (ID 21) not found – is the import profile active?"));
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);
            log.info("✅ Template swapped to SSC ({})", sscTemplate.getName());

            // ── JOIN & START ─────────────────────────────────────────────────────
            logTestProgress("SETUP", 4, 7, "Participant joining, then starting session");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage);

            // Steps 0 and 1 are AUTO-advancing intro steps; after startRetroSession()
            // the system should already be on step 2 (the sticky-note input step).
            // Wait for the multi-column board columns to appear on both pages.
            waitForAllPagesElement("[data-column='Start']", facilitatorPage, participantPage);

            // ── PHASE 1: COLUMN COLORS ───────────────────────────────────────────
            logTestProgress("COLORS", 5, 7, "Verifying Start/Stop/Continue column colors");
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
            log.info("✅ Column colors verified: Start=green, Stop=red, Continue=blue");

            // ── PHASE 2: STICKY NOTES ────────────────────────────────────────────
            logTestProgress("STICKY_NOTES", 6, 7, "Submitting sticky notes in all three columns");

            // Submit a note in the Start column
            fillElement(participantPage, "[data-column='Start'] textarea[name='content']", "Start: More pair programming");
            clickElement(participantPage, "[data-column='Start'] button:has-text('➕')");

            // Submit a note in the Stop column
            fillElement(participantPage, "[data-column='Stop'] textarea[name='content']", "Stop: Long meetings");
            clickElement(participantPage, "[data-column='Stop'] button:has-text('➕')");

            // Submit a note in the Continue column
            fillElement(participantPage, "[data-column='Continue'] textarea[name='content']", "Continue: Code reviews");
            clickElement(participantPage, "[data-column='Continue'] button:has-text('➕')");

            // The participant's own notes should be visible immediately (SSE will also push to facilitator)
            waitForElement(participantPage, "p:has-text('Start: More pair programming')");
            assertTrue(
                    participantPage.locator("p:has-text('Start: More pair programming')").isVisible(),
                    "Submitted Start note should be visible on participant page");
            log.info("✅ Sticky notes submitted in Start, Stop, Continue columns");

            // ── PHASE 3: NAVIGATE TO ACTION ITEMS STEP ───────────────────────────
            // We are currently at step index 2. We need to reach step index 27.
            // That means 25 Next-button clicks.
            logTestProgress("NAVIGATE", 7, 7, "Navigating to action items step (step 27)");
            int clicksNeeded = SSC_ACTION_ITEMS_STEP_INDEX - SSC_INPUT_STEP_INDEX;
            for (int i = 0; i < clicksNeeded; i++) {
                boolean advanced = clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS);
                if (!advanced) {
                    fail("Could not advance at click " + (i + 1) + " of " + clicksNeeded +
                         " while navigating toward step " + SSC_ACTION_ITEMS_STEP_INDEX);
                }
            }

            // ── PHASE 4: ACTION ITEMS ────────────────────────────────────────────
            logTestProgress("ACTION_ITEMS", 7, 7, "Verifying action item form and submitting an action item");
            waitForElement(facilitatorPage, "textarea[name='what']");
            assertTrue(
                    facilitatorPage.locator("textarea[name='what']").isVisible(),
                    "Action item WHAT field should be visible on the action items step");

            fillElement(facilitatorPage, "textarea[name='what']", "Implement daily standups");
            clickElement(facilitatorPage, "button:has-text('Add Action Item')");

            waitForElement(facilitatorPage, "text=Implement daily standups");
            assertTrue(
                    facilitatorPage.locator("text=Implement daily standups").isVisible(),
                    "Submitted action item should appear in the action items list");

            log.info("✅ SSC flow fully validated: column colors, sticky notes, and action items");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "SSC Retrospective Flow", e);
            throw e;
        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }
}
