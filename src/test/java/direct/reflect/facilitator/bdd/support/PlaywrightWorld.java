package direct.reflect.facilitator.bdd.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.spring.ScenarioScope;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@ScenarioScope
@Slf4j
public class PlaywrightWorld {

    private static Playwright playwright;
    private static Browser browser;
    private static final Object BROWSER_LOCK = new Object();

    @Getter
    private BrowserContext browserContext;

    @Getter
    private Page page;

    @Getter
    private String baseUrl;

    private final int serverPort;

    public PlaywrightWorld(@Value("${local.server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
    }

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String EVIDENCE_DIR = ".sisyphus/evidence/bdd-pilot";

    @Before(order = 100)
    public void setUpBrowser(Scenario scenario) {
        baseUrl = "http://localhost:" + serverPort;
        log.debug("Setting up browser for scenario: {}", scenario.getName());

        ensureBrowserStarted();

        browserContext = browser.newContext();
        browserContext.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        browserContext.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);

        blockExternalCdnResources(browserContext);

        page = browserContext.newPage();
        log.debug("Browser context and page created for scenario: {}", scenario.getName());
    }

    @After(order = 100)
    public void tearDownBrowser(Scenario scenario) {
        captureScenarioScreenshot(scenario, scenario.isFailed() ? "failure" : "success");
        if (browserContext != null) {
            try {
                browserContext.close();
            } catch (Exception e) {
                log.warn("Failed to close browser context after scenario '{}': {}", scenario.getName(), e.getMessage());
            }
        }
        log.debug("Browser context closed after scenario: {}", scenario.getName());
    }

    /**
     * Creates an additional isolated browser context for multi-user scenarios.
     * The caller is responsible for closing this context.
     */
    public BrowserContext createAdditionalContext() {
        ensureBrowserStarted();
        BrowserContext additionalContext = browser.newContext();
        additionalContext.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        additionalContext.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
        blockExternalCdnResources(additionalContext);
        return additionalContext;
    }

    public void clearCookies() {
        page.context().clearCookies();
    }

    private static void ensureBrowserStarted() {
        if (browser != null && browser.isConnected()) {
            return;
        }
        synchronized (BROWSER_LOCK) {
            if (browser != null && browser.isConnected()) {
                return;
            }
            boolean debugMode = Boolean.parseBoolean(System.getenv("PLAYWRIGHT_DEBUG"));
            if (playwright == null) {
                playwright = Playwright.create();
            }
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(!debugMode)
                    .setSlowMo(debugMode ? 500 : 0);
            browser = playwright.chromium().launch(launchOptions);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (browser != null) {
                        browser.close();
                    }
                } catch (Exception e) {
                    log.debug("Browser close failed during shutdown: {}", e.getMessage());
                }
                try {
                    if (playwright != null) {
                        playwright.close();
                    }
                } catch (Exception e) {
                    log.debug("Playwright close failed during shutdown: {}", e.getMessage());
                }
            }, "playwright-bdd-shutdown"));

            log.info("Playwright browser started (headless={})", !debugMode);
        }
    }

    private void blockExternalCdnResources(BrowserContext ctx) {
        ctx.route(Pattern.compile(".*fonts\\.googleapis\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*fonts\\.gstatic\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*cdn\\.tailwindcss\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*cdn\\.jsdelivr\\.net.*"), route -> route.abort());
    }

    private void captureScenarioScreenshot(Scenario scenario, String outcome) {
        if (page == null) {
            return;
        }
        try {
            Path evidenceDir = Paths.get(EVIDENCE_DIR);
            Files.createDirectories(evidenceDir);

            String safeName = scenario.getName()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toLowerCase();
            Path screenshotPath = evidenceDir.resolve(safeName + "_" + outcome + ".png");

            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
            log.info("{} screenshot saved: {}", outcome.substring(0, 1).toUpperCase() + outcome.substring(1), screenshotPath);
        } catch (Exception e) {
            log.warn("Failed to capture {} screenshot for scenario '{}': {}", outcome, scenario.getName(), e.getMessage());
        }
    }
}
