package direct.reflect.facilitator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;



import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.APIResponse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Focused SSE infrastructure smoke tests.
 *
 * <p>Validates that SSE connections are established, maintained, and that
 * session-level events (participant join/leave, session start) propagate
 * correctly to connected clients. Does NOT test full retrospective flow logic.
 *
 * <p>Responsibility: SSE transport layer and session synchronization only.
 *
 * <p>Test scope:
 * <ul>
 *   <li>{@code shouldBroadcastParticipantJoinedToAllParticipants} — {@code participant_joined}
 *       event broadcast verified on all connected browser contexts</li>
 *   <li>{@code shouldBroadcastSessionStartedEventToAllParticipants} — {@code session_started}
 *       event transitions all clients from lobby to active retro state</li>
 * </ul>
 *
 * <p>Full retrospective flow tests are in {@link MultiUserRetroBrowserRegressionTest}.
 * Golden-path regression is in {@link RetroFlowBrowserRegressionTest}.
 * SSE → React UI update chain is in {@link SseUiChainTest}.
 */
@DisplayName("SSE Transport Smoke Tests")
@Slf4j
public class SseTransportSmokeTest extends BaseIntegrationTest {



    @Test
    @DisplayName("Should broadcast participant_joined event to all participants in session")
    void shouldBroadcastParticipantJoinedToAllParticipants() throws InterruptedException {
        BrowserContext context1 = createMonitoredContext();
        Page facilitatorPage = context1.newPage();

        authenticateAsGuest(facilitatorPage, "Facilitator");
        String sessionId = createRetroSession(facilitatorPage, "Event Broadcast Test");

        waitForElement(facilitatorPage, "h2:has-text('Session Lobby')");
        waitForSseConnection(facilitatorPage, UUID.fromString(sessionId));

        // Verify facilitator sees itself in participant list
        waitForParticipantList(facilitatorPage, "Facilitator");

        BrowserContext context2 = createMonitoredContext();
        Page participantPage = context2.newPage();

        authenticateAsGuest(participantPage, "Participant");
        joinRetroSession(participantPage, sessionId);

        waitForElement(participantPage, "h2:has-text('Session Lobby')");
        waitForSseConnection(participantPage, UUID.fromString(sessionId));

        // Verify both pages show both participants after participant joins
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

        // Verify both are in lobby phase
        assertTrue(facilitatorPage.url().contains("/retro/" + sessionId),
            "Facilitator should be in retro lobby");
        assertTrue(participantPage.url().contains("/retro/" + sessionId),
            "Participant should be in retro lobby");

        // Facilitator starts the retrospective
        clickElement(facilitatorPage, "[data-testid='start-retro-button']");

        // Wait for both pages to transition to active retrospective phase
        facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
            null, new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        participantPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
            null, new Page.WaitForFunctionOptions().setTimeout(DEFAULT_TIMEOUT_MS));

        // Verify both pages show active retrospective content (first step)
        assertFalse(facilitatorPage.textContent("body").contains("Session Lobby"),
            "Facilitator should no longer see lobby after session started");
        assertFalse(participantPage.textContent("body").contains("Session Lobby"),
            "Participant should no longer see lobby after session started");

        log.info("session_started event successfully broadcast to all participants");
    }
}
