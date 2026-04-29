package direct.reflect.facilitator.e2e.support;

import com.microsoft.playwright.*;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import com.redis.testcontainers.RedisContainer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.regex.Pattern;

import direct.reflect.facilitator.configurator.RetroTemplateRepository;
import direct.reflect.facilitator.auth.TestAuthConfiguration;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.config.TestSecurityOverride;
import direct.reflect.facilitator.configurator.RetroStageRepository;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.participant.ParticipantRepository;
import direct.reflect.facilitator.facilitation.session.RetroSessionRepository;
import direct.reflect.facilitator.facilitation.response.ParticipantResponseRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Base class for browser end-to-end tests with common setup for Playwright, TestContainers, and authentication.
 * All browser-based end-to-end tests should extend this class.
 *
 * Note: Each browser context maintains its own session, so multiple users can be authenticated
 * simultaneously using Spring Security's session-based authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.profiles.active=test,import")
@Import({
    TestAuthConfiguration.class, // Provides TestAuthController with /test/* endpoints
    TestSecurityOverride.class, // Extends SecurityConfig to allow /test/* endpoints
    TestRedisConfig.class // Caps Redis retry backoff to prevent infinite reconnect loops
})
@Slf4j
public abstract class BaseEndToEndTest {

    // ==================== PLAYWRIGHT CONFIGURATION ====================

    /**
     * Timeout constants for Playwright operations.
     * Keep it simple: one default for most operations, one short for quick checks.
     */
    protected static final int DEFAULT_TIMEOUT_MS = 5000;   // Navigation, elements, SSE - use for 95% of cases
    protected static final int SHORT_TIMEOUT_MS = 2000;     // Quick checks when element should already exist
    protected static final int SSE_PROPAGATION_TIMEOUT_MS = 15000;  // SSE events may need time to propagate across multiple browser contexts

    @LocalServerPort
    protected int port;

    // Singleton containers — started once for the entire test run via static initializer,
    // never stopped between test classes. This prevents the "Failed to start bean
    // 'springSessionRedisMessageListenerContainer'" failure that occurs when Testcontainers
    // stops/restarts containers between classes (per-class store in TestcontainersExtension).
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("facilitator_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    static final RedisContainer redis = new RedisContainer("redis:alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected String baseUrl;

    // Test failure reporting infrastructure
    private List<ConsoleMessage> consoleErrors = new ArrayList<>();
    private List<String> networkFailures = new ArrayList<>();

    /**
     * CDN domains intentionally blocked in tests via blockExternalCdnResources().
     * Network failures for these domains are expected and must not be logged as errors.
     */
    private static final Set<String> BLOCKED_CDN_DOMAINS = Set.of(
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "cdn.tailwindcss.com",
        "cdn.jsdelivr.net"
    );

    private boolean isBlockedCdnUrl(String url) {
        return BLOCKED_CDN_DOMAINS.stream().anyMatch(url::contains);
    }

    @Autowired
    protected RetroTemplateRepository templateRepository;

    @Autowired
    protected RetroStageRepository stageRepository;

    @Autowired
    protected RetroSessionService sessionService;

    @Autowired
    protected ParticipantRepository participantRepository;

    @Autowired
    protected RetroSessionRepository retroSessionRepository;

    @Autowired
    protected ParticipantResponseRepository participantResponseRepository;

    // ==================== ACTIVITY BREADCRUMB TRAIL ====================

    /**
     * ThreadLocal storage for tracking test activities.
     * Provides breadcrumb trail for debugging test failures.
     */
    private static final ThreadLocal<List<String>> activityBreadcrumbs =
        ThreadLocal.withInitial(ArrayList::new);

    /**
     * Records an activity with timestamp for the breadcrumb trail.
     * Use this in all helper methods to track the sequence of operations.
     */
    protected void recordActivity(String action) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
        activityBreadcrumbs.get().add(String.format("[%s] %s", timestamp, action));
    }

    /**
     * Logs the activity trail to help debug test failures.
     * Shows the sequence of operations leading up to a failure.
     */
    protected void logActivityTrail() {
        List<String> trail = activityBreadcrumbs.get();
        if (!trail.isEmpty()) {
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ ACTIVITY TRAIL (last {} operations):", trail.size());
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            for (int i = 0; i < trail.size(); i++) {
                log.error("[TEST_FAILURE_REPORT] ║ {}/{}: {}", i + 1, trail.size(), trail.get(i));
            }
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        }
    }

    /**
     * Clears the activity trail.
     * Called at the start of each test to ensure clean state.
     */
    protected void clearActivityTrail() {
        activityBreadcrumbs.get().clear();
    }

    // ==================== FORM STATE CAPTURE ====================

    /**
     * Captures current form state from the page using Playwright's native JavaScript evaluation.
     * Extracts all form fields (input, textarea, select) with their current values.
     *
     * @param page Page to capture form state from
     * @return Map of field names/IDs to their current values
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> captureFormState(Page page) {
        try {
            // Use Playwright's native JavaScript evaluation
            // Try-catch prevents indefinite hangs by using Playwright's default timeout
            Object result = page.evaluate("""
                () => {
                    const state = {};
                    document.querySelectorAll('form input, form textarea, form select')
                        .forEach(el => {
                            const key = el.name || el.id || el.type || 'unnamed';
                            state[key] = el.value || '[empty]';
                        });
                    return state;
                }
            """);

            if (result instanceof Map) {
                return (Map<String, String>) result;
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("Failed to capture form state: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Logs form state with TEST_FAILURE_REPORT marker for debugging.
     * Shows what values were in form fields at time of failure.
     *
     * @param page Page to capture form state from
     * @param context Description of when this capture occurred (e.g., "authentication failure")
     */
    private void logFormState(Page page, String context) {
        Map<String, String> formState = captureFormState(page);
        if (!formState.isEmpty()) {
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ FORM STATE at {}", context);
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            formState.forEach((field, value) ->
                log.error("[TEST_FAILURE_REPORT] ║   {} = {}", field, value));
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        }
    }

    // ==================== CONSOLE AND NETWORK MONITORING ====================


    /**
     * Sets up Playwright's native console and network listeners on the browser context.
     * This automatically applies to all pages created from this context.
     *
     * LOGGING TAXONOMY:
     *   [BROWSER_MONITOR] — Real-time observations during test execution (WARN level).
     *                       These fire continuously and do NOT indicate test failure.
     *   [CDN_BLOCKED]     — Intentionally blocked CDN resources (DEBUG level, hidden by default).
     *   [TEST_FAILURE_REPORT] — Only used by reportTestFailure() when a test actually fails.
     *
     * CDN resources (fonts.googleapis.com, cdn.tailwindcss.com, etc.) are intentionally
     * aborted by blockExternalCdnResources() and silently filtered here.
     */
    private void setupConsoleAndNetworkMonitoring(BrowserContext context) {
        // Clear previous test's errors
        consoleErrors.clear();
        networkFailures.clear();
        // Console error listener — captures browser-side JavaScript errors
        context.onConsoleMessage(msg -> {
            if (msg.type().equals("error")) {
                log.warn("[BROWSER_MONITOR] Browser console error: {}", msg.text());
                consoleErrors.add(msg);
            }
        });

        // Network failure listener — captures connection errors, aborted requests
        // Silently skips intentionally blocked CDN resources (expected behavior)
        context.onRequestFailed(request -> {
            if (isBlockedCdnUrl(request.url())) {
                log.debug("[CDN_BLOCKED] {} {} (expected — blocked by test infrastructure)",
                    request.method(), request.url());
                return;
            }
            String failure = String.format("%s %s - %s",
                request.method(), request.url(), request.failure());
            log.warn("[BROWSER_MONITOR] Network failure: {}", failure);
            networkFailures.add(failure);
        });

        // HTTP error response listener — captures 4xx/5xx status codes
        context.onResponse(response -> {
            if (response.status() >= 400) {
                String failure = String.format("HTTP %d: %s %s",
                    response.status(),
                    response.request().method(),
                    response.url());
                log.warn("[BROWSER_MONITOR] {}", failure);
                networkFailures.add(failure);
            }
        });
    }

    /**
     * Logs summary of browser console errors captured during test execution.
     * Called from comprehensive error reporter on test failure.
     */
    private void logBrowserConsoleErrors() {
        if (!consoleErrors.isEmpty()) {
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ BROWSER CONSOLE ERRORS SUMMARY:");
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            for (ConsoleMessage msg : consoleErrors) {
                log.error("[TEST_FAILURE_REPORT] ║ {}", msg.text());
                if (msg.location() != null) {
                    log.error("[TEST_FAILURE_REPORT] ║   at {}:{}", msg.location(), msg.location());
                }
            }
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        }
    }

    /**
     * Logs summary of network failures captured during test execution.
     * Called from comprehensive error reporter on test failure.
     */
    private void logNetworkFailures() {
        if (!networkFailures.isEmpty()) {
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ NETWORK FAILURES SUMMARY:");
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            for (String failure : networkFailures) {
                log.error("[TEST_FAILURE_REPORT] ║ {}", failure);
            }
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        }
    }

    /**
     * Creates a new browser context with console and network monitoring enabled.
     * Use this helper when tests need multiple user contexts (e.g., facilitator + participants).
     *
     * @return A new BrowserContext with monitoring enabled
     */
    protected BrowserContext createMonitoredContext() {
        BrowserContext newContext = browser.newContext();

        // Set default timeouts to prevent indefinite waits
        newContext.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        newContext.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);

        installSyncDiagnostics(newContext);
        setupConsoleAndNetworkMonitoring(newContext);
        blockExternalCdnResources(newContext);
        return newContext;
    }

    private void installSyncDiagnostics(BrowserContext browserContext) {
        browserContext.addInitScript("""
            (() => {
              if (window.__omoSyncDiagnosticsInstalled) {
                return;
              }

              window.__omoSyncDiagnosticsInstalled = true;

              const MAX_RECENT_EVENTS = 5;
              const authoritativePattern = /\\/api\\/retro\\/[^/]+\\/(state|participants|timer|actions|escalations)(?:[/?#].*)?$/;
              const now = () => new Date().toISOString();

              const diagnostics = {
                connectionState: 'not_started',
                openCount: 0,
                errorCount: 0,
                navigatorOnLine: navigator.onLine,
                signaledVersion: null,
                appliedVersion: null,
                lastSignal: null,
                recentSignals: [],
                lastRefetch: null,
                recentRefetches: [],
                lastConnectionEvent: null,
                eventSourceUrl: null
              };

              window.__omoSyncDiagnostics = diagnostics;

              const pushRecent = (key, entry) => {
                diagnostics[key] = [entry, ...(diagnostics[key] || [])].slice(0, MAX_RECENT_EVENTS);
              };

              const extractSyncVersion = (body) => {
                if (!body || typeof body !== 'object') {
                  return null;
                }
                return typeof body.syncVersion === 'number' ? body.syncVersion : null;
              };

              const recordConnectionEvent = (type, extra = {}) => {
                diagnostics.navigatorOnLine = navigator.onLine;
                diagnostics.lastConnectionEvent = {
                  type,
                  timestamp: now(),
                  ...extra
                };
              };

              window.addEventListener('online', () => {
                diagnostics.navigatorOnLine = true;
                recordConnectionEvent('browser_online');
              });

              window.addEventListener('offline', () => {
                diagnostics.navigatorOnLine = false;
                recordConnectionEvent('browser_offline');
              });

              const NativeFetch = window.fetch.bind(window);
              window.fetch = async (...args) => {
                const request = args[0];
                const init = args[1];
                const url = typeof request === 'string' ? request : request?.url;
                const method = (init?.method || request?.method || 'GET').toUpperCase();
                const trackAuthoritative = typeof url === 'string' && method === 'GET' && authoritativePattern.test(url);

                try {
                  const response = await NativeFetch(...args);

                  if (trackAuthoritative) {
                    const refetch = {
                      url,
                      method,
                      status: response.status,
                      ok: response.ok,
                      timestamp: now()
                    };

                    try {
                      const contentType = response.headers.get('content-type') || '';
                      if (contentType.includes('application/json')) {
                        const body = await response.clone().json();
                        const syncVersion = extractSyncVersion(body);
                        refetch.syncVersion = syncVersion;
                        if (typeof syncVersion === 'number') {
                          diagnostics.appliedVersion = diagnostics.appliedVersion == null
                            ? syncVersion
                            : Math.max(diagnostics.appliedVersion, syncVersion);
                        }
                      }
                    } catch (parseError) {
                      refetch.parseError = String(parseError);
                    }

                    diagnostics.lastRefetch = refetch;
                    pushRecent('recentRefetches', refetch);
                  }

                  return response;
                } catch (error) {
                  if (trackAuthoritative) {
                    const refetch = {
                      url,
                      method,
                      ok: false,
                      error: String(error),
                      timestamp: now()
                    };
                    diagnostics.lastRefetch = refetch;
                    pushRecent('recentRefetches', refetch);
                  }
                  throw error;
                }
              };

              const NativeEventSource = window.EventSource;

              const inspectEvent = (event) => {
                if (!event) {
                  return;
                }

                if (event.type === 'open') {
                  diagnostics.connectionState = 'open';
                  diagnostics.openCount += 1;
                  recordConnectionEvent('open', { openCount: diagnostics.openCount });
                  return;
                }

                if (event.type === 'error') {
                  diagnostics.connectionState = 'error';
                  diagnostics.errorCount += 1;
                  recordConnectionEvent('error', { errorCount: diagnostics.errorCount });
                  return;
                }

                if (typeof event.data !== 'string') {
                  return;
                }

                try {
                  const parsed = JSON.parse(event.data);
                  const syncVersion = typeof parsed?.syncVersion === 'number' ? parsed.syncVersion : null;

                  if (syncVersion != null) {
                    diagnostics.signaledVersion = diagnostics.signaledVersion == null
                      ? syncVersion
                      : Math.max(diagnostics.signaledVersion, syncVersion);

                    const signal = {
                      type: event.type,
                      syncVersion,
                      timestamp: now()
                    };
                    diagnostics.lastSignal = signal;
                    pushRecent('recentSignals', signal);
                  }
                } catch (_ignored) {
                  // Non-envelope event payloads are intentionally ignored.
                }
              };

              function InstrumentedEventSource(url, configuration) {
                const eventSource = new NativeEventSource(url, configuration);
                diagnostics.connectionState = 'connecting';
                diagnostics.eventSourceUrl = String(url);
                recordConnectionEvent('connecting', { url: diagnostics.eventSourceUrl });

                const nativeDispatchEvent = eventSource.dispatchEvent.bind(eventSource);
                eventSource.dispatchEvent = function(event) {
                  inspectEvent(event);
                  return nativeDispatchEvent(event);
                };

                return eventSource;
              }

              InstrumentedEventSource.prototype = NativeEventSource.prototype;
              Object.setPrototypeOf(InstrumentedEventSource, NativeEventSource);
              InstrumentedEventSource.CONNECTING = NativeEventSource.CONNECTING;
              InstrumentedEventSource.OPEN = NativeEventSource.OPEN;
              InstrumentedEventSource.CLOSED = NativeEventSource.CLOSED;

              window.EventSource = InstrumentedEventSource;
            })();
            """);
    }

    private void blockExternalCdnResources(BrowserContext ctx) {
        ctx.route(Pattern.compile(".*fonts\\.googleapis\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*fonts\\.gstatic\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*cdn\\.tailwindcss\\.com.*"), route -> route.abort());
        ctx.route(Pattern.compile(".*cdn\\.jsdelivr\\.net.*"), route -> route.abort());
    }

    @BeforeAll
    static void setUpPlaywright() {
        if (playwright != null) return; // Already initialized — reuse existing instance across subclasses
        boolean debugMode = Boolean.parseBoolean(System.getenv("PLAYWRIGHT_DEBUG"));
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            .setHeadless(!debugMode)
            .setSlowMo(debugMode ? 500 : 0);

        if (debugMode) {
            log.info("🐛 PLAYWRIGHT DEBUG MODE ENABLED");
            log.info("   - Headful browser (you can see what's happening)");
            log.info("   - 500ms delay between actions");
            log.info("   - Screenshots will be saved on failures");
        }
        playwright = Playwright.create();
        browser = playwright.chromium().launch(launchOptions);
        // Register shutdown hook so the single shared instance is always cleaned up
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (browser != null) { try { browser.close(); } catch (Exception ignored) {} }
            if (playwright != null) { try { playwright.close(); } catch (Exception ignored) {} }
        }));
    }

    @AfterAll
    static void tearDownPlaywright() {
        // Do NOT close here — the playwright/browser instances are reused across all subclasses.
        // The JVM shutdown hook registered in setUpPlaywright() handles final cleanup.
    }
    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Clear activity trail for clean state at start of each test
        clearActivityTrail();
        baseUrl = "http://localhost:" + port;
        // Create browser context and set up monitoring
        context = browser.newContext();
        // Set default timeout to prevent indefinite waits (Playwright's default is 30s, but we want faster failures)
        context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
        installSyncDiagnostics(context);
        // Set up console and network monitoring (Playwright native listeners)
        setupConsoleAndNetworkMonitoring(context);
        blockExternalCdnResources(context);
        // Enable Playwright tracing in debug mode for local debugging
        boolean debugMode = Boolean.parseBoolean(System.getenv("PLAYWRIGHT_DEBUG"));
        if (debugMode) {
            context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
            log.info("📊 Playwright tracing enabled");
        }
    }
    @AfterEach
    void tearDown(TestInfo testInfo) {
        boolean debugMode = Boolean.parseBoolean(System.getenv("PLAYWRIGHT_DEBUG"));
        // Note: We save trace for all tests in debug mode since JUnit 5 doesn't provide
        // easy failure detection in @AfterEach without custom extensions
        if (debugMode && context != null) {
            try {
                String testName = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
                Path tracePath = Paths.get("target/traces/" + testName + ".zip");
                Files.createDirectories(tracePath.getParent());
                context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                log.info("📊 Playwright trace saved: {}", tracePath);
                log.info("   View with: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args='show-trace {}'", tracePath);
            } catch (Exception e) {
                log.warn("Failed to save Playwright trace: {}", e.getMessage());
            }
        }

        // Close browser context (browser and playwright are closed in @AfterAll)
        if (context != null) context.close();
        // Spring context is reused across all subclasses — @DirtiesContext is intentionally absent
        // to prevent Spring Boot 4 / Testcontainers @ServiceConnection incompatibility on context rebuild
        clearActivityTrail();
    }

    // ==================== AUTHENTICATION HELPERS ====================

    protected void authenticateAsGuest(Page page, String displayName) {
        recordActivity("authenticateAsGuest: " + displayName);
        try {
            page.context().clearCookies();

            page.navigate(baseUrl + "/login");
            page.waitForSelector("input[name='displayName']",
                new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            page.fill("input[name='displayName']", displayName);
            page.click("button[type='submit']");

            page.waitForURL(
                url -> url.equals(baseUrl + "/") || url.equals(baseUrl + "/home") || url.endsWith("/"),
                new Page.WaitForURLOptions().setTimeout(DEFAULT_TIMEOUT_MS)
            );

            page.waitForSelector("input[name='sessionName']",
                new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));

            if (page.url().contains("/login")) {
                throw new RuntimeException("Guest authentication failed - still on login page");
            }

            log.info("✅ Guest authentication successful for '{}', URL: {}", displayName, page.url());

        } catch (Exception e) {
            reportTestFailure(page, "Guest Authentication for " + displayName, e);
            throw new RuntimeException("Failed to authenticate guest user '" + displayName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates a user as OAuth2 user using the test authentication endpoint.
     * Uses a simpler approach that navigates to a test page first to establish session context.
     */
    protected void authenticateAsOAuth2User(Page page, String username, String displayName, String email) {
        recordActivity("authenticateAsOAuth2User: " + username + " (" + displayName + ")");
        try {
            // Clear cookies first
            page.context().clearCookies();

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
                    new Page.WaitForURLOptions().setTimeout(SHORT_TIMEOUT_MS));

                log.debug("OAuth2 authentication completed, current URL: {}", page.url());

            } catch (Exception e) {
                log.warn("OAuth2 authentication redirect timeout for user '{}': {}", username, e.getMessage());
                log.debug("Current URL after timeout: {}", page.url());

                if (!page.url().contains(baseUrl + "/") && !page.url().contains("/login")) {
                    log.debug("Manually navigating to home page to complete authentication");
                    page.navigate(baseUrl + "/");
                    page.waitForSelector("input[name='sessionName']",
                        new Page.WaitForSelectorOptions().setTimeout(SHORT_TIMEOUT_MS));
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

            }

        } catch (Exception e) {
            log.error("❌ OAuth2 authentication error for user '{}': {}", username, e.getMessage());
            reportTestFailure(page, "OAuth2 Authentication for " + username + " (" + displayName + ")", e);
            throw new RuntimeException("Failed to authenticate OAuth2 user '" + username + "': " + e.getMessage(), e);
        }
    }

    // ==================== SESSION MANAGEMENT HELPERS ====================

    /**
     * Creates a new retrospective session and returns the session ID
     */
    protected String createRetroSession(Page facilitatorPage, String sessionName) {
        recordActivity("createRetroSession: " + sessionName);
        log.debug("Starting session creation for: {}", sessionName);

        facilitatorPage.navigate(baseUrl + "/");
        facilitatorPage.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        log.debug("Session creation form found");

        final String[] redirectUrl = {null};
        facilitatorPage.onResponse(response -> {
            if (response.url().contains("/api/retro/create")) {
                log.debug("Session create response: status={}", response.status());
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

        facilitatorPage.fill("input[name='sessionName']", sessionName);
        facilitatorPage.click("button:has-text('Create Session')");

        if (!facilitatorPage.url().contains("/retro/")) {
            try {
                facilitatorPage.waitForURL(
                    url -> url.contains("/retro/"),
                    new Page.WaitForURLOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS * 2)
                );
            } catch (Exception e) {
                log.warn("waitForURL to /retro/ timed out or failed: {}. Attempting manual navigation.", e.getMessage());
                if (redirectUrl[0] != null && !redirectUrl[0].isEmpty()) {
                    String targetUrl = redirectUrl[0].startsWith("http") ? redirectUrl[0] : baseUrl + redirectUrl[0];
                    log.info("Navigating manually to HX-Redirect URL: {}", targetUrl);
                    facilitatorPage.navigate(targetUrl);
                } else {
                    String persistedSessionId = retroSessionRepository.findAll().stream()
                        .filter(session -> sessionName.equals(session.getName()))
                        .max(Comparator.comparing(session -> session.getCreatedAt()))
                        .map(session -> session.getId().toString())
                        .orElse("");
                    if (!persistedSessionId.isEmpty()) {
                        String targetUrl = baseUrl + "/retro/" + persistedSessionId;
                        log.info("Navigating manually to persisted session URL: {}", targetUrl);
                        facilitatorPage.navigate(targetUrl);
                    } else if (facilitatorPage.url().contains("/retro/")) {
                        log.info("URL already at /retro/ despite waitForURL timeout, continuing");
                    } else {
                        log.error("No HX-Redirect URL or persisted session found; cannot recover from waitForURL failure");
                        throw new RuntimeException("Session creation redirect failed and no session could be recovered", e);
                    }
                }
            }
        } else {
            log.debug("URL already contains /retro/ after click, skipping waitForURL");
        }
        facilitatorPage.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(SHORT_TIMEOUT_MS));

        String sessionUrl = facilitatorPage.url();
        log.debug("Facilitator URL after session creation: {}", sessionUrl);

        if (redirectUrl[0] != null && !redirectUrl[0].isEmpty()) {
            String extractedId = redirectUrl[0].substring(redirectUrl[0].lastIndexOf("/") + 1);
            log.debug("Extracted session ID from HX-Redirect header: {}", extractedId);
            return extractedId;
        } else {
            String extractedId = sessionUrl.substring(sessionUrl.lastIndexOf("/") + 1);
            log.debug("Extracted session ID from current URL: {}", extractedId);
            return extractedId;
        }
    }

    /**
     * Joins an existing retrospective session
     */
    protected void joinRetroSession(Page participantPage, String sessionId) {
        recordActivity("joinRetroSession: " + sessionId);
        try {
            log.debug("Starting join session process - session ID: {}, current URL: {}", sessionId, participantPage.url());

            participantPage.navigate(baseUrl + "/");

            log.info("Waiting for join session form input[name='retroId']...");
            try {
                participantPage.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
                log.info("✅ Join session form found");
            } catch (Exception e) {
                log.error("❌ Failed to find join session form");
                log.error("Current URL: {}", participantPage.url());
                log.error("Page title: {}", participantPage.title());
                throw new RuntimeException("Join session form not found: " + e.getMessage(), e);
            }

            log.info("Filling session ID into form: {}", sessionId);
            participantPage.fill("input[name='retroId']", sessionId);

            String filledValue = participantPage.inputValue("input[name='retroId']");
            log.info("Form filled with value: {}", filledValue);
            if (!sessionId.equals(filledValue)) {
                log.error("❌ Form fill failed! Expected: {}, Actual: {}", sessionId, filledValue);
            }

            final String[] redirectUrl = {null};
            participantPage.onRequest(request -> {
                if (request.url().contains("/api/retro/join")) {
                    log.info("📤 JOIN REQUEST: {} {}", request.method(), request.url());
                }
            });

            participantPage.onResponse(response -> {
                if (response.url().contains("/api/retro/join")) {
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
                }
            });

            log.info("🖱️ Clicking 'Join Session' button...");
            participantPage.click("button:has-text('Join Session')");

            try {
                participantPage.waitForURL(url -> url.contains("/retro/") || url.contains("error="),
                    new Page.WaitForURLOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            } catch (Exception e) {
                participantPage.waitForTimeout(500);
            }

            String finalUrl = participantPage.url();
            if (finalUrl.contains("/retro/")) {
                log.info("✅ Join successful → {}", finalUrl);
            } else {
                log.warn("❌ Join failed, URL: {}", finalUrl);
            }

        if (!participantPage.url().contains("/retro/")) {
            String targetUrl = redirectUrl[0] != null ? (redirectUrl[0].startsWith("http") ? redirectUrl[0] : baseUrl + redirectUrl[0]) : (baseUrl + "/retro/" + sessionId);
            log.info("Redirect didn't occur, navigating manually to: {}", targetUrl);
            participantPage.navigate(targetUrl);
            participantPage.waitForSelector("h2:has-text('Session Lobby'), [data-step-index]",
                new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
            log.info("Manual navigation completed, URL is now: {}", participantPage.url());
        }

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

            boolean hasErrorContent = participantPage.locator(".bg-red-100").count() > 0 ||
                                    participantPage.locator("[class*='error']").count() > 0;
            if (hasErrorContent) {
                String errorText = participantPage.locator(".bg-red-100").count() > 0 ?
                                 participantPage.locator(".bg-red-100").textContent() :
                                 "Error detected on page";
                log.error("❌ Found error content on page: {}", errorText);
                throw new RuntimeException("Failed to join session '" + sessionId + "': " + errorText);
            }

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

    protected void startRetroSession(Page facilitatorPage, String sessionId) {
        recordActivity("startRetroSession");
        log.debug("Starting retro session...");

        String currentUrl = facilitatorPage.url();

        final boolean[] startConfirmed = {false};
        facilitatorPage.onResponse(response -> {
            if (response.url().contains("/" + sessionId + "/start") && response.request().method().equals("POST") && response.status() == 200) {
                startConfirmed[0] = true;
                log.debug("Start session response confirmed with status 200");
            }
        });

        facilitatorPage.click("[data-testid='start-retro-button']");
        log.debug("Start button clicked...");

        try {
            facilitatorPage.waitForFunction(
                "() => !document.body.textContent.includes('Session Lobby') && !document.body.textContent.includes('Loading retrospective')",
                null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            log.debug("Lobby disappeared - retro content visible");
        } catch (Exception e) {
            log.warn("UI did not transition out of lobby (POST aborted, confirmed={}). Using service fallback.", startConfirmed[0]);
            java.util.UUID retroId = java.util.UUID.fromString(sessionId);
            if (retroSessionRepository.findById(retroId)
                    .map(s -> s.getPhase() == direct.reflect.facilitator.facilitation.session.RetroPhase.LOBBY)
                    .orElse(false)) {
                sessionService.startSession(retroId);
            }
            facilitatorPage.navigate(currentUrl, new Page.NavigateOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            facilitatorPage.waitForFunction(
                "() => !document.body.textContent.includes('Session Lobby') && !document.body.textContent.includes('Loading retrospective')",
                null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            log.debug("Lobby disappeared after service-level start + page reload");
        }

        facilitatorPage.waitForSelector("[data-step-index]",
            new Page.WaitForSelectorOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));

        facilitatorPage.waitForSelector("[data-testid='next-step-button']",
            new Page.WaitForSelectorOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
        log.debug("Session started - Next button visible");
    }

    protected void refreshRetroPageUntilLoaded(Page page, String sessionId, String expectedPhase, String readySelector) {
        recordActivity("refreshRetroPageUntilLoaded: " + sessionId + " phase=" + expectedPhase + " selector=" + readySelector);
        String retroUrl = baseUrl + "/retro/" + sessionId;
        AssertionError lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                log.debug("Refreshing retro page {} attempt {}/2", sessionId, attempt);

                if (page.url().contains("/retro/" + sessionId)) {
                    page.reload(new Page.ReloadOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                } else {
                    page.navigate(retroUrl, new Page.NavigateOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                }

                page.waitForFunction(
                    "(phase) => { " +
                        "const bodyText = document.body?.textContent ?? ''; " +
                        "if (bodyText.includes('Loading retrospective')) return false; " +
                        "const retro = document.querySelector('[data-testid=\"retro-content\"]'); " +
                        "if (!retro) return false; " +
                        "return !phase || retro.getAttribute('data-phase') === phase; " +
                    "}",
                    expectedPhase,
                    new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS)
                );

                waitForElement(page, "[data-testid='retro-content']", SSE_PROPAGATION_TIMEOUT_MS);
                if (readySelector != null && !readySelector.isBlank()) {
                    waitForElement(page, readySelector, SSE_PROPAGATION_TIMEOUT_MS);
                }
                return;
            } catch (AssertionError | RuntimeException e) {
                log.warn("Retro page {} not ready on attempt {}/2: {}", sessionId, attempt, e.getMessage());
                lastFailure = e instanceof AssertionError assertionError
                    ? assertionError
                    : new AssertionError("Retro page did not finish loading for session " + sessionId, e);
            }
        }

        throw lastFailure != null ? lastFailure : new AssertionError("Retro page did not finish loading for session " + sessionId);
    }


    protected boolean clickNextAndWait(Page facilitatorPage, int timeoutMs, Page... participantPages) {
        try {
            Locator nextButton = facilitatorPage.locator("[data-testid='next-step-button']");
            if (nextButton.count() == 0) {
                log.debug("Next button not found, assuming end of flow");
                return false;
            }

            Integer currentStepIndex = getCurrentStepIndex(facilitatorPage);
            String currentPhase = getCurrentPhase(facilitatorPage);
            if (currentStepIndex == null) {
                log.warn("Could not determine current step index, using fallback approach");
                nextButton.click(new Locator.ClickOptions().setTimeout(timeoutMs));
                facilitatorPage.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
                return true;
            }

            log.debug("Before click: step={}, phase={}", currentStepIndex, currentPhase);

            List<Page> allPages = new ArrayList<>();
            allPages.add(facilitatorPage);
            Collections.addAll(allPages, participantPages);

            Response nextResponse = facilitatorPage.waitForResponse(
                response -> response.url().contains("/next") && response.request().method().equals("POST"),
                new Page.WaitForResponseOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS),
                () -> {
                    nextButton.click(new Locator.ClickOptions().setTimeout(timeoutMs));
                    log.debug("Next button clicked, waiting for /next POST response...");
                }
            );
            log.debug("/next POST response received with status: {}", nextResponse.status());

            waitForStepChange(currentStepIndex, currentPhase, SSE_PROPAGATION_TIMEOUT_MS, allPages.toArray(new Page[0]));
            
            Integer newStepIndex = getCurrentStepIndex(facilitatorPage);
            String newPhase = getCurrentPhase(facilitatorPage);
            log.debug("After click: step={}, phase={}", newStepIndex, newPhase);
            return true;
            
        } catch (Exception e) {
            log.warn("clickNextAndWait failed: {}", e.getMessage());
            return false;
        }
    }
    
    protected void waitForStepChange(int previousStepIndex, String previousPhase, int timeoutMs, Page... pages) {
        log.debug("Waiting for step change from step={}, phase={} on {} pages", previousStepIndex, previousPhase, pages.length);
        
        for (int i = 0; i < pages.length; i++) {
            Page page = pages[i];
            try {
                page.waitForFunction(
                    String.format(
                        "() => { " +
                        "  const el = document.querySelector('[data-step-index]'); " +
                        "  if (!el) return false; " +
                        "  const stepIdx = parseInt(el.getAttribute('data-step-index')); " +
                        "  const phase = el.getAttribute('data-phase'); " +
                        "  return stepIdx !== %d || phase !== '%s'; " +
                        "}",
                        previousStepIndex, previousPhase
                    ),
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs)
                );
                log.debug("Page {}/{} detected step/phase change", i + 1, pages.length);
            } catch (Exception e) {
                Integer actualIndex = getCurrentStepIndex(page);
                String actualPhase = getCurrentPhase(page);
                log.error("Page {}/{} no change detected: step={}, phase={}", 
                    i + 1, pages.length, actualIndex, actualPhase);
                throw new AssertionError(String.format(
                    "Page %d/%d no step change after %dms (still step=%d, phase=%s)",
                    i + 1, pages.length, timeoutMs, actualIndex, actualPhase), e);
            }
        }
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

        log.debug("Starting retro session...");
        final boolean[] startConfirmed = {false};
        facilitatorPage.onResponse(response -> {
            if (response.url().contains("/" + sessionId + "/start") && response.request().method().equals("POST") && response.status() == 200) {
                startConfirmed[0] = true;
            }
        });
        waitForElement(facilitatorPage, "[data-testid='start-retro-button']", SSE_PROPAGATION_TIMEOUT_MS);
        clickElement(facilitatorPage, "[data-testid='start-retro-button']");

        // Wait for ALL pages (facilitator + participants) to transition from lobby → retro
        // Check that "Session Lobby" disappears on all pages
        log.debug("Waiting for all pages to leave lobby...");
        for (UserPage userPage : participants) {
            try {
                userPage.page().waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                    null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                log.debug("✅ Participant {} left lobby", userPage.displayName());
            } catch (Exception e) {
                log.warn("Participant {} lobby did not disappear - session may not have started", userPage.displayName());
            }
        }

        boolean facilitatorLeftLobby = false;
        try {
            facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                null, new Page.WaitForFunctionOptions().setTimeout(SHORT_TIMEOUT_MS));
            facilitatorLeftLobby = true;
            log.debug("✅ Facilitator left lobby");
        } catch (Exception ignored) {
            log.debug("Facilitator still in lobby after click");
        }

        if (!facilitatorLeftLobby) {
            log.warn("POST to /start may have been aborted (confirmed={}). Using service fallback.", startConfirmed[0]);
            java.util.UUID retroId = java.util.UUID.fromString(sessionId);
            if (retroSessionRepository.findById(retroId)
                    .map(s -> s.getPhase() == direct.reflect.facilitator.facilitation.session.RetroPhase.LOBBY)
                    .orElse(false)) {
                sessionService.startSession(retroId);
            }
            facilitatorPage.navigate(baseUrl + "/retro/" + sessionId, new Page.NavigateOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
            log.debug("✅ Facilitator left lobby after service fallback");
            for (UserPage userPage : participants) {
                try {
                    userPage.page().waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                        null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                    log.debug("✅ Participant {} left lobby after reload", userPage.displayName());
                } catch (Exception e) {
                    log.warn("Participant {} still in lobby after service fallback - reloading page", userPage.displayName());
                    userPage.page().reload(new Page.ReloadOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                    userPage.page().waitForFunction("() => !document.body.textContent.includes('Session Lobby')",
                        null, new Page.WaitForFunctionOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS));
                }
            }
        }

        return sessionId;
    }

    // ==================== NAVIGATION HELPERS ====================

    /**
     * Navigates through steps until finding the desired step type on ALL pages.
     *
     * @param facilitatorPage Facilitator page (clicks Next button)
     * @param stepType Target step type (CATEGORICAL, RATING)
     * @param observerPages Additional pages to verify (participants)
     */
    protected void navigateToStepType(Page facilitatorPage, String stepType, Page... observerPages) {
        log.info("🎯 Navigating to step type: {}", stepType);
        
        // Skip through steps until we find the desired step type
        int maxSteps = 30; // Increased safety limit to account for multiple phases
        for (int i = 0; i < maxSteps; i++) {
            log.debug("Step navigation iteration {}: Current URL: {}", i, facilitatorPage.url());
            
            // Check if we're at the desired step type by looking for specific content
            boolean foundTargetStep = false;
            if (stepType.equals("CATEGORICAL")) {
                // For categorical steps, look for data-column attributes (avoids strict mode violation)
                foundTargetStep = facilitatorPage.locator("[data-column]").count() > 0;
                if (foundTargetStep) {
                    log.info("✅ Found CATEGORICAL step with data-column interface");
                }
            } else if (stepType.equals("RATING")) {
                // For rating steps, look for rating inputs (use count to avoid strict mode violation with multiple radio buttons)
                foundTargetStep = facilitatorPage.locator("input[name='rating']").count() > 0;
                if (foundTargetStep) {
                    log.info("✅ Found RATING step with rating inputs");
                }
            } else {
                // Generic check for step type text
                foundTargetStep = facilitatorPage.locator("text=" + stepType).isVisible();
                if (foundTargetStep) {
                    log.info("✅ Found step type: {}", stepType);
                }
            }
            
            if (foundTargetStep) {
                // Determine expected selector based on step type
                String expectedSelector;
                switch (stepType) {
                    case "CATEGORICAL" -> expectedSelector = "[data-column]";
                    case "RATING" -> expectedSelector = "input[name='rating'][value='1']"; // Check for first radio button to avoid strict mode violation
                    default -> throw new IllegalArgumentException("Unknown step type: " + stepType);
                }

                // Verify all observer pages also show the target step
                if (observerPages.length > 0) {
                    log.info("✅ Facilitator found {} step, verifying {} observer pages...", stepType, observerPages.length);
                    waitForAllPagesElement(expectedSelector, observerPages);
                }
                return;
            }

            if (facilitatorPage.locator("[data-testid='next-step-button']").count() > 0) {
                log.debug("Clicking 'Next' button (iteration {})", i);
                Integer currentIndex = getCurrentStepIndex(facilitatorPage);
                String currentPhase = getCurrentPhase(facilitatorPage);

                if (currentIndex != null && currentPhase != null) {
                    Locator nextBtn = facilitatorPage.locator("[data-testid='next-step-button']");
                    facilitatorPage.waitForResponse(
                        response -> response.url().contains("/next") && response.request().method().equals("POST"),
                        new Page.WaitForResponseOptions().setTimeout(SSE_PROPAGATION_TIMEOUT_MS),
                        () -> nextBtn.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS))
                    );
                    waitForStepChange(currentIndex, currentPhase, SSE_PROPAGATION_TIMEOUT_MS, facilitatorPage);
                } else {
                    clickElement(facilitatorPage, "[data-testid='next-step-button']");
                    facilitatorPage.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
                }
            } else {
                log.error("No 'Next' button found after {} iterations", i);
                break;
            }
        }
        
        // If we got here, we didn't find the target step
        log.error("❌ Failed to find step type '{}' after {} iterations", stepType, maxSteps);
        debugPageState(facilitatorPage, "navigateToStepType failed");
        throw new AssertionError(String.format(
            "Failed to find step type '%s' after %d iterations. Page URL: %s",
            stepType, maxSteps, facilitatorPage.url()));
    }

    // ==================== DEBUGGING HELPERS ====================
    
    // ==================== COMPREHENSIVE ERROR REPORTING ====================

    /**
     * Log progress for multi-step tests.
     * Shows which step is executing (e.g., "[3/24] RATING - Submitting happiness ratings").
     * Integrates with activity breadcrumb trail for complete traceability.
     *
     * @param phase Short phase name (e.g., "SETUP", "RATING", "HISTOGRAM")
     * @param currentStep Current step number (1-based)
     * @param totalSteps Total number of steps in the test
     * @param description Brief description of what this step does
     */
    protected void logTestProgress(String phase, int currentStep, int totalSteps, String description) {
        String progress = String.format("[%d/%d]", currentStep, totalSteps);
        String message = String.format("%-8s %s - %s", progress, phase, description);

        log.info("▶ {}", message);
        recordActivity(message);
    }

    /**
     * Comprehensive error reporter that combines all diagnostic tools.
     * Call this from catch blocks to provide complete failure context.
     *
     * Generates a complete failure report including:
     * 1. Activity breadcrumb trail (sequence of operations)
     * 2. Browser console errors (JavaScript/HTMX errors)
     * 3. Network failures (HTTP errors, connection failures)
     * 4. Page state (URL, title, elements, visible errors)
     * 5. Form state (input field values)
     * 6. Screenshot (visual state at failure)
     *
     * All output prefixed with [TEST_FAILURE_REPORT] for grep-ability.
     *
     * @param page Page where failure occurred
     * @param context Description of what was being attempted (e.g., "Guest Authentication for TestUser")
     * @param error The exception that triggered this report
     */
    protected void reportTestFailure(Page page, String context, Throwable error) {
        log.error("");
        log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════════════════════");
        log.error("[TEST_FAILURE_REPORT] ║");
        log.error("[TEST_FAILURE_REPORT] ║  TEST FAILURE: {}", context);
        log.error("[TEST_FAILURE_REPORT] ║  Error: {}", error.getMessage());
        log.error("[TEST_FAILURE_REPORT] ║");
        log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════════════════════");
        log.error("");

        // 1. Activity Trail - shows sequence of operations leading to failure
        logActivityTrail();
        log.error("");

        // 2. Browser Console Errors - JavaScript/HTMX errors
        logBrowserConsoleErrors();
        if (!consoleErrors.isEmpty()) {
            log.error("");
        }

        // 3. Network Failures - HTTP errors and connection failures
        logNetworkFailures();
        if (!networkFailures.isEmpty()) {
            log.error("");
        }

        logSyncDiagnostics(page, context);
        log.error("");

        // 4. Page State - URL, title, elements, visible errors
        debugPageState(page, context);
        log.error("");

        // 5. Form State - input field values at time of failure
        logFormState(page, context);
        log.error("");

        // 6. Screenshot - visual state at failure
        debugScreenshot(page, "failure", context);

        log.error("");
        log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════════════════════");
        log.error("");
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
                if (element.getClassName().contains("EndToEndTest") && 
                    element.getMethodName().startsWith("should")) {
                    testClass = element.getClassName().substring(element.getClassName().lastIndexOf('.') + 1);
                    testMethod = element.getMethodName();
                    break;
                }
            }
            
            String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));

            // Create descriptive filename
            String filename = String.format("%s_%s_%s_%s.png",
                testClass,
                testMethod,
                step.replaceAll("[^a-zA-Z0-9]", "_"),
                timestamp);

            Path screenshotDir = Paths.get("target", "debug-screenshots");
            Files.createDirectories(screenshotDir);
            
            // Use timeout to prevent hanging on error pages (chrome-error://, network errors, etc.)
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(screenshotDir.resolve(filename))
                .setFullPage(true)
                .setTimeout(SHORT_TIMEOUT_MS));

            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ SCREENSHOT CAPTURED");
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ File: {}", filename);
            log.error("[TEST_FAILURE_REPORT] ║ Test: {}.{}", testClass, testMethod);
            log.error("[TEST_FAILURE_REPORT] ║ Step: {}", step);
            log.error("[TEST_FAILURE_REPORT] ║ URL: {}", page.url());
            // page.title() can hang on error pages, so wrap in try-catch
            try {
                log.error("[TEST_FAILURE_REPORT] ║ Title: {}", page.title());
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Title: [unavailable - {}]", e.getMessage());
            }
            if (additionalInfo != null) {
                log.error("[TEST_FAILURE_REPORT] ║ Info: {}", additionalInfo);
            }
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");

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
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ PAGE STATE - {}", context);
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ URL: {}", page.url());

            // page.title() can hang on error pages, so wrap in try-catch
            try {
                log.error("[TEST_FAILURE_REPORT] ║ Title: {}", page.title());
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Title: [unavailable - {}]", e.getMessage());
            }

            // Count key elements with timeout to prevent hanging on error pages
            try {
                int formCount = page.locator("form").count();
                int buttonCount = page.locator("button").count();
                int inputCount = page.locator("input").count();
                log.error("[TEST_FAILURE_REPORT] ║ Elements: {} forms, {} buttons, {} inputs", formCount, buttonCount, inputCount);
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Elements: [count failed - {}]", e.getMessage());
            }

            // Check for visible error indicators using JavaScript
            // Try-catch prevents indefinite hangs by using Playwright's default timeout
            try {
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
                    log.error("[TEST_FAILURE_REPORT] ║ ⚠️ ERROR detected: {}", errorResult);
                }
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Error detection: [failed - {}]", e.getMessage());
            }

            // Check authentication state with timeout
            try {
                boolean isLoginPage = page.url().contains("/login") ||
                    page.locator("text=Sign in with GitHub").count() > 0;
                boolean hasWelcome = page.locator("text=Welcome").count() > 0;
                log.error("[TEST_FAILURE_REPORT] ║ Auth state: login page={}, has welcome={}", isLoginPage, hasWelcome);
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Auth state: [check failed - {}]", e.getMessage());
            }

            // Sample page content for debugging with timeout
            try {
                String bodyText = page.textContent("body", new Page.TextContentOptions().setTimeout(SHORT_TIMEOUT_MS));
                if (bodyText != null) {
                    if (bodyText.length() > 200) {
                        bodyText = bodyText.substring(0, 200) + "...";
                    }
                    log.error("[TEST_FAILURE_REPORT] ║ Body preview: {}", bodyText.replaceAll("\\s+", " "));
                }
            } catch (Exception e) {
                log.error("[TEST_FAILURE_REPORT] ║ Body preview: [unavailable - {}]", e.getMessage());
            }

            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.warn("Failed to log debug page state: {}", e.getMessage());
        }
    }

    private void logSyncDiagnostics(Page page, String context) {
        try {
            Object result = page.evaluate("""
                () => {
                    const retro = document.querySelector('[data-testid="retro-content"]');
                    const diagnostics = window.__omoSyncDiagnostics || {};
                    const signaledVersion = typeof diagnostics.signaledVersion === 'number' ? diagnostics.signaledVersion : null;
                    const appliedVersion = typeof diagnostics.appliedVersion === 'number' ? diagnostics.appliedVersion : null;
                    const lastRefetch = diagnostics.lastRefetch ?? null;
                    const lastSignal = diagnostics.lastSignal ?? null;
                    const latestAuthoritativeVersion = typeof lastRefetch?.syncVersion === 'number' ? lastRefetch.syncVersion : null;

                    return JSON.stringify({
                      context,
                      dom: {
                        phase: retro?.getAttribute('data-phase') ?? null,
                        stepIndex: retro?.getAttribute('data-step-index') ?? null,
                        sseConnected: retro?.getAttribute('data-sse-connected') ?? null
                      },
                      transport: {
                        connectionState: diagnostics.connectionState ?? null,
                        navigatorOnLine: diagnostics.navigatorOnLine ?? null,
                        openCount: diagnostics.openCount ?? null,
                        errorCount: diagnostics.errorCount ?? null,
                        eventSourceUrl: diagnostics.eventSourceUrl ?? null,
                        lastConnectionEvent: diagnostics.lastConnectionEvent ?? null
                      },
                      sync: {
                        signaledVersion,
                        appliedVersion,
                        versionGap: signaledVersion != null && appliedVersion != null
                          ? signaledVersion - appliedVersion
                          : null,
                        lastSignal,
                        lastRefetch,
                        recentSignals: diagnostics.recentSignals ?? [],
                        recentRefetches: diagnostics.recentRefetches ?? []
                      },
                      classification: {
                        missedInvalidationCandidate: latestAuthoritativeVersion != null && (signaledVersion == null || latestAuthoritativeVersion > signaledVersion),
                        failedAuthoritativeRefetchCandidate: Boolean(lastRefetch && (lastRefetch.ok === false || lastRefetch.error)),
                        failedConvergenceAfterRefetchCandidate: Boolean(lastRefetch && lastRefetch.ok === true && signaledVersion != null && appliedVersion != null && signaledVersion > appliedVersion)
                      }
                    }, null, 2);
                }
                """);

            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ VERSIONED SYNC DIAGNOSTICS - {}", context);
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");

            String payload = result instanceof String ? (String) result : String.valueOf(result);
            for (String line : payload.split("\\R")) {
                log.error("[TEST_FAILURE_REPORT] ║ {}", line);
            }

            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            log.error("[TEST_FAILURE_REPORT] ╔═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ VERSIONED SYNC DIAGNOSTICS - {}", context);
            log.error("[TEST_FAILURE_REPORT] ╠═══════════════════════════════════════════════════════════");
            log.error("[TEST_FAILURE_REPORT] ║ [unavailable - {}]", e.getMessage());
            log.error("[TEST_FAILURE_REPORT] ╚═══════════════════════════════════════════════════════════");
        }
    }

    // ==================== UTILITY HELPERS ====================

    // ==================== CORE PLAYWRIGHT HELPERS ====================

    /**
     * Waits for an element to appear with default timeout.
     * Use this before interacting with elements.
     */
    protected void waitForElement(Page page, String selector) {
        waitForElement(page, selector, DEFAULT_TIMEOUT_MS);
    }

    protected void waitForElement(Page page, String selector, int timeoutMs) {
        recordActivity("waitForElement: " + selector + " (timeout: " + timeoutMs + "ms)");
        try {
            log.debug("Waiting for element '{}' (timeout: {}ms)", selector, timeoutMs);
            page.locator(selector).first().waitFor(
                new Locator.WaitForOptions().setTimeout(timeoutMs));
            log.debug("✅ Element '{}' found", selector);
            recordActivity("✓ Element found: " + selector);
        } catch (Exception e) {
            log.error("❌ Element '{}' not found after {}ms on page {}",
                selector, timeoutMs, page.url());
            debugPageState(page, "waitForElement timeout");
            logActivityTrail(); // Show what led to this failure
            throw new AssertionError(String.format(
                "Element '%s' not found after %dms. Page URL: %s",
                selector, timeoutMs, page.url()), e);
        }
    }

    /**
     * Fills an input element, waiting for it to appear first.
     * Encapsulates wait + fill + error handling.
     */
    protected void fillElement(Page page, String selector, String value) {
        fillElement(page, selector, value, DEFAULT_TIMEOUT_MS);
    }

    protected void fillElement(Page page, String selector, String value, int timeoutMs) {
        recordActivity("fillElement: " + selector + " = '" + value + "'");
        try {
            log.debug("Filling element '{}' with value '{}'", selector, value);
            waitForElement(page, selector, timeoutMs);
            page.fill(selector, value);
            log.debug("✅ Element '{}' filled successfully", selector);
            recordActivity("✓ Element filled: " + selector);
        } catch (Exception e) {
            log.error("❌ Failed to fill element '{}'", selector);
            logActivityTrail(); // Show what led to this failure
            throw new AssertionError(String.format(
                "Failed to fill element '%s' with value '%s'", selector, value), e);
        }
    }

    /**
     * Clicks an element, waiting for it to appear first.
     * Encapsulates wait + click + error handling.
     */
    protected void clickElement(Page page, String selector) {
        clickElement(page, selector, DEFAULT_TIMEOUT_MS);
    }

    protected void clickElement(Page page, String selector, int timeoutMs) {
        recordActivity("clickElement: " + selector);
        try {
            log.debug("Clicking element '{}'", selector);
            waitForElement(page, selector, timeoutMs);
            page.click(selector);
            log.debug("✅ Element '{}' clicked successfully", selector);
            recordActivity("✓ Element clicked: " + selector);
        } catch (Exception e) {
            log.error("❌ Failed to click element '{}'", selector);
            logActivityTrail(); // Show what led to this failure
            throw new AssertionError(String.format(
                "Failed to click element '%s'", selector), e);
        }
    }

    // ==================== SSE SYNCHRONIZATION HELPERS ====================

    // ==================== MULTI-PAGE COORDINATION HELPERS ====================

    /**
     * Waits for an element to appear on ALL pages.
     * Use this to ensure all participants see the same content after SSE events.
     *
     * @param selector Element selector to wait for
     * @param pages All pages that should show the element
     */
    protected void waitForAllPagesElement(String selector, Page... pages) {
        waitForAllPagesElement(selector, DEFAULT_TIMEOUT_MS, pages);
    }

    protected void waitForAllPagesElement(String selector, int timeoutMs, Page... pages) {
        log.debug("Waiting for all {} pages to show element '{}'", pages.length, selector);

        for (int i = 0; i < pages.length; i++) {
            Page page = pages[i];
            try {
                waitForElement(page, selector, timeoutMs);
                log.debug("✅ Page {}/{} shows element '{}'", i + 1, pages.length, selector);
            } catch (AssertionError e) {
                log.error("❌ Page {}/{} failed to show element '{}'", i + 1, pages.length, selector);
                throw new AssertionError(String.format(
                    "Page %d/%d failed to show element '%s' after %dms",
                    i + 1, pages.length, selector, timeoutMs), e);
            }
        }

        log.debug("✅ All {} pages show element '{}'", pages.length, selector);
    }

    /**
     * Waits for all pages to transition away from old content to new content.
     * Use this for major transitions like lobby → retro.
     *
     * @param oldContent Text that should disappear (e.g., "Session Lobby")
     * @param newSelector Element that should appear (e.g., "button:has-text('Next')")
     * @param pages All pages that should transition
     */
    protected void waitForAllPagesTransition(String oldContent, String newSelector, Page... pages) {
        waitForAllPagesTransition(oldContent, newSelector, DEFAULT_TIMEOUT_MS, pages);
    }

    protected void waitForAllPagesTransition(String oldContent, String newSelector, int timeoutMs, Page... pages) {
        log.debug("Waiting for all {} pages to transition from '{}' to '{}'",
            pages.length, oldContent, newSelector);

        for (int i = 0; i < pages.length; i++) {
            Page page = pages[i];
            try {
                // Wait for old content to disappear
                page.waitForFunction(
                    "(oldText) => !document.body.textContent.includes(oldText)",
                    oldContent,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));

                // Wait for new element to appear
                waitForElement(page, newSelector, timeoutMs);

                log.debug("✅ Page {}/{} transitioned successfully", i + 1, pages.length);
            } catch (Exception e) {
                log.error("❌ Page {}/{} failed to transition", i + 1, pages.length);
                debugPageState(page, "transition timeout");
                throw new AssertionError(String.format(
                    "Page %d/%d failed to transition after %dms",
                    i + 1, pages.length, timeoutMs), e);
            }
        }

        log.debug("✅ All {} pages transitioned successfully", pages.length);
    }

    // ==================== PARTICIPANT VERIFICATION HELPERS ====================

    /**
     * Waits for participant list to contain exactly the expected participants.
     *
     * Use this after participant join/leave actions to verify the participant list
     * updated correctly via SSE events.
     *
     * @param page Page to check
     * @param expectedParticipants Exact list of expected participant names (order doesn't matter)
     */
    protected void waitForParticipantList(Page page, String... expectedParticipants) {
        waitForParticipantList(page, SSE_PROPAGATION_TIMEOUT_MS, expectedParticipants);
    }

    protected void waitForParticipantList(Page page, int timeoutMs, String... expectedParticipants) {
        try {
            log.debug("Waiting for participant list: {}", Arrays.toString(expectedParticipants));

            // Wait for participant list to match expected participants
            page.waitForFunction(
                "(expectedParticipants) => {" +
                "  const participantElements = document.querySelectorAll('ul#participants-list li');" +
                "  const actualParticipants = Array.from(participantElements)" +
                "    .map(li => (li.querySelector('span:first-child')?.textContent || '').trim())" +
                "    .filter(Boolean)" +
                "    .sort();" +
                "  const expected = expectedParticipants.slice().sort();" +
                "  return JSON.stringify(actualParticipants) === JSON.stringify(expected);" +
                "}",
                expectedParticipants,
                new Page.WaitForFunctionOptions().setTimeout(timeoutMs));

            log.debug("✅ Participant list matches expected: {}", Arrays.toString(expectedParticipants));

        } catch (Exception e) {
            log.error("❌ Participant list verification failed");

            // Debug: show actual vs expected
            try {
                List<String> actualParticipants = getParticipantNames(page);
                log.error("Expected participants: {}", Arrays.toString(expectedParticipants));
                log.error("Actual participants: {}", actualParticipants);
            } catch (Exception debugError) {
                log.error("Failed to get actual participant names: {}", debugError.getMessage());
            }

            throw new AssertionError(String.format(
                "Participant list verification failed. Expected: %s. Check logs for actual list.",
                Arrays.toString(expectedParticipants)), e);
        }
    }

    /**
     * Gets current participant names from the participant list.
     * Helper method for debugging test failures.
     *
     * @param page Page to extract participant names from
     * @return List of participant display names
     */
    protected List<String> getParticipantNames(Page page) {
        // Use Playwright's native locator API instead of JS evaluation
        return page.locator("ul#participants-list li span:first-child").allTextContents()
            .stream()
            .map(String::trim)
            .collect(Collectors.toList());
    }

    protected void waitForSseConnection(Page page, UUID retroId) {
        recordActivity("waitForSseConnection: " + retroId);
        page.waitForSelector(
            "[data-testid='retro-content'][data-sse-connected='true']",
            new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(DEFAULT_TIMEOUT_MS)
        );
        log.debug("SSE connection established for retro: {} (data-sse-connected='true')", retroId);
        recordActivity("SSE connection established: " + retroId);
    }


    protected void navigateToHome(Page page) {
        recordActivity("navigateToHome");
        page.navigate(baseUrl + "/");
        page.waitForSelector("input[name='sessionName']",
            new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        log.debug("Navigated to home page: {}", page.url());
        recordActivity("Home page loaded");
    }

    /**
     * Waits for ALL pages to show exactly the expected participants.
     * Use this to ensure all participants see consistent participant list after SSE events.
     *
     * @param expectedParticipants Exact list of expected participant names
     * @param pages All pages that should show the same participant list
     */
    protected void waitForAllPagesParticipantList(String[] expectedParticipants, Page... pages) {
        waitForAllPagesParticipantList(expectedParticipants, SSE_PROPAGATION_TIMEOUT_MS, pages);
    }

    protected void waitForAllPagesParticipantList(String[] expectedParticipants, int timeoutMs, Page... pages) {
        log.debug("Waiting for all {} pages to show participants: {}",
            pages.length, Arrays.toString(expectedParticipants));

        for (int i = 0; i < pages.length; i++) {
            Page page = pages[i];
            try {
                waitForParticipantList(page, timeoutMs, expectedParticipants);
                log.debug("✅ Page {}/{} shows correct participant list", i + 1, pages.length);
            } catch (AssertionError e) {
                log.error("❌ Page {}/{} has wrong participant list", i + 1, pages.length);
                throw new AssertionError(String.format(
                    "Page %d/%d has wrong participant list. Expected: %s",
                    i + 1, pages.length, Arrays.toString(expectedParticipants)), e);
            }
        }

        log.debug("✅ All {} pages show correct participant list", pages.length);
    }

    // ==================== DOM-BASED STEP TRACKING ====================

    protected Integer getCurrentStepIndex(Page page) {
        Locator stepContainer = page.locator("[data-step-index]");
        if (stepContainer.count() == 0) {
            return null;
        }
        String indexStr = stepContainer.getAttribute("data-step-index");
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected String getCurrentPhase(Page page) {
        Locator container = page.locator("[data-phase]");
        if (container.count() == 0) {
            return null;
        }
        return container.getAttribute("data-phase");
    }

    // ==================== HELPER CLASSES ====================

    public record UserPage(Page page, String displayName) {}
}
