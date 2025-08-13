package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone test that runs against an already running application on localhost:8080.
 * This test doesn't use Testcontainers or Spring Boot test context.
 */
class StandaloneMultiUserTest {

    private static final String BASE_URL = "http://localhost:8080";
    private Playwright playwright;
    private Browser browser1, browser2;
    private BrowserContext context1, context2;
    private Page page1, page2;

    @BeforeEach
    void setup() {
        playwright = Playwright.create();
        
        // Create two separate browser instances
        browser1 = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        browser2 = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        
        context1 = browser1.newContext();
        context2 = browser2.newContext();
        
        page1 = context1.newPage();
        page2 = context2.newPage();
    }

    @AfterEach
    void cleanup() {
        if (page1 != null) page1.close();
        if (page2 != null) page2.close();
        if (context1 != null) context1.close();
        if (context2 != null) context2.close();
        if (browser1 != null) browser1.close();
        if (browser2 != null) browser2.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void shouldAllowTwoUsersToJoinSameSession() throws Exception {
        // User 1: Authenticated user creates session
        String sessionId = authenticatedUserCreatesSession(page1);
        assertNotNull(sessionId);
        assertTrue(sessionId.matches("[0-9a-f-]{36}"), "Session ID should be valid UUID");

        // User 2: Guest user joins the session
        guestUserJoinsSession(page2, "Test Guest User", sessionId);

        // Verify both users are in the session
        verifyBothUsersInSession(page1, page2);
    }

    private String authenticatedUserCreatesSession(Page page) throws Exception {
        System.out.println("=== Authenticated User Creating Session ===");
        
        // Navigate to login
        page.navigate(BASE_URL + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Navigated to login page: " + page.url());

        // Click User Login tab (should be selected by default)
        page.click("#userLoginTab");
        page.waitForTimeout(1000);
        System.out.println("Clicked User Login tab");

        // Enter credentials
        page.fill("#username", "michel");
        page.fill("#password", "t");
        System.out.println("Filled credentials");

        // Click the Login button
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Clicked submit, redirected to: " + page.url());

        // Wait for home page with session creation form
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(5000));
        System.out.println("Found sessionName input field");

        // Create session
        page.fill("input[name='sessionName']", "Test Session");
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Created session, now at: " + page.url());

        // Wait for session lobby
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
        System.out.println("Reached session lobby");

        // Extract session ID from URL
        String url = page.url();
        String[] parts = url.split("/");
        String sessionId = parts[parts.length - 1];
        System.out.println("Extracted session ID: " + sessionId);
        
        return sessionId;
    }

    private void guestUserJoinsSession(Page page, String guestName, String sessionId) throws Exception {
        System.out.println("=== Guest User Joining Session ===");
        
        // Navigate to login
        page.navigate(BASE_URL + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Guest navigated to login page: " + page.url());

        // Click Guest Access tab
        page.click("#guestLoginTab");
        page.waitForTimeout(1000);
        System.out.println("Clicked Guest Access tab");

        // Enter guest name
        page.fill("#displayName", guestName);
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Guest logged in, redirected to: " + page.url());

        // Wait for home page with join session form
        page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(5000));
        System.out.println("Found retroId input field");

        // Join session
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        System.out.println("Joined session, now at: " + page.url());

        // Wait for session lobby
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
        System.out.println("Guest reached session lobby");
    }

    private void verifyBothUsersInSession(Page page1, Page page2) throws Exception {
        System.out.println("=== Verifying Both Users In Session ===");
        
        // Wait for participant lists to update
        Thread.sleep(3000);

        // Both pages should show participants
        page1.waitForSelector("ul li", new Page.WaitForSelectorOptions().setTimeout(5000));
        page2.waitForSelector("ul li", new Page.WaitForSelectorOptions().setTimeout(5000));

        // Get participant counts
        int participants1 = page1.querySelectorAll("ul li").size();
        int participants2 = page2.querySelectorAll("ul li").size();
        System.out.println("Page 1 participants count: " + participants1);
        System.out.println("Page 2 participants count: " + participants2);

        // Both should see 2 participants
        assertTrue(participants1 >= 2, "Page 1 should see at least 2 participants, saw: " + participants1);
        assertTrue(participants2 >= 2, "Page 2 should see at least 2 participants, saw: " + participants2);

        // Verify specific users are present
        String page1Content = page1.textContent("body");
        String page2Content = page2.textContent("body");

        assertTrue(page1Content.contains("michel"), "Page 1 should show michel");
        assertTrue(page1Content.contains("Test Guest User"), "Page 1 should show guest user");
        assertTrue(page2Content.contains("michel"), "Page 2 should show michel");
        assertTrue(page2Content.contains("Test Guest User"), "Page 2 should show guest user");
        
        System.out.println("✅ Both users successfully verified in session!");
    }
}