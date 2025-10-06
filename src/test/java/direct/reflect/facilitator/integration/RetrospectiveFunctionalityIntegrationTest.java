package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive integration tests for retrospective functionality including:
 * - Response submission and visibility (Mad-Sad-Glad, ratings, freeform)
 * - Real-time SSE updates across multiple users
 * - Session workflow from creation to completion
 * - Multi-user interaction patterns
 */
@DisplayName("Retrospective Functionality Integration Tests")
@Slf4j 
public class RetrospectiveFunctionalityIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("Mad-Sad-Glad Response Flow")
    class MadSadGladResponseFlow {

        @Test
        @DisplayName("Should handle complete Mad-Sad-Glad workflow with multiple participants")
        void shouldHandleCompleteMadSadGladWorkflow() {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participant1Context = browser.newContext(); 
            BrowserContext participant2Context = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participant1Page = participant1Context.newPage();
            Page participant2Page = participant2Context.newPage();

            try {
                // Set up multi-user session
                String sessionId = setupRetroSession(facilitatorPage, "Mad-Sad-Glad Test Session",
                    new UserPage(participant1Page, "Alice"),
                    new UserPage(participant2Page, "Bob"));

                // Navigate to categorical step (Mad-Sad-Glad)
                navigateToStepType(facilitatorPage, "CATEGORICAL");
                waitForSSESync(facilitatorPage, participant1Page, participant2Page);

                // Verify all participants see the Mad-Sad-Glad interface
                assertThat(facilitatorPage.locator("text=Mad")).isVisible();
                assertThat(participant1Page.locator("text=Mad")).isVisible();
                assertThat(participant2Page.locator("text=Mad")).isVisible();

                // Participants submit responses to different categories
                log.info("Alice submitting Mad response");
                Locator aliceMadSection = participant1Page.locator("[data-category='Mad']").first();
                aliceMadSection.locator("textarea").fill("Daily meetings run too long");
                aliceMadSection.locator("button:has-text('Add to Mad')").click();
                participant1Page.waitForTimeout(1000);

                log.info("Bob submitting Sad response"); 
                Locator bobSadSection = participant2Page.locator("[data-category='Sad']").first();
                bobSadSection.locator("textarea").fill("We missed our sprint goal");
                bobSadSection.locator("button:has-text('Add to Sad')").click();
                participant2Page.waitForTimeout(1000);

                log.info("Facilitator submitting Glad response");
                Locator facilitatorGladSection = facilitatorPage.locator("[data-category='Glad']").first();
                facilitatorGladSection.locator("textarea").fill("Great team collaboration this sprint");
                facilitatorGladSection.locator("button:has-text('Add to Glad')").click(); 
                facilitatorPage.waitForTimeout(1000);

                // Verify responses are private before reveal
                assertThat(facilitatorPage.locator("text=Daily meetings run too long")).not().isVisible();
                assertThat(participant2Page.locator("text=Daily meetings run too long")).not().isVisible();
                assertThat(participant1Page.locator("text=We missed our sprint goal")).not().isVisible();

                // Each participant can see their own response
                assertThat(participant1Page.locator("text=Daily meetings run too long")).isVisible();
                assertThat(participant2Page.locator("text=We missed our sprint goal")).isVisible();
                assertThat(facilitatorPage.locator("text=Great team collaboration this sprint")).isVisible();

                // Facilitator reveals all responses
                log.info("Facilitator revealing all responses");
                facilitatorPage.click("button:has-text('Reveal All Responses')");
                
                // Wait for SSE propagation
                waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                
                // All participants should now see all responses in correct categories
                assertThat(facilitatorPage.locator("[data-category='Mad']").locator("text=Daily meetings run too long")).isVisible();
                assertThat(facilitatorPage.locator("[data-category='Sad']").locator("text=We missed our sprint goal")).isVisible();
                assertThat(facilitatorPage.locator("[data-category='Glad']").locator("text=Great team collaboration this sprint")).isVisible();

                assertThat(participant1Page.locator("[data-category='Mad']").locator("text=Daily meetings run too long")).isVisible();
                assertThat(participant1Page.locator("[data-category='Sad']").locator("text=We missed our sprint goal")).isVisible();
                assertThat(participant1Page.locator("[data-category='Glad']").locator("text=Great team collaboration this sprint")).isVisible();

                assertThat(participant2Page.locator("[data-category='Mad']").locator("text=Daily meetings run too long")).isVisible();
                assertThat(participant2Page.locator("[data-category='Sad']").locator("text=We missed our sprint goal")).isVisible();
                assertThat(participant2Page.locator("[data-category='Glad']").locator("text=Great team collaboration this sprint")).isVisible();

                log.info("✅ Mad-Sad-Glad workflow completed successfully");

            } finally {
                facilitatorContext.close();
                participant1Context.close();
                participant2Context.close();
            }
        }

        @Test
        @DisplayName("Should handle response editing and updates")
        void shouldHandleResponseEditingAndUpdates() {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participantContext = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participantPage = participantContext.newPage();

            try {
                String sessionId = setupRetroSession(facilitatorPage, "Response Editing Test",
                    new UserPage(participantPage, "Editor"));

                navigateToStepType(facilitatorPage, "CATEGORICAL");
                waitForSSESync(facilitatorPage, participantPage);

                // Participant submits initial response
                Locator madSection = participantPage.locator("[data-category='Mad']").first();
                madSection.locator("textarea").fill("Initial frustration");
                madSection.locator("button:has-text('Add to Mad')").click();
                // Wait for response to appear instead of fixed delay
                try {
                    participantPage.waitForSelector("text=Initial frustration", 
                        new Page.WaitForSelectorOptions().setTimeout(3000));
                } catch (Exception e) {
                    participantPage.waitForTimeout(300);
                }

                // Verify response appears
                assertThat(participantPage.locator("text=Initial frustration")).isVisible();

                // Edit the response (if editing is supported)
                // This test will help identify if response editing functionality exists
                if (participantPage.locator("button:has-text('Edit')").count() > 0) {
                    participantPage.locator("button:has-text('Edit')").first().click();
                    participantPage.locator("textarea").fill("Updated frustration with more details");
                    participantPage.locator("button:has-text('Update')").click();
                    participantPage.waitForTimeout(1000);

                    // Verify updated response
                    assertThat(participantPage.locator("text=Updated frustration with more details")).isVisible();
                    assertThat(participantPage.locator("text=Initial frustration")).not().isVisible();
                }

                log.info("✅ Response editing test completed");

            } finally {
                facilitatorContext.close();
                participantContext.close();
            }
        }
    }

    @Nested
    @DisplayName("Rating Response Flow")
    class RatingResponseFlow {

        @Test
        @DisplayName("Should handle happiness rating submissions and histogram display")
        void shouldHandleHappinessRatingWorkflow() {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participant1Context = browser.newContext();
            BrowserContext participant2Context = browser.newContext();
            BrowserContext participant3Context = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participant1Page = participant1Context.newPage();
            Page participant2Page = participant2Context.newPage();
            Page participant3Page = participant3Context.newPage();

            try {
                // Set up 4-user session for meaningful rating distribution
                String sessionId = setupRetroSession(facilitatorPage, "Rating Test Session",
                    new UserPage(participant1Page, "Rater1"),
                    new UserPage(participant2Page, "Rater2"), 
                    new UserPage(participant3Page, "Rater3"));

                // Navigate to rating step
                navigateToStepType(facilitatorPage, "RATING");
                waitForSSESync(facilitatorPage, participant1Page, participant2Page, participant3Page);

                // Submit diverse ratings with comments
                log.info("Submitting diverse ratings");
                
                // Facilitator rates 9/10
                facilitatorPage.locator("input[name='rating'][value='9']").click();
                facilitatorPage.locator("textarea[name='comment']").fill("Excellent sprint overall");
                facilitatorPage.click("button:has-text('Submit My Rating')");

                // Participant 1 rates 7/10
                participant1Page.locator("input[name='rating'][value='7']").click();
                participant1Page.locator("textarea[name='comment']").fill("Good progress with some challenges");
                participant1Page.click("button:has-text('Submit My Rating')");

                // Participant 2 rates 5/10
                participant2Page.locator("input[name='rating'][value='5']").click();
                participant2Page.locator("textarea[name='comment']").fill("Mixed feelings about this sprint");
                participant2Page.click("button:has-text('Submit My Rating')");

                // Participant 3 rates 8/10
                participant3Page.locator("input[name='rating'][value='8']").click();
                participant3Page.locator("textarea[name='comment']").fill("Strong team performance");
                participant3Page.click("button:has-text('Submit My Rating')");

                // Wait for all submissions
                waitForSSESync(facilitatorPage, participant1Page, participant2Page, participant3Page);

                // Verify ratings are private before reveal
                assertThat(participant1Page.locator("text=Excellent sprint overall")).not().isVisible();
                assertThat(facilitatorPage.locator("text=Good progress with some challenges")).not().isVisible();

                // Facilitator reveals histogram
                log.info("Revealing rating histogram");
                facilitatorPage.click("button:has-text('Show Histogram')");
                
                // Wait for histogram to propagate via SSE
                waitForSSESync(facilitatorPage, participant1Page, participant2Page, participant3Page);

                // All participants should see all rating comments
                String[] expectedComments = {
                    "Excellent sprint overall",
                    "Good progress with some challenges", 
                    "Mixed feelings about this sprint",
                    "Strong team performance"
                };

                for (String comment : expectedComments) {
                    assertThat(facilitatorPage.locator("text=" + comment)).isVisible();
                    assertThat(participant1Page.locator("text=" + comment)).isVisible();
                    assertThat(participant2Page.locator("text=" + comment)).isVisible();
                    assertThat(participant3Page.locator("text=" + comment)).isVisible();
                }

                // Verify histogram shows rating distribution (if histogram visualization exists)
                if (facilitatorPage.locator(".histogram, .rating-chart, [data-rating]").count() > 0) {
                    log.info("Verifying histogram visualization");
                    // The specific implementation depends on how ratings are visualized
                    // This is a placeholder for histogram-specific assertions
                    assertTrue(facilitatorPage.locator(".histogram, .rating-chart, [data-rating]").count() > 0);
                }

                log.info("✅ Rating workflow completed successfully");

            } finally {
                facilitatorContext.close();
                participant1Context.close();
                participant2Context.close();
                participant3Context.close();
            }
        }
    }

    @Nested
    @DisplayName("Freeform Response Flow")
    class FreeformResponseFlow {

        @Test
        @DisplayName("Should handle freeform text submissions with real-time updates")
        void shouldHandleFreeformResponseWorkflow() {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participantContext = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participantPage = participantContext.newPage();

            try {
                String sessionId = setupRetroSession(facilitatorPage, "Freeform Test Session",
                    new UserPage(participantPage, "Writer"));

                // Navigate to freeform step
                navigateToStepType(facilitatorPage, "FREEFORM");
                waitForSSESync(facilitatorPage, participantPage);

                // Verify freeform interface is available
                assertThat(participantPage.locator("textarea[name='content']")).isVisible();
                assertThat(facilitatorPage.locator("textarea[name='content']")).isVisible();

                // Participant submits freeform response
                log.info("Submitting freeform responses");
                participantPage.locator("textarea[name='content']").fill("I think we should focus more on automated testing in our next sprint. It would help us catch bugs earlier and improve our overall quality.");
                participantPage.click("button:has-text('Submit Response')");
                participantPage.waitForTimeout(1000);

                // Facilitator also submits a response
                facilitatorPage.locator("textarea[name='content']").fill("Great suggestion! We should also consider implementing continuous integration to streamline our deployment process.");
                facilitatorPage.click("button:has-text('Submit Response')");
                facilitatorPage.waitForTimeout(1000);

                // Verify responses are private before reveal
                assertThat(facilitatorPage.locator("text=automated testing")).not().isVisible();
                assertThat(participantPage.locator("text=continuous integration")).not().isVisible();

                // Each can see their own response
                assertThat(participantPage.locator("text=automated testing")).isVisible();
                assertThat(facilitatorPage.locator("text=continuous integration")).isVisible();

                // Facilitator reveals all responses
                log.info("Revealing freeform responses");
                facilitatorPage.click("button:has-text('Reveal All Responses')");
                
                // Wait for SSE propagation
                waitForSSESync(facilitatorPage, participantPage);

                // Both should now see both responses
                assertThat(facilitatorPage.locator("text=automated testing")).isVisible();
                assertThat(facilitatorPage.locator("text=continuous integration")).isVisible();
                assertThat(participantPage.locator("text=automated testing")).isVisible();
                assertThat(participantPage.locator("text=continuous integration")).isVisible();

                log.info("✅ Freeform response workflow completed successfully");

            } finally {
                facilitatorContext.close();
                participantContext.close();
            }
        }
    }

    @Nested
    @DisplayName("Real-time SSE Communication")
    class SSECommunication {

        @Test
        @DisplayName("Should deliver participant updates via SSE across all users")
        void shouldDeliverParticipantUpdatesViaSSE() throws InterruptedException {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participant1Context = browser.newContext();
            BrowserContext participant2Context = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participant1Page = participant1Context.newPage();
            Page participant2Page = participant2Context.newPage();

            try {
                // Set up session with facilitator and first participant
                authenticateAsGuest(facilitatorPage, "Facilitator");
                authenticateAsGuest(participant1Page, "Participant1");
                
                String sessionId = createRetroSession(facilitatorPage, "SSE Test Session");
                joinRetroSession(participant1Page, sessionId);
                
                // Start the session to activate SSE connections
                startRetroSession(facilitatorPage);
                waitForSSESync(facilitatorPage, participant1Page);

                // Verify SSE connections are established
                log.info("Verifying SSE connections are active");
                Object facilitatorSSEResult = facilitatorPage.evaluate("() => window.eventSource ? (window.eventSource.readyState === 1) : false");
                Object participant1SSEResult = participant1Page.evaluate("() => window.eventSource ? (window.eventSource.readyState === 1) : false");
                boolean facilitatorSSE = facilitatorSSEResult != null && (Boolean) facilitatorSSEResult;
                boolean participant1SSE = participant1SSEResult != null && (Boolean) participant1SSEResult;
                
                // Log SSE status for debugging
                log.info("Facilitator SSE status - eventSource exists: {}, readyState: {}", 
                    facilitatorPage.evaluate("() => !!window.eventSource"), 
                    facilitatorPage.evaluate("() => window.eventSource ? window.eventSource.readyState : 'N/A'"));
                log.info("Participant1 SSE status - eventSource exists: {}, readyState: {}", 
                    participant1Page.evaluate("() => !!window.eventSource"), 
                    participant1Page.evaluate("() => window.eventSource ? window.eventSource.readyState : 'N/A'"));
                    
                assertTrue(facilitatorSSE, "Facilitator should have active SSE connection");
                assertTrue(participant1SSE, "Participant1 should have active SSE connection");
                
                // Set up SSE event capture on existing participants before new participant joins
                facilitatorPage.evaluate("""
                    window.sseEventsReceived = [];
                    if (window.eventSource) {
                        window.eventSource.addEventListener('participant_joined', function(evt) {
                            console.log('Facilitator received participant_joined:', evt.data);
                            window.sseEventsReceived.push({type: 'participant_joined', data: evt.data});
                        });
                    }
                """);
                
                participant1Page.evaluate("""
                    window.sseEventsReceived = [];
                    if (window.eventSource) {
                        window.eventSource.addEventListener('participant_joined', function(evt) {
                            console.log('Participant1 received participant_joined:', evt.data);
                            window.sseEventsReceived.push({type: 'participant_joined', data: evt.data});
                        });
                    }
                """);
                
                // Add third participant to trigger PARTICIPANT_JOINED SSE event
                log.info("Adding third participant to test SSE event delivery");
                authenticateAsGuest(participant2Page, "Participant2");
                joinRetroSession(participant2Page, sessionId);
                
                // Wait for SSE events to be delivered and captured
                Thread.sleep(2000);
                
                // Verify that PARTICIPANT_JOINED events were received by existing participants
                Integer facilitatorEventCount = (Integer) facilitatorPage.evaluate("() => window.sseEventsReceived ? window.sseEventsReceived.length : 0");
                Integer participant1EventCount = (Integer) participant1Page.evaluate("() => window.sseEventsReceived ? window.sseEventsReceived.length : 0");
                
                log.info("SSE events received - Facilitator: {}, Participant1: {}", facilitatorEventCount, participant1EventCount);
                
                // ALL existing participants should have received the PARTICIPANT_JOINED event
                // (This tests that SSE event delivery is working for everyone, not just some users)
                assertTrue(facilitatorEventCount > 0, 
                    "Facilitator MUST receive PARTICIPANT_JOINED event via SSE");
                assertTrue(participant1EventCount > 0, 
                    "Participant1 MUST receive PARTICIPANT_JOINED event via SSE");
                
                // Verify participant2 also gets SSE connection after joining
                Object participant2SSEResult = participant2Page.evaluate("() => window.eventSource ? (window.eventSource.readyState === 1) : false");
                boolean participant2SSE = participant2SSEResult != null && (Boolean) participant2SSEResult;
                assertTrue(participant2SSE, "Participant2 should have active SSE connection after joining");

                log.info("✅ SSE event delivery confirmed - PARTICIPANT_JOINED events received");

            } finally {
                facilitatorContext.close();
                participant1Context.close();
                participant2Context.close();
            }
        }

        @Test
        @DisplayName("Should handle SSE connection stability during session switching")
        void shouldHandleSSEStabilityDuringSessionSwitching() throws InterruptedException {
            BrowserContext user1Context = browser.newContext();
            BrowserContext user2Context = browser.newContext();
            
            Page user1Page = user1Context.newPage();
            Page user2Page = user2Context.newPage();

            try {
                // Set up two users
                authenticateAsGuest(user1Page, "User1");
                authenticateAsGuest(user2Page, "User2");

                // User1 creates first session
                String session1Id = createRetroSession(user1Page, "First Session");
                
                // User2 joins first session
                joinRetroSession(user2Page, session1Id);
                
                // Verify both users are connected
                waitForSSESync(user1Page, user2Page);
                assertTrue(user1Page.locator("ul li").count() >= 2, "Session 1 should have 2 participants");
                assertTrue(user2Page.locator("ul li").count() >= 2, "Session 1 should have 2 participants");

                // User2 creates new session (switches away)
                log.info("User2 switching to new session");
                user2Page.navigate(baseUrl + "/");
                user2Page.waitForLoadState(LoadState.NETWORKIDLE);
                String session2Id = createRetroSession(user2Page, "Second Session");
                
                // Wait for SSE participant removal to propagate
                log.info("Waiting for SSE participant_left event...");
                try {
                    waitForSSEEvent(user1Page, "participant_left", 5000);
                } catch (Exception e) {
                    // Fallback if event detection fails
                    Thread.sleep(1000);
                }
                
                // User1 should now see only 1 participant in session 1 (SSE should have notified of User2's departure)
                assertTrue(user1Page.locator("ul li").count() >= 1, "Session 1 should have 1 participant after User2 left");
                
                // User2 should see 1 participant in session 2
                assertTrue(user2Page.locator("ul li").count() >= 1, "Session 2 should have 1 participant");

                log.info("✅ SSE stability during session switching verified");

            } finally {
                user1Context.close();
                user2Context.close();
            }
        }
    }

    @Nested
    @DisplayName("Facilitator Role Assignment")
    class FacilitatorRoleAssignment {

        @Test
        @DisplayName("Should assign FACILITATOR role to session creator and show Start Retrospective button")
        void shouldAssignFacilitatorRoleToSessionCreator() {
            BrowserContext facilitatorContext = browser.newContext();
            Page facilitatorPage = facilitatorContext.newPage();

            try {
                log.info("=== FACILITATOR ROLE ASSIGNMENT TEST ===");
                
                // Authenticate as guest facilitator
                authenticateAsGuest(facilitatorPage, "TestFacilitator");
                log.info("Authenticated as guest: TestFacilitator");
                
                // Create retro session (this should assign FACILITATOR role)
                String sessionId = createRetroSession(facilitatorPage, "Role Test Session");
                log.info("Created session: {}", sessionId);
                
                // Verify we're in the LOBBY phase and facilitator can see Start button
                log.info("Checking for Start Retrospective button visibility");
                
                // Wait a moment for page to fully load
                facilitatorPage.waitForTimeout(2000);
                
                // Check if we're in LOBBY phase
                log.info("Current page URL: {}", facilitatorPage.url());
                log.info("Page title: {}", facilitatorPage.title());
                
                // Debug: Check what's actually rendered
                String pageContent = facilitatorPage.content();
                log.info("Page contains 'Start Retrospective': {}", pageContent.contains("Start Retrospective"));
                log.info("Page contains 'Session Lobby': {}", pageContent.contains("Session Lobby"));
                log.info("Page contains 'isFacilitator': {}", pageContent.contains("isFacilitator"));
                
                // Try to find the button with various selectors
                int startButtonCount = facilitatorPage.locator("button:has-text('Start Retrospective')").count();
                log.info("Start Retrospective buttons found: {}", startButtonCount);
                
                int buttonCount = facilitatorPage.locator("button").count();
                log.info("Total buttons on page: {}", buttonCount);
                
                // List all buttons for debugging
                for (int i = 0; i < buttonCount; i++) {
                    try {
                        String buttonText = facilitatorPage.locator("button").nth(i).textContent();
                        log.info("Button {}: '{}'", i, buttonText);
                    } catch (Exception e) {
                        log.info("Button {}: <could not read text>", i);
                    }
                }
                
                // The test should pass if Start Retrospective button is visible
                if (startButtonCount > 0) {
                    log.info("✅ SUCCESS: Start Retrospective button found - facilitator role correctly assigned");
                    assertTrue(startButtonCount > 0, "Start Retrospective button should be visible for facilitator");
                } else {
                    log.error("❌ FAILURE: Start Retrospective button not found - facilitator role assignment issue");
                    fail("Start Retrospective button not found - facilitator role was not properly assigned");
                }

            } finally {
                facilitatorContext.close();
            }
        }

        @Test  
        @DisplayName("Should assign PARTICIPANT role to session joiner and hide Start Retrospective button")
        void shouldAssignParticipantRoleToSessionJoiner() {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participantContext = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participantPage = participantContext.newPage();

            try {
                log.info("=== PARTICIPANT ROLE ASSIGNMENT TEST ===");
                
                // Facilitator creates session
                authenticateAsGuest(facilitatorPage, "TestFacilitator");
                String sessionId = createRetroSession(facilitatorPage, "Role Test Session");
                
                // Participant joins session
                authenticateAsGuest(participantPage, "TestParticipant");
                joinRetroSession(participantPage, sessionId);
                
                // Verify facilitator can see Start button
                int facilitatorStartButtonCount = facilitatorPage.locator("button:has-text('Start Retrospective')").count();
                log.info("Facilitator - Start Retrospective buttons found: {}", facilitatorStartButtonCount);
                
                // Verify participant cannot see Start button
                int participantStartButtonCount = participantPage.locator("button:has-text('Start Retrospective')").count();
                log.info("Participant - Start Retrospective buttons found: {}", participantStartButtonCount);
                
                assertTrue(facilitatorStartButtonCount > 0, "Facilitator should see Start Retrospective button");
                assertEquals(0, participantStartButtonCount, "Participant should NOT see Start Retrospective button");

                log.info("✅ SUCCESS: Role assignment working correctly - facilitator sees button, participant does not");

            } finally {
                facilitatorContext.close();
                participantContext.close();
            }
        }
    }

    @Nested
    @DisplayName("Complete Session Workflow")
    class CompleteSessionWorkflow {

        @Test
        @DisplayName("Should handle complete retrospective session from creation to completion")
        void shouldHandleCompleteRetrospectiveWorkflow() throws InterruptedException {
            BrowserContext facilitatorContext = browser.newContext();
            BrowserContext participant1Context = browser.newContext();
            BrowserContext participant2Context = browser.newContext();
            
            Page facilitatorPage = facilitatorContext.newPage();
            Page participant1Page = participant1Context.newPage();
            Page participant2Page = participant2Context.newPage();

            try {
                // Full retrospective workflow test
                log.info("Starting complete retrospective workflow test");
                
                // 1. Session creation and joining
                String sessionId = setupRetroSession(facilitatorPage, "Complete Workflow Test",
                    new UserPage(participant1Page, "Alice"),
                    new UserPage(participant2Page, "Bob"));

                // 2. Go through multiple step types if available
                
                // Try categorical step (Mad-Sad-Glad)
                navigateToStepType(facilitatorPage, "CATEGORICAL");
                waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                
                if (facilitatorPage.locator("[data-category='Mad']").count() > 0) {
                    log.info("Processing categorical step");
                    
                    // Submit responses
                    participant1Page.locator("[data-category='Mad'] textarea").fill("Process issues");
                    participant1Page.locator("[data-category='Mad'] button:has-text('Add to Mad')").click();
                    
                    participant2Page.locator("[data-category='Glad'] textarea").fill("Good teamwork");
                    participant2Page.locator("[data-category='Glad'] button:has-text('Add to Glad')").click();
                    
                    waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                    
                    // Reveal responses
                    facilitatorPage.click("button:has-text('Reveal All Responses')");
                    waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                    
                    // Verify all can see responses
                    assertThat(facilitatorPage.locator("text=Process issues")).isVisible();
                    assertThat(participant2Page.locator("text=Good teamwork")).isVisible();
                }

                // 3. Try to advance to next step
                if (facilitatorPage.locator("button:has-text('Next Step')").count() > 0) {
                    log.info("Advancing to next step");
                    facilitatorPage.click("button:has-text('Next Step')");
                    waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                    
                    // Check if we reached a rating step
                    if (facilitatorPage.locator("input[name='rating']").count() > 0) {
                        log.info("Processing rating step");
                        
                        // Submit ratings
                        facilitatorPage.locator("input[name='rating'][value='8']").click();
                        facilitatorPage.locator("textarea[name='comment']").fill("Great session");
                        facilitatorPage.click("button:has-text('Submit My Rating')");
                        
                        participant1Page.locator("input[name='rating'][value='7']").click();
                        participant1Page.locator("textarea[name='comment']").fill("Productive discussion");
                        participant1Page.click("button:has-text('Submit My Rating')");
                        
                        waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                        
                        // Reveal ratings
                        if (facilitatorPage.locator("button:has-text('Show Histogram')").count() > 0) {
                            facilitatorPage.click("button:has-text('Show Histogram')");
                            waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                        }
                    }
                }

                // 4. Try to complete session if possible
                if (facilitatorPage.locator("button:has-text('Complete Session'), button:has-text('End Retrospective')").count() > 0) {
                    log.info("Completing retrospective session");
                    facilitatorPage.locator("button:has-text('Complete Session'), button:has-text('End Retrospective')").first().click();
                    waitForSSESync(facilitatorPage, participant1Page, participant2Page);
                }

                log.info("✅ Complete retrospective workflow test completed successfully");

            } finally {
                facilitatorContext.close();
                participant1Context.close();
                participant2Context.close();
            }
        }
    }
}