package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive multi-user test that validates session switching functionality
 * with 4 users (1 authenticated + 3 guests) testing the scenarios from the original request.
 */
class ComprehensiveMultiUserTest {

    private static final String BASE_URL = "http://localhost:8080";
    private Playwright playwright;
    private List<Browser> browsers = new ArrayList<>();
    private List<BrowserContext> contexts = new ArrayList<>();
    private List<Page> pages = new ArrayList<>();

    @BeforeEach
    void setup() {
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
        String session1Id = authenticatedUserCreatesSession(pages.get(0), "First Session");
        System.out.println("Session 1 created: " + session1Id);

        // Step 2: Three guests join the first session
        guestUserJoinsSession(pages.get(1), "Guest Facilitator", session1Id);
        guestUserJoinsSession(pages.get(2), "Guest User 1", session1Id);
        guestUserJoinsSession(pages.get(3), "Guest User 2", session1Id);

        // Step 3: Verify all 4 users are in session 1
        System.out.println("=== Verifying all 4 users in Session 1 ===");
        verifyParticipantCount(pages.get(0), 4, "Authenticated user");
        verifyParticipantCount(pages.get(1), 4, "Guest Facilitator");
        verifyParticipantCount(pages.get(2), 4, "Guest User 1");
        verifyParticipantCount(pages.get(3), 4, "Guest User 2");

        // Step 4: Guest User 2 creates a new session (switches from session 1)
        System.out.println("=== Guest User 2 creating new session (switching) ===");
        String session2Id = guestUserCreatesNewSession(pages.get(3), "Second Session by Guest");
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
        guestUserJoinsSession(pages.get(2), "Guest User 1", session2Id);
        
        // Step 7: Final verification
        Thread.sleep(3000);
        verifyParticipantCount(pages.get(0), 2, "Session 1 - final count");
        verifyParticipantCount(pages.get(1), 2, "Session 1 - Guest Facilitator final");  
        verifyParticipantCount(pages.get(2), 2, "Session 2 - Guest User 1");
        verifyParticipantCount(pages.get(3), 2, "Session 2 - Guest User 2 final");

        System.out.println("✅ Multi-user session switching test completed successfully!");
    }

    private String authenticatedUserCreatesSession(Page page, String sessionName) throws Exception {
        // Navigate and login
        page.navigate(BASE_URL + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.click("#userLoginTab");
        page.waitForTimeout(1000);
        page.fill("#username", "michel");
        page.fill("#password", "t");
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Create session
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(5000));
        page.fill("input[name='sessionName']", sessionName);
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Extract session ID
        String url = page.url();
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    private void guestUserJoinsSession(Page page, String guestName, String sessionId) throws Exception {
        // Navigate and login as guest
        page.navigate(BASE_URL + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.click("#guestLoginTab");
        page.waitForTimeout(1000);
        page.fill("#displayName", guestName);
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Join session
        page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(5000));
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    private String guestUserCreatesNewSession(Page page, String sessionName) throws Exception {
        // Navigate home to create new session (should trigger session switching)
        page.navigate(BASE_URL);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Fill session name and create
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(5000));
        page.fill("input[name='sessionName']", sessionName);
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Extract new session ID
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