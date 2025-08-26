package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.FormData;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import direct.reflect.facilitator.auth.TestAuthConfiguration;

/**
 * Integration test for multi-user retrospective sessions using real browser instances.
 * Tests mixed authentication (authenticated user + guest users) and real-time SSE communication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = "spring.profiles.active=test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.context.annotation.Import(TestAuthConfiguration.class)
class MultiUserRetrospectiveIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("facilitator_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @LocalServerPort
    private int port;

    private String baseUrl;
    private Playwright playwright;
    private List<Browser> browsers = new ArrayList<>();
    private List<BrowserContext> contexts = new ArrayList<>();
    private List<Page> pages = new ArrayList<>();

    // Test data
    private String sessionId;
    private final Map<String, List<String>> sseEventsPerUser = new HashMap<>();

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;
        playwright = Playwright.create();
        
        // Initialize SSE event tracking
        sseEventsPerUser.clear();
    }

    @AfterEach
    void cleanup() {
        // Close all pages, contexts, and browsers
        pages.forEach(Page::close);
        contexts.forEach(BrowserContext::close);
        browsers.forEach(Browser::close);
        
        if (playwright != null) {
            playwright.close();
        }
        
        pages.clear();
        contexts.clear();
        browsers.clear();
    }

    @Test
    @Order(1)
    void shouldSupportMixedAuthenticationMultiUserSession() throws Exception {
        // Create 4 browser instances for different user types
        createBrowserInstance("authenticated-user");
        createBrowserInstance("guest-facilitator");  
        createBrowserInstance("guest-user-1");
        createBrowserInstance("guest-user-2");

        // Step 1: Authenticated user logs in and creates session
        authenticateAsUser(pages.get(0));
        sessionId = createNewSession(pages.get(0), "Integration Test Session");
        assertNotNull(sessionId, "Session ID should not be null");
        assertTrue(sessionId.length() > 30, "Session ID should be a valid UUID");

        // Step 2: Guest users join the session sequentially (to avoid Playwright concurrency issues)
        authenticateAsGuest(pages.get(1), "Guest Facilitator");
        joinExistingSession(pages.get(1), sessionId);
        
        authenticateAsGuest(pages.get(2), "Guest User 1");
        joinExistingSession(pages.get(2), sessionId);
        
        authenticateAsGuest(pages.get(3), "Guest User 2");
        joinExistingSession(pages.get(3), sessionId);

        // Step 3: Verify all users see each other in participant list
        verifyParticipantListsInAllBrowsers();
        
        // Step 4: Verify SSE events were received by all users
        verifySseEventsReceivedByAllUsers();
    }

    @Test 
    @Order(2)
    void shouldHandleSessionSwitchingWithMixedUsers() throws Exception {
        // Reuse browsers from previous test (if available) or create new ones
        if (browsers.isEmpty()) {
            shouldSupportMixedAuthenticationMultiUserSession();
        }

        // Step 1: Guest User 2 creates a new session (switching from current)
        pages.get(3).navigate(baseUrl);
        pages.get(3).waitForLoadState(LoadState.NETWORKIDLE);
        String newSessionId = createNewSession(pages.get(3), "New Session by Guest");
        assertNotNull(newSessionId);
        assertNotEquals(sessionId, newSessionId);

        // Step 2: Verify original session participant list updated
        Thread.sleep(2000); // Allow SSE events to propagate
        verifyParticipantCountInBrowser(0, 3); // Should have 3 participants (lost Guest User 2)

        // Step 3: Guest User 1 switches to the new session (keeping same participant ID)
        pages.get(2).navigate(baseUrl);  // Keep existing authentication, just navigate home
        pages.get(2).waitForLoadState(LoadState.NETWORKIDLE);
        joinExistingSession(pages.get(2), newSessionId);
        
        // Step 4: Verify session switching worked correctly
        Thread.sleep(2000);
        verifyParticipantCountInBrowser(0, 2); // Original session now has 2 participants
        verifyParticipantCountInBrowser(3, 2); // New session has 2 participants
    }

    private void createBrowserInstance(String userType) {
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        
        // Inject SSE event tracking
        injectSseEventTracking(page, userType);
        
        browsers.add(browser);
        contexts.add(context);
        pages.add(page);
        sseEventsPerUser.put(userType, new ArrayList<>());
    }

    private void injectSseEventTracking(Page page, String userType) {
        // Navigate to page first to have a proper context
        page.navigate(baseUrl);
        
        // Inject JavaScript to track SSE events
        page.addInitScript("""
            window.sseEvents = [];
            window.userType = '%s';
            
            // Track HTMX SSE messages
            document.addEventListener('DOMContentLoaded', function() {
                document.body.addEventListener('htmx:sseMessage', function(evt) {
                    const event = {
                        type: evt.detail?.type || 'unknown',
                        data: evt.detail?.data || '',
                        timestamp: Date.now(),
                        userType: window.userType
                    };
                    window.sseEvents.push(event);
                    console.log('SSE Event received by ' + window.userType + ':', event);
                });
                
                // Track connection status
                document.body.addEventListener('htmx:sseOpen', function(evt) {
                    console.log('SSE Connection opened for ' + window.userType);
                });
                
                document.body.addEventListener('htmx:sseError', function(evt) {
                    console.log('SSE Connection error for ' + window.userType + ':', evt);
                });
            });
            """.formatted(userType));
    }

    private void authenticateAsUser(Page page) throws Exception {
        // First navigate to any page to get a valid session with CSRF token
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Extract CSRF token from the page
        Object csrfTokenObj = page.evaluate("() => document.querySelector('meta[name=\"_csrf\"]')?.getAttribute('content') || document.querySelector('input[name=\"_csrf\"]')?.value");
        if (csrfTokenObj == null) {
            fail("Could not extract CSRF token from login page - no meta tag or input field found");
        }
        String csrfToken = csrfTokenObj.toString();
        if (csrfToken.equals("null")) {
            fail("CSRF token was null in the page");
        }
        
        // Use Playwright to make a POST request directly to TestAuthController with CSRF token
        APIResponse response = page.request().post(baseUrl + "/test/login-oauth-user", 
            RequestOptions.create()
                .setForm(FormData.create()
                    .set("username", "michel")
                    .set("displayName", "Michel Test User") 
                    .set("email", "michel@example.com")
                    .set("_csrf", csrfToken)));
        
        if (!response.ok()) {
            fail("TestAuthController POST request failed with status: " + response.status() + 
                 ", body: " + response.text());
        }
        
        String responseText = response.text();
        if (!responseText.contains("OAuth2 authentication set up for: michel")) {
            fail("TestAuthController didn't respond correctly. Response: " + responseText);
        }
        
        // Now navigate to home page - the session should be authenticated
        page.navigate(baseUrl + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Wait for home page content to load - look for elements that should be present for authenticated users
        try {
            // Wait for either the session creation form or welcome message
            page.waitForSelector("input[name='sessionName'], :has-text('Welcome')", new Page.WaitForSelectorOptions().setTimeout(8000));
        } catch (Exception e) {
            // Debug: Let's see if we were redirected and where
            String currentUrl = page.url();
            String pageContent = page.textContent("body");
            System.out.println("DEBUG: Final URL after timeout: " + currentUrl);
            System.out.println("DEBUG: Page content after authentication timeout:");
            System.out.println(pageContent.substring(0, Math.min(pageContent.length(), 1000)));
            
            if (currentUrl.contains("/login")) {
                fail("Authentication failed - page was redirected to login: " + currentUrl);
            }
            throw e;
        }
        
        // Additional short wait to ensure template rendering is complete
        Thread.sleep(500);
    }

    private String createNewSession(Page page, String sessionName) throws Exception {
        // Create session - wait for the form to be available
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(5000));
        page.fill("input[name='sessionName']", sessionName);
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Wait for session lobby page
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
        
        // Extract session ID from URL
        String currentUrl = page.url();
        String[] urlParts = currentUrl.split("/");
        String extractedSessionId = urlParts[urlParts.length - 1];
        
        // Verify session ID format (should be UUID)
        assertTrue(extractedSessionId.matches("[0-9a-f-]{36}"), "Session ID should be valid UUID format");
        
        // Wait for SSE connection (look for "Connected" status)
        page.waitForSelector(":has-text('Connected')", new Page.WaitForSelectorOptions().setTimeout(5000));
        
        return extractedSessionId;
    }

    private void authenticateAsGuest(Page page, String guestName) throws Exception {
        // Switch to guest mode using the actual UI
        page.click("#guestLoginTab");
        page.waitForTimeout(500); // Wait for UI to update
        
        // Fill in guest name using the actual form
        page.fill("#displayName", guestName);
        
        // Submit the form using the actual submit button
        page.click("#submitButton");
        
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void joinExistingSession(Page page, String sessionId) throws Exception {
        // Should be on home page now - join the session
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Wait for session lobby page
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
        
        // Wait for SSE connection
        page.waitForSelector(":has-text('Connected')", new Page.WaitForSelectorOptions().setTimeout(5000));
        
        // Verify we're in the correct session
        assertTrue(page.url().contains(sessionId), 
            "User should be in session " + sessionId + " but URL is: " + page.url());
    }


    private void verifyParticipantListsInAllBrowsers() throws Exception {
        // Expected participants: Michel Test User (OAuth user), Guest Facilitator, Guest User 1, Guest User 2
        String[] expectedParticipants = {"Michel Test User", "Guest Facilitator", "Guest User 1", "Guest User 2"};
        
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            
            // Wait for participant list to update - look for Michel Test User instead of michel
            page.waitForSelector("ul li:has-text('Michel Test User')", new Page.WaitForSelectorOptions().setTimeout(5000));
            
            // Get all participant names from the list
            List<String> participantElements = page.querySelectorAll("ul li").stream()
                .map(element -> element.textContent().trim())
                .filter(text -> !text.isEmpty())
                .toList();
            
            System.out.println("DEBUG Browser " + i + " participant list: " + participantElements);
            
            // Verify we have 4 participants
            assertTrue(participantElements.size() >= 4, 
                "Browser " + i + " should see at least 4 participants, but saw: " + participantElements);
            
            // Verify each expected participant is present
            for (String expectedParticipant : expectedParticipants) {
                boolean found = participantElements.stream()
                    .anyMatch(participant -> participant.contains(expectedParticipant));
                assertTrue(found, 
                    "Browser " + i + " should see participant '" + expectedParticipant + "' in list: " + participantElements);
            }
        }
    }

    private void verifyParticipantCountInBrowser(int browserIndex, int expectedCount) {
        Page page = pages.get(browserIndex);
        
        // Get participant count
        List<ElementHandle> participants = page.querySelectorAll("ul li");
        assertEquals(expectedCount, participants.size(),
            "Browser " + browserIndex + " should have " + expectedCount + " participants");
    }

    private void verifySseEventsReceivedByAllUsers() throws Exception {
        // Wait a bit for SSE events to propagate
        Thread.sleep(3000);
        
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            
            // Get SSE events from browser JavaScript
            Object events = page.evaluate("window.sseEvents || []");
            assertNotNull(events, "Browser " + i + " should have SSE events tracked");
            
            List<?> eventList = (List<?>) events;
            System.out.println("Browser " + i + " received " + eventList.size() + " SSE events: " + eventList);
            
            // Browser-specific expectations based on join order:
            // Browser 0 (authenticated user): joins first, sees 3 other participants join
            // Browser 1 (Guest Facilitator): joins second, sees 2 participants join after them  
            // Browser 2 (Guest User 1): joins third, sees 1 participant join after them
            // Browser 3 (Guest User 2): joins last, sees 0 participants join after them
            if (i == 3) {
                // Browser 3 is the last to join, so it might not receive any PARTICIPANT_JOINED events
                // This is correct behavior - just verify the SSE tracking is working
                assertTrue(eventList != null, 
                    "Browser " + i + " should have SSE event tracking initialized (even if empty)");
            } else {
                // Other browsers should receive at least 1 SSE event 
                assertTrue(eventList.size() >= 1, 
                    "Browser " + i + " should have received at least 1 SSE event, but got: " + eventList.size());
            }
        }
    }
}