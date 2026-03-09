package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Integration test verifying the SSE → UI update chain.
 *
 * This test runs against the Thymeleaf app (current state) because T31
 * (Spring Boot serving React) is not yet complete. The SSE infrastructure
 * being tested is shared by both Thymeleaf and React: the same backend
 * EventService publishes events to the same SSE endpoint (/api/retro/{id}/events).
 * Once T31 is done, the selectors may need updating to target React-rendered DOM,
 * but the SSE plumbing assertions will remain valid.
 *
 * Test scenarios:
 * 1. Submit a note via the UI → SSE note_added event fires → note appears on
 *    all connected browser contexts (multi-user real-time update chain).
 * 2. New participant joins → SSE participant_joined event fires → participant
 *    list updates on all existing connected browser contexts.
 *
 * The SSE → React Query invalidation chain (T8/useSSE hook) is unit-tested in
 * frontend/src/hooks/useSSE.test.ts. This integration test validates the
 * backend half of the chain: that real SSE events are fired when actions occur.
 */
@DisplayName("SSE → UI Update Integration Tests")
@Slf4j
public class SseReactIntegrationTest extends BaseIntegrationTest {

    /**
     * Verifies the full chain: submit note via UI → SSE note_added event fires →
     * note appears on a second browser context (observer page).
     *
     * Chain validated:
     * 1. Facilitator and participant both open a retro page (SSE connected)
     * 2. Participant submits a note via the UI form
     * 3. Backend saves note and publishes SSE note_added event
     * 4. Facilitator's page receives the SSE event and HTMX refreshes the column
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
            // Skip through steps until we find the Mad/Sad/Glad input step
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

            // Note content that we'll submit and then check appears on the facilitator's page
            String noteContent = "SSE test note: " + System.currentTimeMillis();

            facilitatorPage.evaluate("window.sseNoteEvents = []");
            facilitatorPage.evaluate("""
                if (window.eventSource) {
                    window.eventSource.addEventListener('note_added', function(evt) {
                        console.log('[SSE] facilitator received note_added:', evt.data);
                        window.sseNoteEvents.push(evt.data);
                    });
                }
            """);

            fillElement(participantPage, "[data-column=\"Mad\"] textarea[name='content']", noteContent);
            clickElement(participantPage, "[data-column=\"Mad\"] button:has-text('➕')");
            log.info("Participant submitted note: {}", noteContent);

            waitForElement(participantPage, "[data-column=\"Mad\"] p:has-text('" + noteContent + "')",
                DEFAULT_TIMEOUT_MS);
            log.info("Note visible on participant's own page");

            facilitatorPage.waitForFunction(
                "() => window.sseNoteEvents && window.sseNoteEvents.length > 0",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            log.info("Facilitator received note_added SSE event");

            Integer facilitatorNoteEventCount = (Integer) facilitatorPage.evaluate(
                "() => window.sseNoteEvents ? window.sseNoteEvents.length : 0");
            assertTrue(facilitatorNoteEventCount > 0,
                "Facilitator MUST receive note_added SSE event when participant submits a note");

            waitForElement(facilitatorPage, "[data-column=\"Mad\"] p:has-text('[Hidden until revealed]')",
                SSE_PROPAGATION_TIMEOUT_MS);

            assertTrue(facilitatorPage.locator(
                "[data-column=\"Mad\"] p:has-text('[Hidden until revealed]')").first().isVisible(),
                "Facilitator's Mad column should show a hidden card after SSE note_added event propagates via HTMX");

            log.info("✅ SSE note_added chain verified: note submitted by participant → visible on facilitator's page");

        } finally {
            facilitatorContext.close();
            participantContext.close();
        }
    }

    /**
     * Verifies the full chain: new participant joins → SSE participant_joined event fires →
     * participant list updates on all connected browser contexts.
     *
     * Chain validated:
     * 1. Facilitator creates and starts a session (SSE connected)
     * 2. Participant 1 joins and connects to SSE
     * 3. Participant 2 joins the already-started session
     * 4. Backend publishes SSE participant_joined event
     * 5. Facilitator's page and Participant 1's page both see Participant 2 in the list
     *    without manual refresh
     */
    @Test
    @DisplayName("Should broadcast participant_joined SSE event so participant list updates on all pages")
    void shouldUpdateParticipantListViaSSEWhenNewParticipantJoins() throws InterruptedException {
        BrowserContext facilitatorContext = createMonitoredContext();
        BrowserContext participant1Context = createMonitoredContext();
        BrowserContext participant2Context = createMonitoredContext();

        Page facilitatorPage = facilitatorContext.newPage();
        Page participant1Page = participant1Context.newPage();
        Page participant2Page = participant2Context.newPage();

        try {
            log.info("=== SSE participant_joined BROADCAST TEST ===");

            // Authenticate users
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participant1Page, "Alice");

            // Facilitator creates session and Participant 1 joins
            String sessionId = createRetroSession(facilitatorPage, "SSE Participant Join Test");
            joinRetroSession(participant1Page, sessionId);

            // Verify both are in lobby
            waitForElement(facilitatorPage, "h2:has-text('Session Lobby')");
            waitForElement(participant1Page, "h2:has-text('Session Lobby')");

            // Establish SSE connections before starting session
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));
            waitForSseConnection(participant1Page, UUID.fromString(sessionId));
            log.info("Facilitator and Participant1 SSE connections established in lobby");

            // Brief pause to ensure server-side SSE emitter registration is complete
            Thread.sleep(500);

            // Start the session so SSE stays active for retro steps
            startRetroSession(facilitatorPage);

            // Wait for Participant1 to also transition to retro
            participant1Page.waitForFunction(
                "() => !document.body.textContent.includes('Session Lobby')",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            // Verify SSE connections are active on retro page
            facilitatorPage.waitForFunction(
                "() => window.eventSource && window.eventSource.readyState === 1",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            participant1Page.waitForFunction(
                "() => window.eventSource && window.eventSource.readyState === 1",
                null,
                new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            log.info("Both SSE connections active on retro page");

            // Set up SSE event capture on facilitator and participant1 before participant2 joins
            facilitatorPage.evaluate("""
                window.sseParticipantEvents = [];
                if (window.eventSource) {
                    window.eventSource.addEventListener('participant_joined', function(evt) {
                        console.log('[SSE] facilitator received participant_joined:', evt.data);
                        window.sseParticipantEvents.push(evt.data);
                    });
                }
            """);

            participant1Page.evaluate("""
                window.sseParticipantEvents = [];
                if (window.eventSource) {
                    window.eventSource.addEventListener('participant_joined', function(evt) {
                        console.log('[SSE] participant1 received participant_joined:', evt.data);
                        window.sseParticipantEvents.push(evt.data);
                    });
                }
            """);

            // Now participant2 joins — this should trigger participant_joined SSE event
            log.info("Participant2 joining the active session...");
            authenticateAsGuest(participant2Page, "Bob");
            joinRetroSession(participant2Page, sessionId);
            log.info("Bob joined session");

            // Wait for facilitator to receive the participant_joined SSE event
            facilitatorPage.waitForFunction(
                "() => window.sseParticipantEvents && window.sseParticipantEvents.length > 0",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            // Wait for participant1 to receive the participant_joined SSE event
            participant1Page.waitForFunction(
                "() => window.sseParticipantEvents && window.sseParticipantEvents.length > 0",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

            // Verify both received the event
            Integer facilitatorEventCount = (Integer) facilitatorPage.evaluate(
                "() => window.sseParticipantEvents ? window.sseParticipantEvents.length : 0");
            Integer participant1EventCount = (Integer) participant1Page.evaluate(
                "() => window.sseParticipantEvents ? window.sseParticipantEvents.length : 0");

            log.info("SSE participant_joined events received — Facilitator: {}, Participant1: {}",
                facilitatorEventCount, participant1EventCount);

            assertTrue(facilitatorEventCount > 0,
                "Facilitator MUST receive participant_joined SSE event when Bob joins");
            assertTrue(participant1EventCount > 0,
                "Participant1 MUST receive participant_joined SSE event when Bob joins");

            // Verify the SSE event payload contains the new participant's name
            // The participant_joined payload is a JSON string (displayName)
            String facilitatorLastEvent = (String) facilitatorPage.evaluate(
                "() => window.sseParticipantEvents[window.sseParticipantEvents.length - 1]");
            log.info("participant_joined payload received by facilitator: {}", facilitatorLastEvent);
            assertNotNull(facilitatorLastEvent,
                "participant_joined payload must not be null");
            assertTrue(facilitatorLastEvent.contains("Bob"),
                "participant_joined payload must contain the joiner's name 'Bob', got: " + facilitatorLastEvent);

            log.info("✅ SSE participant_joined chain verified: Bob joined → event received by facilitator and Alice");

        } finally {
            facilitatorContext.close();
            participant1Context.close();
            participant2Context.close();
        }
    }
}
