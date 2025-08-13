package direct.reflect.facilitator.integration;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to validate Playwright setup without Spring Boot or Testcontainers.
 * This tests against a manually running application on localhost:8080.
 */
class PlaywrightSetupTest {

    private static final String BASE_URL = "http://localhost:8080";
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeEach
    void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void cleanup() {
        if (page != null) page.close();
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void shouldConnectToLoginPage() {
        // Navigate to login page
        page.navigate(BASE_URL + "/login");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Verify we're on the login page
        assertTrue(page.url().contains("/login"));
        
        // Check for expected elements
        assertTrue(page.isVisible("button:has-text('User Login')"));
        assertTrue(page.isVisible("button:has-text('Guest Access')"));
        
        String pageContent = page.textContent("body");
        assertTrue(pageContent.contains("Team Retrospective"));
    }


    @Test
    void shouldCreatePlaywrightInstance() {
        // Just verify Playwright can be instantiated
        assertNotNull(playwright);
        assertNotNull(browser);
        assertNotNull(context);
        assertNotNull(page);
    }
}