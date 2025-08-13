package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic multi-user integration test to validate the setup and approach.
 * Tests that multiple browser instances can interact with the application correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BasicMultiUserTest {

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
    private Browser browser1, browser2;
    private BrowserContext context1, context2;
    private Page page1, page2;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;
        playwright = Playwright.create();
        
        // Create two separate browser instances
        browser1 = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        browser2 = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        
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

    @Test
    void shouldHandleBothUserTypesLogin() throws Exception {
        // Test authenticated user login
        authenticatedUserLogin(page1);
        String page1Content = page1.textContent("body");
        assertTrue(page1Content.contains("michel") || page1Content.contains("Welcome"));

        // Test guest user login
        guestUserLogin(page2, "Guest Test User");
        String page2Content = page2.textContent("body");
        assertTrue(page2Content.contains("Guest Test User") || page2Content.contains("Welcome"));
    }

    private String authenticatedUserCreatesSession(Page page) throws Exception {
        // Navigate to login and authenticate
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Click User Login tab (it's already selected by default, but let's be explicit)
        page.click("#userLoginTab");
        page.waitForTimeout(500);

        // Enter credentials (michel / t from SecurityConfig)
        page.fill("#username", "michel");
        page.fill("#password", "t");

        // Click the Login button (submitButton)
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Should be redirected to home page, wait for the session name input field
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Create session
        page.fill("input[name='sessionName']", "Basic Test Session");
        page.click("button:has-text('Create Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Wait for session lobby
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Extract session ID from URL
        String url = page.url();
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    private void guestUserJoinsSession(Page page, String guestName, String sessionId) throws Exception {
        // Navigate to login
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Click Guest Access tab
        page.click("#guestLoginTab");
        page.waitForTimeout(500);

        // Enter guest name
        page.fill("#displayName", guestName);
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Should be redirected to home page, wait for the retroId input field
        page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Join session
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Wait for session lobby
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    private void authenticatedUserLogin(Page page) {
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.click("#userLoginTab");
        page.waitForTimeout(500);

        page.fill("#username", "michel");
        page.fill("#password", "t");
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void guestUserLogin(Page page, String guestName) {
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.click("#guestLoginTab");
        page.waitForTimeout(500);

        page.fill("#displayName", guestName);
        page.click("#submitButton");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void verifyBothUsersInSession(Page page1, Page page2) throws Exception {
        // Wait a bit for participant lists to update
        Thread.sleep(2000);

        // Both pages should show participants
        page1.waitForSelector("ul li", new Page.WaitForSelectorOptions().setTimeout(5000));
        page2.waitForSelector("ul li", new Page.WaitForSelectorOptions().setTimeout(5000));

        // Get participant counts
        int participants1 = page1.querySelectorAll("ul li").size();
        int participants2 = page2.querySelectorAll("ul li").size();

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
    }
}