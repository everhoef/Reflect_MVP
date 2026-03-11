package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests focused on authentication flows and session management.
 * 
 * Covers:
 * - OAuth2 and guest user authentication
 * - Multi-user session creation and joining 
 * - Session switching between users
 * - Participant list updates via SSE
 * - Error handling for invalid sessions
 */
@DisplayName("Authentication and Session Management Integration Tests")
@Slf4j
public class AuthenticationAndSessionManagementIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("Multi-User Session Management")
    class MultiUserSessionManagement {

        @Test
        @DisplayName("Should allow multi-user session creation and joining with mixed authentication")
        void shouldAllowMultiUserSessionCreationAndJoining() {
            // Create contexts for 4 users: 1 OAuth2 facilitator + 3 guest participants
            BrowserContext facilitatorContext = createMonitoredContext();
            BrowserContext participant1Context = createMonitoredContext();
            BrowserContext participant2Context = createMonitoredContext();
            BrowserContext participant3Context = createMonitoredContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participant1Page = participant1Context.newPage();
            Page participant2Page = participant2Context.newPage();
            Page participant3Page = participant3Context.newPage();

            try {
                // Set up authentication for all users
                log.info("Setting up multi-user authentication");
                authenticateAsOAuth2User(facilitatorPage, "facilitator", "Alice Facilitator", "alice@example.com");
                authenticateAsGuest(participant1Page, "Bob");
                authenticateAsGuest(participant2Page, "Charlie");
                authenticateAsGuest(participant3Page, "Diana");
                
                // Facilitator creates session
                log.info("Facilitator creating session");
                String sessionId = createRetroSession(facilitatorPage, "Multi-User Test Session");
                assertNotNull(sessionId, "Session ID should not be null");
                assertFalse(sessionId.isEmpty(), "Session ID should not be empty");
                
                // All participants join the same session
                log.info("Participants joining session: {}", sessionId);
                joinRetroSession(participant1Page, sessionId);
                joinRetroSession(participant2Page, sessionId);
                joinRetroSession(participant3Page, sessionId);
                
                // Verify all users are on the retro page
                assertTrue(facilitatorPage.url().contains("/retro/" + sessionId), 
                    "Facilitator should be on retro page");
                assertTrue(participant1Page.url().contains("/retro/" + sessionId), 
                    "Bob should be on retro page");
                assertTrue(participant2Page.url().contains("/retro/" + sessionId), 
                    "Charlie should be on retro page");
                assertTrue(participant3Page.url().contains("/retro/" + sessionId),
                    "Diana should be on retro page");

                // Wait for all pages to load participant list
                waitForAllPagesElement("ul#participants-list", facilitatorPage, participant1Page, participant2Page, participant3Page);

                // Verify participants don't see home page elements
                assertThat(facilitatorPage.locator("body")).not().containsText("Create New Retrospective");
                assertThat(participant1Page.locator("body")).not().containsText("Join Existing Retrospective");
                assertThat(participant2Page.locator("body")).not().containsText("Join Existing Retrospective");
                assertThat(participant3Page.locator("body")).not().containsText("Join Existing Retrospective");

                log.info("✅ Multi-user session creation and joining successful");

            } finally {
                facilitatorContext.close();
                participant1Context.close();
                participant2Context.close();
                participant3Context.close();
            }
        }

    }

    @Nested
    @DisplayName("Comprehensive Multi-User Scenarios")
    class ComprehensiveMultiUserScenarios {

        @Test
        @DisplayName("Should support 4-user session with mixed authentication and complex session switching")
        void shouldSupportFourUserMixedAuthSessionSwitching() throws Exception {
            // Create 4 separate browser contexts for different user types
            List<BrowserContext> testContexts = new ArrayList<>();
            List<Page> testPages = new ArrayList<>();
            
            for (int i = 0; i < 4; i++) {
                BrowserContext context = createMonitoredContext();
                Page page = context.newPage();
                testContexts.add(context);
                testPages.add(page);
            }

            try {
                // Step 1: Authenticated OAuth2 user creates first session
                log.info("Step 1: OAuth2 user creating first session");
                authenticateAsOAuth2User(testPages.get(0), "michel", "Michel Test User", "michel@example.com");
                String session1Id = createRetroSession(testPages.get(0), "First Session");
                log.info("Session 1 created: {}", session1Id);

                // Step 2: Three guest users join the first session
                log.info("Step 2: Three guests joining first session");
                authenticateAsGuest(testPages.get(1), "Guest Facilitator");
                joinRetroSession(testPages.get(1), session1Id);
                
                authenticateAsGuest(testPages.get(2), "Guest User 1");
                joinRetroSession(testPages.get(2), session1Id);
                
                authenticateAsGuest(testPages.get(3), "Guest User 2");
                joinRetroSession(testPages.get(3), session1Id);

                // Step 3: Verify all 4 users are in session 1
                log.info("Step 3: Verifying all 4 users in Session 1");
                String[] allFourParticipants = {"Michel Test User", "Guest Facilitator",
                    "Guest User 1", "Guest User 2"};
                waitForAllPagesParticipantList(allFourParticipants,
                    testPages.get(0), testPages.get(1), testPages.get(2), testPages.get(3));

                // Step 4: Guest User 2 creates a new session (switches from session 1)
                log.info("Step 4: Guest User 2 creating new session (switching)");
                navigateToHome(testPages.get(3));
                String session2Id = createRetroSession(testPages.get(3), "Second Session by Guest");
                log.info("Session 2 created: {}", session2Id);
                assertNotEquals(session1Id, session2Id);

                // Step 5: Verify session 1 now has 3 participants, session 2 has 1
                log.info("Step 5: Verifying participant lists after Guest User 2 switched to Session 2");
                String[] session1After = {"Michel Test User", "Guest Facilitator", "Guest User 1"};
                waitForAllPagesParticipantList(session1After, testPages.get(0), testPages.get(1), testPages.get(2));
                waitForParticipantList(testPages.get(3), "Guest User 2");

                // Step 6: Guest User 1 switches to session 2
                log.info("Step 6: Guest User 1 switching to Session 2");

                navigateToHome(testPages.get(2));
                joinRetroSession(testPages.get(2), session2Id);

                // Step 7: Final verification after Guest User 1 switched to Session 2
                log.info("Step 7: Final verification - Session 1 has 2, Session 2 has 2");
                String[] session1Final = {"Michel Test User", "Guest Facilitator"};
                String[] session2Final = {"Guest User 1", "Guest User 2"};
                waitForAllPagesParticipantList(session1Final, testPages.get(0), testPages.get(1));
                waitForAllPagesParticipantList(session2Final, testPages.get(2), testPages.get(3));

                log.info("✅ 4-user mixed authentication session switching test completed successfully!");

            } finally {
                testPages.forEach(Page::close);
                testContexts.forEach(BrowserContext::close);
            }
        }
    }

}
