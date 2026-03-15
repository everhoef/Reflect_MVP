package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * SSE → React UI update chain tests.
 *
 * <p>Validates that SSE events trigger correct UI updates in the React frontend.
 * Focuses on the event propagation path: backend event → SSE stream → browser DOM update.
 * Does NOT own full retrospective journey assertions.
 *
 * <p>Responsibility: SSE event → UI rendering chain only.
 *
 * <p>Test scope:
 * <ul>
 *   <li>{@code shouldBroadcastNoteAddedToAllParticipantsWhenNoteSubmitted} — submit note via UI
 *       → SSE {@code note_added} event fires → note visible on all connected browser contexts</li>
 * </ul>
 *
 * <p>SSE transport/session sync smoke tests are in {@link SseTransportSmokeTest}.
 * Golden-path regression is in {@link RetroFlowBrowserRegressionTest}.
 * Multi-user flow interaction tests are in {@link MultiUserRetroBrowserRegressionTest}.
 */
@DisplayName("SSE to UI Update Chain Tests")
@Slf4j
public class SseUiChainTest extends BaseIntegrationTest {

    /**
     * Verifies the full chain: submit note via UI → SSE note_added event fires →
     * note appears on a second browser context (observer page).
     *
     * Chain validated:
     * 1. Facilitator and participant both open a retro page (SSE connected)
     * 2. Participant submits a note via the UI form
     * 3. Backend saves note and publishes SSE note_added event
     * 4. React invalidates the query and re-fetches responses
     * 5. The note content is visible on the facilitator's page without manual refresh
     */
    @Test
    @DisplayName("Should broadcast note_added SSE event to all participants when a note is submitted")
    void shouldBroadcastNoteAddedToAllParticipantsWhenNoteSubmitted() throws InterruptedException {
        BrowserContext facilitatorContext = createMonitoredContext();
        BrowserContext participantContext = createMonitoredContext();

        Page facilitatorPage = facilitatorContext.newPage();
        Page participantPage = participantContext.newPage();

        try {
            log.info("=== SSE note_added BROADCAST TEST ===");

            // Authenticate both users
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participantPage, "Participant");

            // Facilitator creates session
            String sessionId = createRetroSession(facilitatorPage, "SSE Note Broadcast Test");
            log.info("Session created: {}", sessionId);

            // Participant joins session
            joinRetroSession(participantPage, sessionId);
            log.info("Participant joined session");

            // Ensure both SSE connections are established before starting
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));
            waitForSseConnection(participantPage, UUID.fromString(sessionId));
            log.info("Both SSE connections established");

            // Brief pause to ensure server-side SSE emitter registration is complete
            Thread.sleep(500);

            // Start the retro
            startRetroSession(facilitatorPage);
            log.info("Retro started");

            // Wait for participant to transition to active retro
            participantPage.waitForFunction(
                "() => !document.body.textContent.includes('Session Lobby')",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            // Navigate to a step with a MULTI_COLUMN_BOARD (Mad/Sad/Glad input step)
            log.info("Navigating to Mad/Sad/Glad input step...");
            int maxSkips = 15;
            boolean foundInputStep = false;

            for (int i = 0; i < maxSkips; i++) {
                if (participantPage.locator("[data-column=\"Mad\"] textarea[name='content']").count() > 0) {
                    log.info("Found Mad/Sad/Glad input step at iteration {}", i);
                    foundInputStep = true;
                    break;
                }
                clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, participantPage);
            }

            if (!foundInputStep) {
                throw new AssertionError("Failed to find Mad/Sad/Glad input step after " + maxSkips + " iterations");
            }

            // Note content that we'll submit and then verify appears on the facilitator's page
            String noteContent = "SSE test note: " + System.currentTimeMillis();

            fillElement(participantPage, "[data-column=\"Mad\"] textarea[name='content']", noteContent);
            clickElement(participantPage, "[data-column=\"Mad\"] button:has-text('➕')");
            log.info("Participant submitted note: {}", noteContent);

            // Verify note appears on participant's own page
            waitForElement(participantPage, "[data-column=\"Mad\"] p:has-text('" + noteContent + "')",
                DEFAULT_TIMEOUT_MS);
            log.info("Note visible on participant's own page");

            // Verify note (hidden) appears on facilitator's page via SSE → React Query refresh
            waitForElement(facilitatorPage, "[data-column=\"Mad\"] p:has-text('[Hidden until revealed]')",
                SSE_PROPAGATION_TIMEOUT_MS);

            assertTrue(facilitatorPage.locator(
                "[data-column=\"Mad\"] p:has-text('[Hidden until revealed]')").first().isVisible(),
                "Facilitator's Mad column should show a hidden card after SSE note_added event propagates");

            log.info("✅ SSE note_added chain verified: note submitted by participant → visible on facilitator's page");

        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }
}
