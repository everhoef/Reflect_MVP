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

import static org.junit.jupiter.api.Assertions.*;

import direct.reflect.facilitator.auth.TestAuthConfiguration;

/**
 * Comprehensive multi-user test that validates session switching functionality
 * with 4 users (1 authenticated + 3 guests) testing the scenarios from the original request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = "spring.profiles.active=test")
@Testcontainers
@org.springframework.context.annotation.Import(TestAuthConfiguration.class)
class ComprehensiveMultiUserTest {

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

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;
        playwright = Playwright.create();
        
        // Create 4 separate browser instances for different user types
        for (int i = 0; i < 4; i++) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            
            browsers.add(browser);
            contexts.add(context);
            pages.add(page);
        }
    }

    @AfterEach
    void cleanup() {
        pages.forEach(Page::close);
        contexts.forEach(BrowserContext::close);
        browsers.forEach(Browser::close);
        if (playwright != null) playwright.close();
        
        pages.clear();
        contexts.clear();
        browsers.clear();
    }

    @Test
    void shouldSupportMultiUserSessionWithSessionSwitching() throws Exception {
        // Step 1: Authenticated user creates first session
        authenticateAsUser(pages.get(0));
        String session1Id = createNewSession(pages.get(0), "First Session");
        System.out.println("Session 1 created: " + session1Id);

        // Step 2: Three guests join the first session
        authenticateAsGuest(pages.get(1), "Guest Facilitator");
        joinExistingSession(pages.get(1), session1Id);
        
        authenticateAsGuest(pages.get(2), "Guest User 1");
        joinExistingSession(pages.get(2), session1Id);
        
        authenticateAsGuest(pages.get(3), "Guest User 2");
        joinExistingSession(pages.get(3), session1Id);

        // Step 3: Verify all 4 users are in session 1
        System.out.println("=== Verifying all 4 users in Session 1 ===");
        verifyParticipantCount(pages.get(0), 4, "Authenticated user");
        verifyParticipantCount(pages.get(1), 4, "Guest Facilitator");
        verifyParticipantCount(pages.get(2), 4, "Guest User 1");
        verifyParticipantCount(pages.get(3), 4, "Guest User 2");

        // Step 4: Guest User 2 creates a new session (switches from session 1)
        System.out.println("=== Guest User 2 creating new session (switching) ===");
        pages.get(3).navigate(baseUrl);
        pages.get(3).waitForLoadState(LoadState.NETWORKIDLE);
        String session2Id = createNewSession(pages.get(3), "Second Session by Guest");
        System.out.println("Session 2 created: " + session2Id);
        assertNotEquals(session1Id, session2Id);

        // Step 5: Wait for session updates and verify counts
        Thread.sleep(3000); // Allow time for SSE events to propagate
        
        verifyParticipantCount(pages.get(0), 3, "Session 1 - should have 3 participants now");
        verifyParticipantCount(pages.get(1), 3, "Session 1 - Guest Facilitator");
        verifyParticipantCount(pages.get(2), 3, "Session 1 - Guest User 1");
        verifyParticipantCount(pages.get(3), 1, "Session 2 - should have 1 participant");

        // Step 6: Guest User 1 switches to session 2
        System.out.println("=== Guest User 1 switching to Session 2 ===");
        
        // Before switching - verify current state
        System.out.println("BEFORE SWITCH - Session 1 participant counts:");
        for (int i = 0; i < 2; i++) { // Only check Session 1 browsers (0 and 1)
            List<ElementHandle> participants = pages.get(i).querySelectorAll("ul li");
            System.out.println("Browser " + i + " (Session 1): " + participants.size() + " participants");
        }
        
        // Keep existing authentication, just navigate home and join new session
        pages.get(2).navigate(baseUrl);
        pages.get(2).waitForLoadState(LoadState.NETWORKIDLE);
        joinExistingSession(pages.get(2), session2Id);
        
        // Right after switching - check if the removal happened immediately
        Thread.sleep(2000);
        System.out.println("IMMEDIATELY AFTER SWITCH - Session 1 participant counts:");
        for (int i = 0; i < 2; i++) { // Only check Session 1 browsers (0 and 1)
            List<ElementHandle> participants = pages.get(i).querySelectorAll("ul li");
            System.out.println("Browser " + i + " (Session 1): " + participants.size() + " participants");
        }
        
        // Step 7: Final verification - wait even longer for session switching and SSE events to complete
        Thread.sleep(8000);
        
        // Debug: print actual participant counts for troubleshooting
        for (int i = 0; i < 4; i++) {
            List<ElementHandle> participants = pages.get(i).querySelectorAll("ul li");
            System.out.println("DEBUG: Browser " + i + " sees " + participants.size() + " participants");
        }
        
        verifyParticipantCount(pages.get(0), 2, "Session 1 - final count");
        verifyParticipantCount(pages.get(1), 2, "Session 1 - Guest Facilitator final");  
        verifyParticipantCount(pages.get(2), 2, "Session 2 - Guest User 1");
        verifyParticipantCount(pages.get(3), 2, "Session 2 - Guest User 2 final");

        System.out.println("✅ Multi-user session switching test completed successfully!");
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

    private void authenticateAsGuest(Page page, String guestName) throws Exception {
        // Navigate to login page
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Switch to guest mode using the actual UI
        page.click("#guestLoginTab");
        page.waitForTimeout(500); // Wait for UI to update
        
        // Fill in guest name using the actual form
        page.fill("#displayName", guestName);
        
        // Submit the form using the actual submit button
        page.click("#submitButton");
        
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Debug: Check what page we're actually on
        System.out.println("DEBUG: After guest auth, current URL: " + page.url());
        System.out.println("DEBUG: Page title: " + page.title());
    }

    private void joinExistingSession(Page page, String sessionId) throws Exception {
        // Wait for session join form
        try {
            page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("DEBUG: Failed to find retroId input. Current URL: " + page.url());
            System.out.println("DEBUG: Page content preview: " + page.textContent("body").substring(0, Math.min(500, page.textContent("body").length())));
            throw e;
        }
        
        // Join the session
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
        
        // Verify we're in the correct session
        String currentUrl = page.url();
        assertTrue(currentUrl.contains(sessionId), 
            "User should be in session " + sessionId + " but URL is: " + currentUrl);
    }

    private String createNewSession(Page page, String sessionName) throws Exception {
        // Create session - wait for the form to be available
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(5000));
        page.fill("input[name='sessionName']", sessionName);
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Extract session ID from URL
        String url = page.url();
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    private void verifyParticipantCount(Page page, int expectedCount, String userDescription) throws Exception {
        try {
            // Wait for participant list to load
            page.waitForSelector("ul li", new Page.WaitForSelectorOptions().setTimeout(5000));
            
            // Get actual count
            List<ElementHandle> participants = page.querySelectorAll("ul li");
            int actualCount = participants.size();
            
            System.out.println(userDescription + " sees " + actualCount + " participants (expected: " + expectedCount + ")");
            
            assertEquals(expectedCount, actualCount, 
                userDescription + " should see " + expectedCount + " participants, but saw " + actualCount);
                
        } catch (Exception e) {
            System.err.println("Error verifying participant count for " + userDescription + ": " + e.getMessage());
            // Take a screenshot for debugging if needed
            String pageContent = page.textContent("body");
            System.err.println("Page content: " + (pageContent.length() > 500 ? pageContent.substring(0, 500) + "..." : pageContent));
            throw e;
        }
    }
}