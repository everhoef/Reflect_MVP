package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.FormData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;

import direct.reflect.facilitator.configurator.RetroTemplateRepository;
import direct.reflect.facilitator.configurator.RetroStageRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.facilitation.RetroSessionService;

import java.util.List;

/**
 * Base class for integration tests with common setup for Playwright, TestContainers, and authentication.
 * All browser-based integration tests should extend this class.
 * 
 * Note: Each browser context maintains its own session, so multiple users can be authenticated
 * simultaneously using Spring Security's session-based authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.profiles.active=test,import")
@Testcontainers
@org.springframework.context.annotation.Import({
    direct.reflect.facilitator.auth.TestAuthConfiguration.class, // Provides TestAuthController with /test/* endpoints
    direct.reflect.facilitator.config.TestSecurityOverride.class // Extends SecurityConfig to allow /test/* endpoints
})
@Slf4j
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("facilitator_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    protected Playwright playwright;
    protected Browser browser;
    protected String baseUrl;

    @Autowired
    protected RetroTemplateRepository templateRepository;

    @Autowired
    protected RetroStageRepository stageRepository;

    @Autowired
    protected RetroSessionService sessionService;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        playwright = Playwright.create();
        
        // Enable visual debugging: set PLAYWRIGHT_DEBUG=true for headful mode + slow motion
        boolean debugMode = Boolean.parseBoolean(System.getenv("PLAYWRIGHT_DEBUG"));
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            .setHeadless(!debugMode);
        
        if (debugMode) {
            launchOptions.setSlowMo(1000); // 1 second delay between actions
            log.info("🐛 PLAYWRIGHT DEBUG MODE ENABLED");
            log.info("   - Headful browser (you can see what's happening)");  
            log.info("   - 1 second delay between actions");
            log.info("   - Screenshots will be saved on failures");
        }
        
        browser = playwright.chromium().launch(launchOptions);
    }

    @AfterEach
    void tearDown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    // ==================== AUTHENTICATION HELPERS ====================

    /**
     * Authenticates a user as a guest using the UI authentication flow.
     * Each browser context maintains its own session cookies, allowing multiple
     * simultaneous authenticated users.
     */
    protected void authenticateAsGuest(Page page, String displayName) {
        try {
            // Navigate to login page
            page.navigate(baseUrl + "/login");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            
            // Submit guest authentication using the real UI flow to /auth/guest
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
            """, baseUrl, displayName));
            
            // Wait for redirect to home page (/) after guest authentication
            try {
                page.waitForURL(url -> url.equals(baseUrl + "/") || url.contains("/home"), 
                    new Page.WaitForURLOptions().setTimeout(15000));
                log.info("✅ Guest authentication successful, redirected to: {}", page.url());
            } catch (Exception e) {
                log.error("❌ Guest authentication redirect failed for user '{}'. Final URL: {}", displayName, page.url());
                throw new RuntimeException("Timeout waiting for guest authentication redirect for user '" + displayName + "'. URL: " + page.url());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to authenticate guest user '" + displayName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates a user as OAuth2 user using the test authentication endpoint.
     * Uses a simpler approach that navigates to a test page first to establish session context.
     */
    protected void authenticateAsOAuth2User(Page page, String username, String displayName, String email) {
        try {
            log.debug("Starting OAuth2 authentication for user: {}", username);
            
            // Create GET URL for the test auth endpoint (now supports GET)
            String authUrl = String.format("%s/test/login-oauth-user?username=%s&displayName=%s&email=%s", 
                baseUrl, 
                java.net.URLEncoder.encode(username, "UTF-8"),
                java.net.URLEncoder.encode(displayName, "UTF-8"), 
                java.net.URLEncoder.encode(email, "UTF-8"));
                
            log.debug("Navigating to OAuth2 auth URL: {}", authUrl);
            
            // Navigate directly to the auth endpoint - it will redirect to home page
            page.navigate(authUrl);
            
            // Wait for authentication and redirect to complete
            try {
                // The endpoint should redirect to home page ("/") after authentication
                page.waitForURL(url -> url.equals(baseUrl + "/") || url.contains("/home"), 
                    new Page.WaitForURLOptions().setTimeout(10000));
                
                log.debug("OAuth2 authentication completed, current URL: {}", page.url());
                
            } catch (Exception e) {
                log.warn("OAuth2 authentication redirect timeout for user '{}': {}", username, e.getMessage());
                log.debug("Current URL after timeout: {}", page.url());
                
                // If redirect didn't work, try manual navigation
                if (!page.url().contains(baseUrl + "/") && !page.url().contains("/login")) {
                    log.debug("Manually navigating to home page to complete authentication");
                    page.navigate(baseUrl + "/");
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                }
            }
            
            // Verify we reached an authenticated page (should not be login page)
            String finalUrl = page.url();
            if (finalUrl.contains("/login")) {
                log.error("❌ OAuth2 authentication failed - redirected back to login page");
                debugPageState(page, "OAuth2 authentication failure");
                debugScreenshot(page, "oauth2_auth_failed", "Expected authenticated page but got login page");
                throw new RuntimeException("OAuth2 authentication failed - user was redirected to login page");
            } else {
                log.info("✅ OAuth2 authentication completed successfully, final URL: {}", finalUrl);
                debugPageState(page, "OAuth2 authentication success");
            }
            
        } catch (Exception e) {
            log.error("❌ OAuth2 authentication error for user '{}': {}", username, e.getMessage());
            throw new RuntimeException("Failed to authenticate OAuth2 user '" + username + "': " + e.getMessage(), e);
        }
    }

    // ==================== SESSION MANAGEMENT HELPERS ====================

    /**
     * Creates a new retrospective session and returns the session ID
     */
    protected String createRetroSession(Page facilitatorPage, String sessionName) {
        log.debug("Starting session creation for: {}", sessionName);
        
        // Navigate to home page first (should be authenticated already)
        facilitatorPage.navigate(baseUrl + "/");
        
        // Wait for session creation form
        facilitatorPage.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(10000));
        log.debug("Session creation form found");
        
        // Set up response monitoring to capture the HX-Redirect header
        final String[] redirectUrl = {null};
        
        facilitatorPage.onResponse(response -> {
            if (response.url().contains("/api/retro/create")) {
                log.debug("Session create response: status={}, headers={}", response.status(), response.headers());
                String hxRedirect = response.headers().get("hx-redirect");
                if (hxRedirect == null) {
                    hxRedirect = response.headers().get("HX-Redirect");
                }
                if (hxRedirect != null) {
                    redirectUrl[0] = hxRedirect;
                    log.debug("Found HX-Redirect header: {}", hxRedirect);
                } else {
                    log.warn("No HX-Redirect header found in session creation response");
                }
            }
        });
        
        // Create session (no template selection needed - API uses default template)
        facilitatorPage.fill("input[name='sessionName']", sessionName);
        facilitatorPage.click("button:has-text('Create Session')");
        
        // Wait for HTMX redirect to retro page (instead of network idle due to SSE)
        facilitatorPage.waitForURL(url -> url.contains("/retro/"), new Page.WaitForURLOptions().setTimeout(10000));
        
        // Wait for essential page elements to load instead of fixed delay
        facilitatorPage.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(2000));

        String sessionUrl = facilitatorPage.url();
        log.debug("Facilitator URL after session creation: {}", sessionUrl);
        
        // Use the redirect URL from the response to extract session ID
        if (redirectUrl[0] != null && !redirectUrl[0].isEmpty()) {
            String extractedId = redirectUrl[0].substring(redirectUrl[0].lastIndexOf("/") + 1);
            log.debug("Extracted session ID from HX-Redirect header: {}", extractedId);
            return extractedId;
        } else {
            // Fallback to URL parsing
            String extractedId = sessionUrl.substring(sessionUrl.lastIndexOf("/") + 1);
            log.debug("Extracted session ID from current URL: {}", extractedId);
            return extractedId;
        }
    }

    /**
     * Joins an existing retrospective session
     */
    protected void joinRetroSession(Page participantPage, String sessionId) {
        try {
            log.debug("Starting join session process - session ID: {}, current URL: {}", sessionId, participantPage.url());
            
            // Navigate to home page first (should be authenticated already)
            participantPage.navigate(baseUrl + "/");
            
            // Wait for join session form
            log.info("Waiting for join session form input[name='retroId']...");
            try {
                participantPage.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(10000));
                log.info("✅ Join session form found");
            } catch (Exception e) {
                log.error("❌ Failed to find join session form after 10 seconds");
                log.error("Current URL: {}", participantPage.url());
                log.error("Page title: {}", participantPage.title());
                
                // Debug: Check what's actually on the page
                String bodyText = participantPage.textContent("body");
                log.error("Page content (first 1000 chars): {}", 
                    bodyText.length() > 1000 ? bodyText.substring(0, 1000) + "..." : bodyText);
                
                // Check if there are any forms on the page
                int formCount = participantPage.locator("form").count();
                log.error("Number of forms found: {}", formCount);
                
                // Check for specific elements that should be on home page
                boolean hasSessionNameInput = participantPage.locator("input[name='sessionName']").count() > 0;
                boolean hasRetroIdInput = participantPage.locator("input[name='retroId']").count() > 0;
                log.error("Has sessionName input: {}, Has retroId input: {}", hasSessionNameInput, hasRetroIdInput);
                
                throw new RuntimeException("Join session form not found: " + e.getMessage(), e);
            }
            
            // Check if page has any existing error parameters
            if (participantPage.url().contains("error=")) {
                log.warn("⚠️ Page already has error parameter: {}", participantPage.url());
            }
            
            // Only log page content before form fill if there are issues finding the form
            // (removed to reduce noise)
            
            log.info("Filling session ID into form: {}", sessionId);
            participantPage.fill("input[name='retroId']", sessionId);
            
            // Verify the form was filled
            String filledValue = participantPage.inputValue("input[name='retroId']");
            log.info("Form filled with value: {}", filledValue);
            if (!sessionId.equals(filledValue)) {
                log.error("❌ Form fill failed! Expected: {}, Actual: {}", sessionId, filledValue);
            }
            
            // Set up response monitoring to track HTMX redirect headers
            final String[] redirectUrl = {null};
            final boolean[] requestSent = {false};
            final boolean[] responsReceived = {false};
            
            // Monitor JOIN requests - minimal logging
            participantPage.onRequest(request -> {
                if (request.url().contains("/api/retro/join")) {
                    requestSent[0] = true;
                    log.info("📤 JOIN REQUEST: {} {}", request.method(), request.url());
                }
            });
            
            participantPage.onResponse(response -> {
                if (response.url().contains("/api/retro/join")) {
                    responsReceived[0] = true;
                    
                    // Check for HX-Redirect header (case insensitive)
                    String hxRedirect = response.headers().get("hx-redirect");
                    if (hxRedirect == null) {
                        hxRedirect = response.headers().get("HX-Redirect");
                    }
                    if (hxRedirect != null) {
                        log.info("📥 JOIN SUCCESS → {}", hxRedirect);
                        redirectUrl[0] = hxRedirect;
                    } else {
                        log.warn("📥 JOIN RESPONSE ({}): No redirect header", response.status());
                    }
                    
                    try {
                        String responseText = response.text();
                        if (response.status() == 200 && redirectUrl[0] == null && !responseText.contains("error")) {
                            redirectUrl[0] = baseUrl + "/retro/" + sessionId;
                        }
                    } catch (Exception e) {
                        // Ignore - expected for redirect responses
                    }
                }
            });
            
            log.info("🖱️ Clicking 'Join Session' button...");
            participantPage.click("button:has-text('Join Session')");
            
            // Wait for HTMX to process and potentially redirect
            // Instead of fixed 3s delay, wait for URL change or specific response elements
            try {
                participantPage.waitForURL(url -> url.contains("/retro/") || url.contains("error="), 
                    new Page.WaitForURLOptions().setTimeout(5000));
            } catch (Exception e) {
                // If URL doesn't change quickly, allow time for HTMX response
                participantPage.waitForTimeout(500);
            }
            
            String finalUrl = participantPage.url(); 
            if (finalUrl.contains("/retro/")) {
                log.info("✅ Join successful → {}", finalUrl);
            } else {
                log.warn("❌ Join failed, URL: {}", finalUrl);
                if (!requestSent[0] || !responsReceived[0]) {
                    log.warn("📊 Request sent: {}, Response received: {}", requestSent[0], responsReceived[0]);
                }
            }
            
            if (!requestSent[0]) {
                log.error("❌ NO JOIN REQUEST WAS SENT! This suggests HTMX form submission failed");
                // Debug: Check if HTMX is loaded
                Object htmxLoaded = participantPage.evaluate("typeof htmx !== 'undefined'");
                log.error("HTMX loaded: {}", htmxLoaded);
                
                // Check form attributes
                String formHtml = participantPage.innerHTML("form");
                log.error("Form HTML: {}", formHtml);
            }
            
            if (!responsReceived[0] && requestSent[0]) {
                log.error("❌ JOIN REQUEST SENT BUT NO RESPONSE RECEIVED");
            }
            
            // If we have a redirect URL but we're still on home page, navigate manually
            if (redirectUrl[0] != null && !participantPage.url().contains("/retro/")) {
                log.info("🔄 HTMX redirect didn't occur, navigating manually to: {}", redirectUrl[0]);
                participantPage.navigate(redirectUrl[0]);
                participantPage.waitForLoadState(LoadState.NETWORKIDLE);
                log.info("✅ Manual navigation completed, URL is now: {}", participantPage.url());
            }
            
            // Check for error parameters in URL and throw exception if found
            if (participantPage.url().contains("error=")) {
                String url = participantPage.url();
                String errorParam = url.substring(url.indexOf("error=") + 6);
                if (errorParam.contains("&")) {
                    errorParam = errorParam.substring(0, errorParam.indexOf("&"));
                }
                log.error("❌ Found error parameter in URL: {}", errorParam);
                debugPageState(participantPage, "Session join error");
                debugScreenshot(participantPage, "session_join_error", "Error parameter: " + errorParam);
                throw new RuntimeException("Failed to join session '" + sessionId + "': " + errorParam);
            }
            
            // Check for error indicators in page content
            boolean hasErrorContent = participantPage.locator(".bg-red-100").count() > 0 ||
                                    participantPage.locator("[class*='error']").count() > 0;
            if (hasErrorContent) {
                String errorText = participantPage.locator(".bg-red-100").count() > 0 ?
                                 participantPage.locator(".bg-red-100").textContent() :
                                 "Error detected on page";
                log.error("❌ Found error content on page: {}", errorText);
                throw new RuntimeException("Failed to join session '" + sessionId + "': " + errorText);
            }
            
            // Only log page content if there was an error
            if (!participantPage.url().contains("/retro/")) {
                String pageContentAfter = participantPage.textContent("body");
                log.warn("❌ Join failed - page content (first 300 chars): {}", 
                    pageContentAfter.substring(0, Math.min(300, pageContentAfter.length())));
            }
                
            log.info("=== END JOIN SESSION PROCESS ===");
            
        } catch (Exception e) {
            log.error("❌ Exception during session join: {}", e.getMessage());
            log.error("Page URL at time of error: {}", participantPage.url());
            throw e;
        }
    }

    /**
     * Starts the retrospective session (facilitator only)
     */
    protected void startRetroSession(Page facilitatorPage) {
        log.debug("Starting retro session...");
        
        // Set up response monitoring to detect session start completion
        final boolean[] sessionStarted = {false};
        
        facilitatorPage.onResponse(response -> {
            if (response.url().contains("/start") && response.status() == 302) {
                sessionStarted[0] = true;
                log.debug("Session start response received: {}", response.status());
            }
        });
        
        facilitatorPage.click("button:has-text('Start Retrospective')");
        
        // Wait for HTMX redirect to complete and page to reload with active session state
        try {
            // First wait for the network request to complete
            facilitatorPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
            
            // Then wait for SSE connection to be established (which happens in active phases)
            facilitatorPage.waitForFunction("() => {" +
                "const eventSource = window.eventSource;" +
                "return eventSource && eventSource.readyState === 1;" + // OPEN state
            "}", new Page.WaitForFunctionOptions().setTimeout(5000));
            
            log.debug("Session started with SSE connection established");
        } catch (Exception e) {
            log.warn("SSE connection not established immediately after session start, using fallback wait: {}", e.getMessage());
            // Fallback: wait for session start indicators and give time for SSE
            try {
                facilitatorPage.waitForSelector(".retro-step, .step-content, .participants-list", 
                    new Page.WaitForSelectorOptions().setTimeout(3000));
            } catch (Exception selectorException) {
                // Final fallback
                facilitatorPage.waitForTimeout(1000);
            }
        }
        
        log.debug("Session start process completed");
    }

    // ==================== MULTI-USER SESSION SETUP ====================

    /**
     * Sets up a complete multi-user retrospective session.
     * 
     * @param facilitatorPage The facilitator's browser page
     * @param sessionName Name for the retrospective session
     * @param participants Variable number of participant pages with their display names
     * @return The session ID
     * 
     * Usage examples:
     * - Two users: setupRetroSession(facilitatorPage, "Test Session", 
     *                                new UserPage(participant1Page, "Alice"))
     * - Three users: setupRetroSession(facilitatorPage, "Test Session", 
     *                                  new UserPage(participant1Page, "Alice"), 
     *                                  new UserPage(participant2Page, "Bob"))
     */
    protected String setupRetroSession(Page facilitatorPage, String sessionName, UserPage... participants) {
        // Set up authentication for facilitator
        log.debug("Authenticating facilitator...");
        authenticateAsGuest(facilitatorPage, "Facilitator");
        log.debug("Facilitator authenticated, URL: {}", facilitatorPage.url());

        // Set up authentication for participants
        for (UserPage userPage : participants) {
            log.debug("Authenticating participant: {}", userPage.displayName());
            authenticateAsGuest(userPage.page(), userPage.displayName());
            log.debug("Participant {} authenticated, URL: {}", userPage.displayName(), userPage.page().url());
            
            // Debug: Check if participant page properly loaded
            String participantContent = userPage.page().textContent("body");
            if (participantContent.length() < 100) {
                log.warn("Participant {} page content seems incomplete (length: {})", userPage.displayName(), participantContent.length());
                log.warn("Content preview: {}", participantContent);
            } else {
                log.debug("Participant {} page loaded successfully", userPage.displayName());
            }
        }

        // Facilitator creates session
        log.debug("Creating retro session...");
        String sessionId = createRetroSession(facilitatorPage, sessionName);
        log.debug("Session created with ID: {}", sessionId);

        // Participants join
        for (UserPage userPage : participants) {
            log.debug("Participant {} joining session {}", userPage.displayName(), sessionId);
            joinRetroSession(userPage.page(), sessionId);
        }

        // Start the session
        log.debug("Starting retro session...");
        startRetroSession(facilitatorPage);

        return sessionId;
    }

    // ==================== NAVIGATION HELPERS ====================

    /**
     * Navigates through steps until finding the desired step type
     */
    protected void navigateToStepType(Page facilitatorPage, String stepType) {
        log.info("🎯 Navigating to step type: {}", stepType);
        
        // Skip through steps until we find the desired step type
        int maxSteps = 30; // Increased safety limit to account for multiple phases
        for (int i = 0; i < maxSteps; i++) {
            log.debug("Step navigation iteration {}: Current URL: {}", i, facilitatorPage.url());
            
            // Check if we're at the desired step type by looking for specific content
            boolean foundTargetStep = false;
            if (stepType.equals("CATEGORICAL")) {
                // For categorical steps, look for category titles like "Mad", "Sad", "Glad"
                foundTargetStep = facilitatorPage.locator("text=Mad").isVisible() || 
                    facilitatorPage.locator("[data-category]").count() > 0;
                if (foundTargetStep) {
                    log.info("✅ Found CATEGORICAL step with Mad-Sad-Glad interface");
                }
            } else if (stepType.equals("RATING")) {
                // For rating steps, look for rating inputs or scale
                foundTargetStep = facilitatorPage.locator("input[name='rating']").isVisible() ||
                    facilitatorPage.locator("text=Rate").isVisible();
                if (foundTargetStep) {
                    log.info("✅ Found RATING step with rating inputs");
                }
            } else if (stepType.equals("FREEFORM")) {
                // For freeform steps, look for content textarea
                foundTargetStep = facilitatorPage.locator("textarea[name='content']").isVisible();
                if (foundTargetStep) {
                    log.info("✅ Found FREEFORM step with content textarea");
                }
            } else {
                // Generic check for step type text
                foundTargetStep = facilitatorPage.locator("text=" + stepType).isVisible();
                if (foundTargetStep) {
                    log.info("✅ Found step type: {}", stepType);
                }
            }
            
            if (foundTargetStep) {
                break;
            }
            
            // Try to advance to next step if available
            if (facilitatorPage.locator("button:has-text('Next Step')").isVisible()) {
                log.debug("🔄 Clicking 'Next Step' button (iteration {})", i);
                facilitatorPage.click("button:has-text('Next Step')");
                
                // Wait for step content to load with better error handling
                try {
                    facilitatorPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(3000));
                } catch (Exception e) {
                    // If network idle fails, just wait for basic content
                    facilitatorPage.waitForTimeout(1000);
                }
            } else {
                log.warn("❌ No 'Next Step' button found after {} iterations. Current page content snippet: {}", 
                    i, facilitatorPage.textContent("body").substring(0, Math.min(200, facilitatorPage.textContent("body").length())));
                break;
            }
        }
        
        // Final check - log what we ended up with
        boolean finalCheck = false;
        if (stepType.equals("CATEGORICAL")) {
            finalCheck = facilitatorPage.locator("text=Mad").isVisible() || facilitatorPage.locator("[data-category]").count() > 0;
        } else if (stepType.equals("RATING")) {
            finalCheck = facilitatorPage.locator("input[name='rating']").isVisible();
        } else if (stepType.equals("FREEFORM")) {
            finalCheck = facilitatorPage.locator("textarea[name='content']").isVisible();
        }
        
        log.info("🏁 Navigation completed. Found target step type '{}': {}", stepType, finalCheck);
        if (!finalCheck) {
            log.error("❌ Failed to find step type '{}'. Final page title: {}", stepType, facilitatorPage.title());
            // Dump some debug info about current page state
            String bodyText = facilitatorPage.textContent("body");
            log.error("Page content (first 500 chars): {}", 
                bodyText.substring(0, Math.min(500, bodyText.length())));
        }
    }

    // ==================== DEBUGGING HELPERS ====================
    
    /**
     * Creates a clean test environment for debugging a specific scenario.
     * Use this method in a simple @Test method to isolate and debug specific issues.
     */
    protected void debugTestScenario(String scenarioName, DebugTestAction action) throws Exception {
        log.info("🐛 DEBUGGING SCENARIO: {}", scenarioName);
        log.info("───────────────────────────────────────────────────────────");
        
        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        
        try {
            action.execute(page);
            log.info("✅ Debug scenario completed successfully: {}", scenarioName);
        } catch (Exception e) {
            log.error("❌ Debug scenario failed: {} - {}", scenarioName, e.getMessage());
            debugScreenshot(page, "scenario_failure", "Scenario: " + scenarioName);
            debugPageState(page, "scenario failure");
            throw e;
        } finally {
            context.close();
            log.info("───────────────────────────────────────────────────────────");
        }
    }
    
    @FunctionalInterface
    public interface DebugTestAction {
        void execute(Page page) throws Exception;
    }

    // ==================== ADVANCED DEBUGGING HELPERS ====================
    
    /**
     * Captures a detailed screenshot with test context for debugging
     */
    protected void debugScreenshot(Page page, String step, String additionalInfo) {
        try {
            // Get test context from stack trace
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String testClass = "Unknown";
            String testMethod = "Unknown";
            
            for (StackTraceElement element : stackTrace) {
                if (element.getClassName().contains("IntegrationTest") && 
                    element.getMethodName().startsWith("should")) {
                    testClass = element.getClassName().substring(element.getClassName().lastIndexOf('.') + 1);
                    testMethod = element.getMethodName();
                    break;
                }
            }
            
            String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
            
            // Create descriptive filename
            String filename = String.format("%s_%s_%s_%s.png", 
                testClass,
                testMethod, 
                step.replaceAll("[^a-zA-Z0-9]", "_"),
                timestamp);
            
            java.nio.file.Path screenshotDir = java.nio.file.Paths.get("target", "debug-screenshots");
            java.nio.file.Files.createDirectories(screenshotDir);
            
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotDir.resolve(filename))
                .setFullPage(true));
            
            log.info("📸 DEBUG SCREENSHOT: {}", filename);
            log.info("   Test: {}.{}", testClass, testMethod);
            log.info("   Step: {}", step);
            log.info("   URL: {}", page.url());
            log.info("   Title: {}", page.title());
            if (additionalInfo != null) {
                log.info("   Info: {}", additionalInfo);
            }
            
        } catch (Exception e) {
            log.warn("Failed to capture debug screenshot: {}", e.getMessage());
        }
    }
    
    /**
     * Overload for simpler calls
     */
    protected void debugScreenshot(Page page, String step) {
        debugScreenshot(page, step, null);
    }
    
    /**
     * Logs detailed page state for debugging failures
     */
    protected void debugPageState(Page page, String context) {
        try {
            log.info("🔍 DEBUG PAGE STATE - {}", context);
            log.info("   URL: {}", page.url());
            log.info("   Title: {}", page.title());
            
            // Count key elements
            int formCount = page.locator("form").count();
            int buttonCount = page.locator("button").count();
            int inputCount = page.locator("input").count();
            
            log.info("   Elements: {} forms, {} buttons, {} inputs", formCount, buttonCount, inputCount);
            
            // Check for visible error indicators using JavaScript
            Object errorResult = page.evaluate("""
                () => {
                    const errorSelectors = ['.error', '.bg-red-100', '[class*="error"]'];
                    for (const selector of errorSelectors) {
                        const elements = document.querySelectorAll(selector);
                        for (const element of elements) {
                            // Check if element is actually visible (not just in DOM)
                            const style = getComputedStyle(element);
                            const isVisible = element.offsetParent !== null && 
                                            style.display !== 'none' && 
                                            style.visibility !== 'hidden' &&
                                            !element.classList.contains('hidden');
                            if (isVisible && element.textContent.trim()) {
                                return element.textContent.trim();
                            }
                        }
                    }
                    return null;
                }
            """);
            if (errorResult != null) {
                log.warn("   ⚠️ ERROR detected: {}", errorResult);
            }
            
            // Check authentication state
            boolean isLoginPage = page.url().contains("/login") || 
                page.locator("text=Sign in with GitHub").count() > 0;
            boolean hasWelcome = page.locator("text=Welcome").count() > 0;
            
            log.info("   Auth state: login page={}, has welcome={}", isLoginPage, hasWelcome);
            
            // Sample page content for debugging
            String bodyText = page.textContent("body");
            if (bodyText.length() > 200) {
                bodyText = bodyText.substring(0, 200) + "...";
            }
            log.debug("   Body preview: {}", bodyText.replaceAll("\\s+", " "));
            
        } catch (Exception e) {
            log.warn("Failed to log debug page state: {}", e.getMessage());
        }
    }

    // ==================== UTILITY HELPERS ====================

    /**
     * Waits for SSE synchronization across multiple pages
     */
    /**
     * Waits for SSE events to propagate across pages by listening for actual SSE events
     * instead of using fixed delays. Much faster and more reliable.
     */
    protected void waitForSSESync(Page... pages) {
        // For SSE synchronization, we need to wait for the actual SSE events to be processed
        // by the pages. We do this by waiting for the EventSource to receive and process messages.
        
        for (Page page : pages) {
            try {
                // Wait for SSE connection to be established and events to be processed
                page.waitForFunction("() => {" +
                    "const eventSource = window.eventSource;" +
                    "return eventSource && eventSource.readyState === 1;" + // OPEN state
                "}", new Page.WaitForFunctionOptions().setTimeout(2000));
                
                // Give a brief moment for any pending SSE events to be processed
                page.waitForTimeout(150);
                
            } catch (Exception e) {
                // Fallback: if SSE detection fails, use minimal delay
                page.waitForTimeout(300);
            }
        }
    }
    
    /**
     * Waits for a specific SSE event to arrive and update the page content.
     * Uses HTMX SSE event format and includes debugging.
     */
    protected void waitForSSEEvent(Page page, String eventType, int timeoutMs) {
        try {
            log.debug("Waiting for SSE event '{}' on page {}", eventType, page.url());
            
            // First, set up a flag to track when the event arrives
            page.evaluate("() => { window.sseEventReceived = false; window.sseEventType = null; }");
            
            // Add a listener for the specific HTMX SSE event
            page.evaluate("(eventType) => {" +
                "document.body.addEventListener('sse:' + eventType, function(evt) {" +
                "  console.log('SSE Event received:', eventType, evt);" +
                "  window.sseEventReceived = true;" +
                "  window.sseEventType = eventType;" +
                "});" +
            "}", eventType);
            
            // Wait for the event to be received
            page.waitForFunction("() => window.sseEventReceived === true",
                new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            
            log.debug("✅ SSE event '{}' received successfully", eventType);
            
        } catch (Exception e) {
            log.warn("⚠️ SSE event '{}' not detected within {}ms, falling back", eventType, timeoutMs);
            // If we can't detect the specific event, wait for general SSE activity
            try {
                page.waitForFunction("() => document.querySelector('[hx-sse]') !== null",
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs / 3));
                page.waitForTimeout(300);
            } catch (Exception fallbackException) {
                page.waitForTimeout(800);
            }
        }
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Record to pair a page with a user's display name for cleaner test setup
     */
    public record UserPage(Page page, String displayName) {}
}