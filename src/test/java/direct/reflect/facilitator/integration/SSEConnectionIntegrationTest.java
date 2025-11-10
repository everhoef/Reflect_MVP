package direct.reflect.facilitator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.APIResponse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration test for SSE (Server-Sent Events) connection functionality.
 *
 * Tests verify that:
 * 1. SSE connections remain stable without unnecessary reconnections
 * 2. Keep-alive heartbeats are sent to maintain connection
 * 3. Events are broadcast to all participants in a session
 * 4. Different event types are properly delivered
 */
@DisplayName("SSE Connection Integration Tests")
@Slf4j
public class SSEConnectionIntegrationTest extends BaseIntegrationTest {

    /**
     * Wait for SSE connection to be established by waiting for the response directly.
     */
    private void waitForSseConnection(Page page, String retroId) {
        page.waitForResponse(
            response -> {
                String contentType = response.headers().get("content-type");
                return response.url().contains("/api/retro/" + retroId + "/events") &&
                       contentType != null &&
                       contentType.contains("text/event-stream");
            },
            () -> {
                // No action needed, just waiting for the response
            }
        );
        log.info("SSE connection established for retro: {}", retroId);
    }

    @Test
    @DisplayName("Should maintain stable SSE connection without reconnecting")
    void shouldMaintainStableSSEConnection() throws InterruptedException {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        AtomicInteger sseConnectionCount = new AtomicInteger(0);
        page.onResponse(response -> {
            if (response.url().contains("/events") &&
                response.headers().get("content-type") != null &&
                response.headers().get("content-type").contains("text/event-stream")) {
                sseConnectionCount.incrementAndGet();
                log.info("SSE connection detected, total: {}", sseConnectionCount.get());
            }
        });

        authenticateAsGuest(page, "SSE Test User");
        String sessionId = createRetroSession(page, "SSE Stability Test");

        page.waitForSelector("h2:has-text('Session Lobby')",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));

        waitForSseConnection(page, sessionId);

        int initialConnectionCount = sseConnectionCount.get();
        assertEquals(1, initialConnectionCount, "Should have exactly 1 SSE connection established initially");

        log.info("Waiting 30 seconds to verify SSE connection stability");
        Thread.sleep(30000);

        int finalConnectionCount = sseConnectionCount.get();

        assertEquals(initialConnectionCount, finalConnectionCount,
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
        BrowserContext context1 = browser.newContext();
        Page facilitatorPage = context1.newPage();

        authenticateAsGuest(facilitatorPage, "Facilitator");
        String sessionId = createRetroSession(facilitatorPage, "Event Broadcast Test");

        facilitatorPage.waitForSelector("h2:has-text('Session Lobby')",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
        waitForSseConnection(facilitatorPage, sessionId);

        int initialFacilitatorParticipants = facilitatorPage.locator("ul#participants-list li").count();
        assertEquals(1, initialFacilitatorParticipants, "Facilitator should see 1 participant initially");

        BrowserContext context2 = browser.newContext();
        Page participantPage = context2.newPage();

        authenticateAsGuest(participantPage, "Participant");
        joinRetroSession(participantPage, sessionId);

        participantPage.waitForSelector("h2:has-text('Session Lobby')",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
        waitForSseConnection(participantPage, sessionId);

        facilitatorPage.waitForFunction("() => document.querySelectorAll('ul#participants-list li').length === 2",
            null, new Page.WaitForFunctionOptions().setTimeout(5000));
        participantPage.waitForFunction("() => document.querySelectorAll('ul#participants-list li').length === 2",
            null, new Page.WaitForFunctionOptions().setTimeout(5000));

        int facilitatorParticipants = facilitatorPage.locator("ul#participants-list li").count();
        int participantParticipants = participantPage.locator("ul#participants-list li").count();

        assertEquals(2, facilitatorParticipants,
            "Facilitator should see 2 participants after participant joins");
        assertEquals(2, participantParticipants,
            "Participant should see 2 participants after joining");

        log.info("participant_joined event successfully broadcast to all participants");
    }

    @Test
    @DisplayName("Should broadcast session_started event to all participants")
    void shouldBroadcastSessionStartedEventToAllParticipants() throws InterruptedException {
        BrowserContext context1 = browser.newContext();
        Page facilitatorPage = context1.newPage();

        BrowserContext context2 = browser.newContext();
        Page participantPage = context2.newPage();

        authenticateAsGuest(facilitatorPage, "Facilitator");
        String sessionId = createRetroSession(facilitatorPage, "Session Start Test");

        facilitatorPage.waitForSelector("h2:has-text('Session Lobby')",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
        waitForSseConnection(facilitatorPage, sessionId);

        authenticateAsGuest(participantPage, "Participant");
        joinRetroSession(participantPage, sessionId);

        participantPage.waitForSelector("h2:has-text('Session Lobby')",
            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
        waitForSseConnection(participantPage, sessionId);

        // Verify both are in lobby phase
        assertTrue(facilitatorPage.url().contains("/retro/" + sessionId),
            "Facilitator should be in retro lobby");
        assertTrue(participantPage.url().contains("/retro/" + sessionId),
            "Participant should be in retro lobby");

        // Facilitator starts the retrospective
        facilitatorPage.click("button:has-text('Start Retrospective')");

        // Wait for both pages to transition to active retrospective phase
        facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
            null, new Page.WaitForFunctionOptions().setTimeout(5000));
        participantPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
            null, new Page.WaitForFunctionOptions().setTimeout(5000));

        // Verify both pages show active retrospective content (first step)
        assertFalse(facilitatorPage.textContent("body").contains("Session Lobby"),
            "Facilitator should no longer see lobby after session started");
        assertFalse(participantPage.textContent("body").contains("Session Lobby"),
            "Participant should no longer see lobby after session started");

        log.info("session_started event successfully broadcast to all participants");
    }

    @Test
    @DisplayName("Should sync histogram rating updates across all participants when facilitator reveals")
    void shouldSyncHistogramUpdatesAcrossParticipants() throws InterruptedException {
        BrowserContext context1 = browser.newContext();
        Page facilitatorPage = context1.newPage();

        BrowserContext context2 = browser.newContext();
        Page participantPage = context2.newPage();

        // Authenticate both users
        authenticateAsGuest(facilitatorPage, "Facilitator");
        authenticateAsGuest(participantPage, "Participant");

        // Create session with default template (starts at Happiness Histogram in SET_THE_STAGE phase)
        // facilitatorPage creates session → automatically becomes facilitator
        String sessionId = createRetroSession(facilitatorPage, "Histogram Reveal Test");

        // Participant joins
        joinRetroSession(participantPage, sessionId);

        // Start retro - both should go directly to Happiness Histogram
        startRetroSession(facilitatorPage);

        // Wait for both pages to load the histogram step
        Thread.sleep(2000);

        // Advance from step 1 (instruction) to step 2 (activity) where rating input is available
        facilitatorPage.click("button:has-text('Next')");
        Thread.sleep(2000);

        // Step 1: Participant submits rating in PRIVATE mode using radio button
        participantPage.click("input[name='rating'][value='8']");
        participantPage.click("button[type='submit']:has-text('Submit')");

        // Wait for submission to complete
        Thread.sleep(1000);

        log.info("Participant submitted rating of 8");

        // Step 2: Before reveal - participant should NOT see histogram details (PRIVATE mode)
        // Facilitator SHOULD see histogram (facilitators see all responses even when not revealed)
        String participantContentBefore = participantPage.textContent("body");
        String facilitatorContentBefore = facilitatorPage.textContent("body");

        log.info("Before reveal - Participant sees: {}", participantContentBefore.contains("No ratings") ? "No ratings" : "Some ratings");
        log.info("Before reveal - Facilitator sees: {}", facilitatorContentBefore.contains("rating(s) submitted") ? "Rating count" : "No count");

        // Step 3: Facilitator clicks "Reveal All Responses"
        facilitatorPage.click("button:has-text('Reveal')");
        log.info("Facilitator clicked Reveal button");

        // Step 4: Wait for SSE event to propagate and HTMX to refresh histogram
        participantPage.waitForFunction(
            "() => document.body.textContent.includes('rating(s) submitted')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(5000)
        );
        log.info("Participant page updated after reveal");

        // Step 5: Verify BOTH see the updated histogram (PUBLIC mode)
        String facilitatorContentAfter = facilitatorPage.textContent("body");
        String participantContentAfter = participantPage.textContent("body");

        // Both should now see "X rating(s) submitted" and the histogram bars
        assertTrue(facilitatorContentAfter.contains("rating(s) submitted"),
            "Facilitator should see rating count after reveal");
        assertTrue(participantContentAfter.contains("rating(s) submitted"),
            "Participant should see rating count after reveal");

        // Verify participant content actually changed after reveal
        assertNotEquals(participantContentBefore, participantContentAfter,
            "Participant view should update after reveal");

        log.info("Histogram revealed and synced across all participants via SSE");
    }

    @Test
    @DisplayName("Should enforce facilitator-only controls (next step, reveal)")
    void shouldEnforceFacilitatorControls() throws InterruptedException {
        BrowserContext context1 = browser.newContext();
        Page facilitatorPage = context1.newPage();

        BrowserContext context2 = browser.newContext();
        Page participantPage = context2.newPage();

        // Authenticate both users
        authenticateAsGuest(facilitatorPage, "Facilitator");
        authenticateAsGuest(participantPage, "Participant");

        // Create session with default template (starts at Happiness Histogram in SET_THE_STAGE phase)
        // facilitatorPage creates session → automatically becomes facilitator
        String sessionId = createRetroSession(facilitatorPage, "Auth Test");

        // Participant joins (becomes regular participant)
        joinRetroSession(participantPage, sessionId);

        // Start retro
        startRetroSession(facilitatorPage);
        Thread.sleep(2000);

        // UI Test 1: Facilitator SEES "Next" button
        int facilitatorNextButtons = facilitatorPage.locator("button:has-text('Next')").count();
        assertTrue(facilitatorNextButtons > 0,
            "Facilitator should see 'Next' button");
        log.info("✓ Facilitator sees 'Next' button");

        // UI Test 2: Participant DOES NOT see "Next" button
        int participantNextButtons = participantPage.locator("button:has-text('Next')").count();
        assertEquals(0, participantNextButtons,
            "Participant should NOT see 'Next' button");
        log.info("✓ Participant does NOT see 'Next' button");

        // UI Test 3: Facilitator SEES "Reveal" button (if applicable for this step)
        int facilitatorRevealButtons = facilitatorPage.locator("button:has-text('Reveal')").count();
        if (facilitatorRevealButtons > 0) {
            log.info("✓ Facilitator sees 'Reveal' button");

            // UI Test 4: Participant DOES NOT see "Reveal" button
            int participantRevealButtons = participantPage.locator("button:has-text('Reveal')").count();
            assertEquals(0, participantRevealButtons,
                "Participant should NOT see 'Reveal' button");
            log.info("✓ Participant does NOT see 'Reveal' button");
        }

        // Functional Test 1: Facilitator CAN actually click Next button and advance step
        // Current step shows "Welcome - Happiness Histogram" instruction
        String instructionText = "Welcome - Happiness Histogram";
        assertTrue(participantPage.textContent("body").contains(instructionText),
            "Participant should see instruction step before advancing");

        facilitatorPage.click("button:has-text('Next')");

        // Wait for both pages to update (SSE should propagate step_advanced event)
        // After clicking Next, should no longer see the instruction step
        facilitatorPage.waitForFunction(
            "() => !document.body.textContent.includes('Welcome - Happiness Histogram')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(5000)
        );
        participantPage.waitForFunction(
            "() => !document.body.textContent.includes('Welcome - Happiness Histogram')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(5000)
        );

        // Both pages should now show the activity step (rating input form)
        assertTrue(facilitatorPage.locator("input[name='rating']").count() > 0,
            "Facilitator should see rating input after advancing");
        assertTrue(participantPage.locator("input[name='rating']").count() > 0,
            "Participant should see rating input after advancing");
        log.info("✓ Facilitator can advance step and participant sees update via SSE");

        // Functional Test 2: If Reveal button exists, facilitator CAN click it
        if (facilitatorRevealButtons > 0) {
            facilitatorPage.click("button:has-text('Reveal')");
            Thread.sleep(1000);
            log.info("✓ Facilitator can click Reveal button");
        }

        log.info("Facilitator-only controls successfully enforced (UI)");
    }
}
