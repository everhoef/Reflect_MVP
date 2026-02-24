package direct.reflect.facilitator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

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
 * Integration test for SSE (Server-Sent Events) infrastructure.
 *
 * Tests verify that:
 * 1. SSE connections remain stable without unnecessary reconnections
 * 2. Events are broadcast to all participants in a session (participant_joined, session_started)
 * 3. Our EventService correctly publishes events that trigger UI updates
 *
 * Component behavior and retrospective flow tests are in {@link RetroFlowIntegrationTest}.
 */
@DisplayName("SSE Infrastructure Integration Tests")
@Slf4j
public class SSEConnectionIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should maintain stable SSE connection without reconnecting")
    @DirtiesContext // Clean up after this 30-second SSE test to prevent contamination
    void shouldMaintainStableSSEConnection() throws InterruptedException {
        BrowserContext context = createMonitoredContext();
        Page page = context.newPage();

        authenticateAsGuest(page, "SSE Test User");
        String sessionId = createRetroSession(page, "SSE Stability Test");

        waitForElement(page, "h2:has-text('Session Lobby')");

        // Wait for initial SSE connection
        waitForSseConnection(page, UUID.fromString(sessionId));

        // Verify SSE connection element is present using hx-ext="sse" which is more stable
        // The element with hx-ext="sse" is the parent container that manages SSE connections
        int initialSseElements = page.locator("[hx-ext='sse']").count();
        assertEquals(1, initialSseElements, "Should have exactly 1 SSE connection element initially");

        log.info("Waiting 30 seconds to verify SSE connection stability");
        Thread.sleep(30000);

        // Verify SSE connection element is still present (not reconnected/duplicated)
        int finalSseElements = page.locator("[hx-ext='sse']").count();

        assertEquals(initialSseElements, finalSseElements,
            "SSE connection should remain stable for 30 seconds without reconnecting");

        log.info("SSE connection remained stable for 30 seconds with no reconnections");
    }

    // @Test
    @DisplayName("Should receive keep-alive heartbeat events")
    void shouldReceiveKeepAliveHeartbeats() throws InterruptedException {
        // TODO: Implement after stability test passes
    }

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
        clickElement(facilitatorPage, "button:has-text('Start Retrospective')");

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
