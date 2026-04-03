package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE browser tests: transport layer and React UI update chain.
 *
 * Transport tests: SSE connections, participant_joined, session_started broadcast.
 * UI chain tests: SSE event → React Query invalidation → DOM update.
 */
@DisplayName("SSE Browser Tests")
@Slf4j
public class SseBrowserTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("SSE Transport: session-level event broadcast")
    class SseTransport {

        @Test
        @DisplayName("Should broadcast participant_joined event to all participants in session")
        void shouldBroadcastParticipantJoinedToAllParticipants() throws InterruptedException {
            BrowserContext context1 = createMonitoredContext();
            Page facilitatorPage = context1.newPage();

            authenticateAsGuest(facilitatorPage, "Facilitator");
            String sessionId = createRetroSession(facilitatorPage, "Event Broadcast Test");

            waitForElement(facilitatorPage, "h2:has-text('Session Lobby')");
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));

            waitForParticipantList(facilitatorPage, "Facilitator");

            BrowserContext context2 = createMonitoredContext();
            Page participantPage = context2.newPage();

            authenticateAsGuest(participantPage, "Participant");
            joinRetroSession(participantPage, sessionId);

            waitForElement(participantPage, "h2:has-text('Session Lobby')");
            waitForSseConnection(participantPage, UUID.fromString(sessionId));

            waitForAllPagesParticipantList(new String[]{"Facilitator", "Participant"},
                facilitatorPage, participantPage);

            log.info("participant_joined event successfully broadcast to all participants");
        }

        @Test
        @DisplayName("Should broadcast session_started event to all participants")
        void shouldBroadcastSessionStartedEventToAllParticipants() throws InterruptedException {
            BrowserContext context1 = createMonitoredContext();
            Page facilitatorPage = context1.newPage();

            BrowserContext context2 = createMonitoredContext();
            Page participantPage = context2.newPage();

            authenticateAsGuest(facilitatorPage, "Facilitator");
            String sessionId = createRetroSession(facilitatorPage, "Session Start Test");

            waitForElement(facilitatorPage, "h2:has-text('Session Lobby')");
            waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));

            authenticateAsGuest(participantPage, "Participant");
            joinRetroSession(participantPage, sessionId);

            waitForElement(participantPage, "h2:has-text('Session Lobby')");
            waitForSseConnection(participantPage, UUID.fromString(sessionId));

            assertTrue(facilitatorPage.url().contains("/retro/" + sessionId),
                "Facilitator should be in retro lobby");
            assertTrue(participantPage.url().contains("/retro/" + sessionId),
                "Participant should be in retro lobby");

            clickElement(facilitatorPage, "[data-testid='start-retro-button']");

            facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                null, new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            participantPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                null, new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            assertFalse(facilitatorPage.textContent("body").contains("Session Lobby"),
                "Facilitator should no longer see lobby after session started");
            assertFalse(participantPage.textContent("body").contains("Session Lobby"),
                "Participant should no longer see lobby after session started");

            log.info("session_started event successfully broadcast to all participants");
        }
    }

    @Nested
    @DisplayName("SSE UI Chain: event triggers React DOM update")
    class SseUiChain {

        @Test
        @DisplayName("Should broadcast note_added SSE event to all participants when a note is submitted")
        void shouldBroadcastNoteAddedToAllParticipantsWhenNoteSubmitted() throws InterruptedException {
            BrowserContext facilitatorContext = createMonitoredContext();
            BrowserContext participantContext = createMonitoredContext();

            Page facilitatorPage = facilitatorContext.newPage();
            Page participantPage = participantContext.newPage();

            try {
                log.info("=== SSE note_added BROADCAST TEST ===");

                authenticateAsGuest(facilitatorPage, "Facilitator");
                authenticateAsGuest(participantPage, "Participant");

                String sessionId = createRetroSession(facilitatorPage, "SSE Note Broadcast Test");
                log.info("Session created: {}", sessionId);

                joinRetroSession(participantPage, sessionId);
                log.info("Participant joined session");

                waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));
                waitForSseConnection(participantPage, UUID.fromString(sessionId));
                log.info("Both SSE connections established");

                Thread.sleep(500);

                startRetroSession(facilitatorPage, sessionId);
                log.info("Retro started");

                participantPage.waitForFunction(
                    "() => !document.body.textContent.includes('Session Lobby')",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

                // Wait for participant page to fully render at the starting step before
                // beginning the skip loop — without this, the loop races ahead of the
                // participant page's React render and lands on the wrong step.
                waitForElement(participantPage, "[data-step-index]", DEFAULT_TIMEOUT_MS);
                log.info("Participant page rendered at step index: {}", getCurrentStepIndex(participantPage));

                log.info("Navigating to first multi-column input step with text input (not single-char ESVP)...");
                int maxSkips = 20;
                boolean foundInputStep = false;
                for (int i = 0; i < maxSkips; i++) {
                    participantPage.waitForFunction(
                        "() => !document.querySelector('[class*=\"animate-spin\"]')",
                        null,
                        new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS)
                    );
                    int textareaCount = participantPage.locator("[data-column] textarea[name='content']").count();
                    if (textareaCount > 0) {
                        String maxLen = participantPage.locator("[data-column] textarea[name='content']").first()
                            .getAttribute("maxlength");
                        boolean isSingleCharStep = "1".equals(maxLen);
                        if (!isSingleCharStep) {
                            log.info("Found multi-column text input step at skip iteration {}", i);
                            foundInputStep = true;
                            break;
                        }
                        log.info("Skipping single-char step (maxlength=1, e.g. ESVP), iteration {}", i);
                    }
                    if (facilitatorPage.locator("[data-testid='next-step-button']").count() == 0) {
                        log.error("No Next button found while looking for input step (iteration {})", i);
                        break;
                    }
                    log.info("Advancing to next step (iteration {})", i);
                    clickNextAndWait(facilitatorPage, DEFAULT_TIMEOUT_MS, participantPage);
                }
                if (!foundInputStep) {
                    throw new AssertionError(
                        "Failed to find multi-column input step with textarea after " + maxSkips + " skip iterations");
                }

                String firstColumnTitle = (String) participantPage.evaluate(
                    "() => document.querySelector('[data-column] textarea[name=\\'content\\']')?.closest('[data-column]')?.getAttribute('data-column')");
                log.info("Using column: {}", firstColumnTitle);

                String columnSelector = "[data-column='" + firstColumnTitle + "']";
                String noteContent = "SSE test note: " + System.currentTimeMillis();

                fillElement(participantPage, columnSelector + " textarea[name='content']", noteContent);
                clickElement(participantPage, columnSelector + " button:has-text('➕')");
                log.info("Participant submitted note to column '{}': {}", firstColumnTitle, noteContent);

                waitForElement(participantPage, columnSelector + " p:has-text('" + noteContent + "')",
                    DEFAULT_TIMEOUT_MS);
                log.info("Note visible on participant's own page");

                waitForElement(facilitatorPage, columnSelector + " p:has-text('[Hidden until revealed]')",
                    SSE_PROPAGATION_TIMEOUT_MS);

                assertTrue(facilitatorPage.locator(
                    columnSelector + " p:has-text('[Hidden until revealed]')").first().isVisible(),
                    "Facilitator's column should show a hidden card after SSE note_added event propagates");

                log.info("✅ SSE note_added chain verified: note submitted by participant → visible on facilitator's page");

            } finally {
                facilitatorContext.close();
                participantContext.close();
            }
        }
    }
}
