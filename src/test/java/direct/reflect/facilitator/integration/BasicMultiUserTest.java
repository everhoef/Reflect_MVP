package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import direct.reflect.facilitator.auth.TestAuthConfiguration;

/**
 * Basic multi-user integration test to validate the setup and approach.
 * Tests that multiple browser instances can interact with the application correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.profiles.active=test")
@Testcontainers
@org.springframework.context.annotation.Import(TestAuthConfiguration.class)
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

    private TestRestTemplate restTemplate = new TestRestTemplate();
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
        // Test authenticated user login (OAuth2 mocked via TestAuthController)
        authenticatedUserLogin(page1);
        String page1Content = page1.textContent("body");
        System.out.println("DEBUG: Page 1 content: " + page1Content);
        System.out.println("DEBUG: Page 1 URL: " + page1.url());
        
        // Debug: Check each condition individually
        boolean hasMichelTestUser = page1Content.contains("Michel Test User");
        boolean hasWelcome = page1Content.contains("Welcome");
        boolean hasSessionName = page1Content.contains("sessionName");
        boolean hasCreateRetro = page1Content.contains("Create New Retrospective");
        boolean hasTeamRetro = page1Content.contains("Team Retrospective");
        
        System.out.println("DEBUG: Contains 'Michel Test User': " + hasMichelTestUser);
        System.out.println("DEBUG: Contains 'Welcome': " + hasWelcome);
        System.out.println("DEBUG: Contains 'sessionName': " + hasSessionName);
        System.out.println("DEBUG: Contains 'Create New Retrospective': " + hasCreateRetro);
        System.out.println("DEBUG: Contains 'Team Retrospective': " + hasTeamRetro);
        
        // Check if we got redirected to login (which means authentication failed)
        if (page1.url().contains("/login")) {
            fail("Authentication failed - page was redirected to /login. URL: " + page1.url() + 
                 ". This suggests the TestAuthController OAuth2 setup isn't working.");
        }
        
        // After the authentication flow, check for expected content
        boolean hasExpectedContent = page1Content.contains("Michel Test User") || 
                                   page1Content.contains("Welcome") || 
                                   page1Content.contains("sessionName") ||
                                   page1Content.contains("Create New Retrospective") ||
                                   page1Content.contains("Team Retrospective");
        if (!hasExpectedContent) {
            System.out.println("DEBUG: Page content doesn't contain expected elements:");
            System.out.println("Page URL: " + page1.url());
            System.out.println("Full page content:");
            System.out.println(page1Content);
        }
        assertTrue(hasExpectedContent, "Page should show OAuth user content or home page elements. URL: " + page1.url());

        // Test guest user login (actual guest authentication flow)
        guestUserLogin(page2, "Guest Test User");
        String page2Content = page2.textContent("body");
        
        // Debug guest user content
        System.out.println("DEBUG: Guest user page content: " + page2Content.substring(0, Math.min(1000, page2Content.length())));
        System.out.println("DEBUG: Guest page contains 'Guest Test User': " + page2Content.contains("Guest Test User"));
        System.out.println("DEBUG: Guest page contains 'Welcome': " + page2Content.contains("Welcome"));
        System.out.println("DEBUG: Guest user URL: " + page2.url());
        
        assertTrue(page2Content.contains("Guest Test User") || page2Content.contains("Welcome"), 
                   "Guest user page should contain username or welcome message. URL: " + page2.url());
    }

    private String authenticatedUserCreatesSession(Page page) throws Exception {
        // Use the same authentication approach as the working test
        authenticatedUserLogin(page);
        
        // Should be at home page, wait for the session name input field
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
        // Test the actual guest authentication flow end-to-end
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Submit guest authentication directly to /auth/guest endpoint using the real flow
        page.evaluate(String.format("""
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '%s/auth/guest';
            
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') ||
                              document.querySelector('input[name="_csrf"]')?.value ||
                              'test-csrf-token';
            
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = '_csrf';
            csrfInput.value = csrfToken;
            form.appendChild(csrfInput);
            
            const displayNameInput = document.createElement('input');
            displayNameInput.type = 'hidden';
            displayNameInput.name = 'displayName';
            displayNameInput.value = '%s';
            form.appendChild(displayNameInput);
            
            document.body.appendChild(form);
            form.submit();
        """, baseUrl, guestName));
        
        // Wait for redirect completion - be more flexible about page state
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("DEBUG: Timeout waiting for DOMContentLoaded, checking current state:");
            System.out.println("URL: " + page.url());
            System.out.println("Content preview: " + page.textContent("body").substring(0, Math.min(500, page.textContent("body").length())));
        }

        // Should be redirected to home page, wait for the retroId input field
        // Wait for home page content to load properly
        try {
            page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(10000));
        } catch (Exception e) {
            // Debug if selector not found
            System.out.println("DEBUG: input[name='retroId'] not found, checking page content:");
            System.out.println("URL: " + page.url());
            System.out.println("Content: " + page.textContent("body").substring(0, Math.min(1000, page.textContent("body").length())));
            throw e;
        }

        // Join session
        page.fill("input[name='retroId']", sessionId);
        page.click("button:has-text('Join Session')");
        
        // Wait for navigation to session page
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("DEBUG: Timeout waiting for session page load:");
            System.out.println("URL: " + page.url());
            System.out.println("Content preview: " + page.textContent("body").substring(0, Math.min(500, page.textContent("body").length())));
            // Don't throw - continue with the test
        }

        // Wait for session lobby
        page.waitForSelector("h2:has-text('Session Lobby')", new Page.WaitForSelectorOptions().setTimeout(10000));
    }
    
    /**
     * Sets up OAuth2 authentication using TestAuthController (bypasses external OAuth2 provider)
     */
    private String setupOAuthUser(String username, String displayName, String email) throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("username", username);
        params.add("displayName", displayName);
        params.add("email", email);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/test/login-oauth-user",
            new HttpEntity<>(params),
            String.class
        );
        
        String sessionCookie = response.getHeaders().getFirst("Set-Cookie");
        assertNotNull(sessionCookie, "Should receive session cookie from OAuth2 test endpoint");
        
        System.out.println("DEBUG: Received Set-Cookie header: " + sessionCookie);
        
        // Extract JSESSIONID value from cookie header (format: "JSESSIONID=value; Path=/; HttpOnly")
        String sessionId;
        if (sessionCookie.contains("JSESSIONID=")) {
            sessionId = sessionCookie.split("JSESSIONID=")[1].split(";")[0];
        } else {
            // Fallback: cookie might not have JSESSIONID prefix in test environment
            sessionId = sessionCookie.split(";")[0];
        }
        
        System.out.println("DEBUG: Extracted session ID: " + sessionId);
        return sessionId;
    }

    private void authenticatedUserLogin(Page page) throws Exception {
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
        
        // First check if TestAuthController endpoint is available
        APIResponse testResponse = page.request().get(baseUrl + "/test/login-oauth-user");
        System.out.println("DEBUG: TestAuthController GET test - Status: " + testResponse.status() + ", Body: " + testResponse.text());
        
        // Use Playwright to make a POST request directly to TestAuthController with CSRF token
        // This ensures session creation happens in the same browser context
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

    private void guestUserLogin(Page page, String guestName) throws Exception {
        // Navigate to login page
        page.navigate(baseUrl + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Submit guest authentication directly to /auth/guest endpoint with CSRF token
        page.evaluate(String.format("""
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '%s/auth/guest';
            
            // Add CSRF token
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || 
                              document.querySelector('input[name="_csrf"]')?.value ||
                              'test-csrf-token';
            
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = '_csrf';
            csrfInput.value = csrfToken;
            form.appendChild(csrfInput);
            
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'displayName';
            input.value = '%s';
            form.appendChild(input);
            
            document.body.appendChild(form);
            form.submit();
        """, baseUrl, guestName));
        
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Wait for home page content to load properly - similar to OAuth fix
        // Try multiple selectors to wait for home page content
        try {
            // First try waiting for the join session input (guest users see this)
            page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(5000));
        } catch (Exception e1) {
            try {
                // Fallback: wait for welcome message
                page.waitForSelector(":has-text('Welcome')", new Page.WaitForSelectorOptions().setTimeout(3000));
            } catch (Exception e2) {
                // Last fallback: wait for any substantial content to load
                page.waitForSelector("main, .container, .content", new Page.WaitForSelectorOptions().setTimeout(3000));
            }
        }
        
        // Additional short wait to ensure template rendering is complete
        Thread.sleep(500);
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

        // Debug what content we actually see
        System.out.println("DEBUG: Page 1 content: " + page1Content.substring(0, Math.min(1000, page1Content.length())));
        System.out.println("DEBUG: Page 2 content: " + page2Content.substring(0, Math.min(1000, page2Content.length())));

        // Check for OAuth user (Michel Test User is the display name from TestAuthController)
        assertTrue(page1Content.contains("Michel Test User"), "Page 1 should show Michel Test User");
        assertTrue(page1Content.contains("Test Guest User"), "Page 1 should show guest user");
        assertTrue(page2Content.contains("Michel Test User"), "Page 2 should show Michel Test User");
        assertTrue(page2Content.contains("Test Guest User"), "Page 2 should show guest user");
    }
}