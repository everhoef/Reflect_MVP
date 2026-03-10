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
    @DisplayName("Authentication Flows")
    class AuthenticationFlows {

        @Test
        @DisplayName("Should support both OAuth2 and guest authentication flows")
        void shouldSupportOAuth2AndGuestAuthentication() {
            // Create browser contexts for different user types
            BrowserContext oauth2Context = createMonitoredContext();
            BrowserContext guestContext = createMonitoredContext();
            
            Page oauth2Page = oauth2Context.newPage();
            Page guestPage = guestContext.newPage();

            try {
                log.info("Testing OAuth2 user authentication");
                authenticateAsOAuth2User(oauth2Page, "testuser", "Test User", "test@example.com");
                
                // Verify OAuth2 user sees home page correctly (authentication method already navigated to home page)
                assertTrue(oauth2Page.url().contains(baseUrl), 
                    "OAuth2 user should reach home page after authentication");
                assertThat(oauth2Page.locator("body")).containsText("Welcome");
                
                log.info("Testing guest user authentication");
                authenticateAsGuest(guestPage, "Guest User");
                
                // Verify guest user sees home page correctly
                assertTrue(guestPage.url().contains(baseUrl), 
                    "Guest user should reach home page after authentication");
                assertThat(guestPage.locator("body")).containsText("Welcome");
                
                log.info("✅ Both authentication flows working correctly");
                
            } finally {
                oauth2Context.close();
                guestContext.close();
            }
        }
    }

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

        @Test
        @DisplayName("Should handle session switching between multiple users")
        void shouldHandleSessionSwitchingBetweenMultipleUsers() {
            // Create contexts for 3 users
            BrowserContext aliceContext = createMonitoredContext();
            BrowserContext bobContext = createMonitoredContext();
            BrowserContext charlieContext = createMonitoredContext();
            
            Page alicePage = aliceContext.newPage();
            Page bobPage = bobContext.newPage();
            Page charliePage = charlieContext.newPage();

            try {
                // Set up authentication
                log.info("Setting up users for session switching test");
                authenticateAsGuest(alicePage, "Alice");
                authenticateAsGuest(bobPage, "Bob");
                authenticateAsGuest(charliePage, "Charlie");
                
                // Alice creates first session
                log.info("Alice creating first session");
                String session1Id = createRetroSession(alicePage, "Session 1");
                
                // Bob creates second session  
                log.info("Bob creating second session");
                String session2Id = createRetroSession(bobPage, "Session 2");
                
                // Charlie joins session 1
                log.info("Charlie joining session 1");
                joinRetroSession(charliePage, session1Id);
                assertTrue(charliePage.url().contains("/retro/" + session1Id),
                    "Charlie should be in session 1");
                
                // Charlie switches to session 2
                log.info("Charlie switching to session 2");
                joinRetroSession(charliePage, session2Id);
                assertTrue(charliePage.url().contains("/retro/" + session2Id),
                    "Charlie should have switched to session 2");
                
                // Bob switches to session 1 (should leave his own session)
                log.info("Bob switching to session 1");
                joinRetroSession(bobPage, session1Id);
                assertTrue(bobPage.url().contains("/retro/" + session1Id),
                    "Bob should have switched to session 1");
                
                // Verify all users are in correct sessions
                assertTrue(alicePage.url().contains("/retro/" + session1Id),
                    "Alice should still be in session 1");
                assertTrue(bobPage.url().contains("/retro/" + session1Id),
                    "Bob should be in session 1");
                assertTrue(charliePage.url().contains("/retro/" + session2Id),
                    "Charlie should be in session 2");
                
                log.info("✅ Session switching working correctly");
                
            } finally {
                aliceContext.close();
                bobContext.close();
                charlieContext.close();
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

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle invalid session IDs gracefully")
        void shouldHandleInvalidSessionIDs() {
            BrowserContext userContext = createMonitoredContext();
            Page userPage = userContext.newPage();

            try {
                authenticateAsGuest(userPage, "Test User");
                
                // Try to join a non-existent session
                log.info("Testing invalid session ID handling");
                String invalidSessionId = "00000000-0000-0000-0000-000000000000";
                
                userPage.navigate(baseUrl + "/");
                userPage.waitForSelector("input[name='retroId']",
                    new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
                userPage.fill("input[name='retroId']", invalidSessionId);
                userPage.click("button:has-text('Join Session')");

                userPage.waitForSelector(".text-red-600",
                    new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));

                assertFalse(userPage.url().contains("/retro/"),
                    "Page should not navigate to a retro session for an invalid session ID");

                String errorText = userPage.locator(".text-red-600").textContent();
                assertFalse(errorText == null || errorText.isBlank(),
                    "An error message should be displayed for invalid session ID");
                log.info("✅ Invalid session ID handled gracefully with error: {}", errorText);
                
            } finally {
                userContext.close();
            }
        }
    }
}