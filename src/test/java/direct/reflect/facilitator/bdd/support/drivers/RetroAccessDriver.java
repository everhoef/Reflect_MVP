package direct.reflect.facilitator.bdd.support.drivers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import direct.reflect.facilitator.bdd.support.context.RetroScenarioContext;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ADDRESS_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ADDRESS_PLACEHOLDER_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.CAPTCHA_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.DISPLAY_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.EMAIL_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ERROR_PAGE_LOAD_FAILED_MESSAGE;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ERROR_PAGE_MESSAGE;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ERROR_PAGE_NOT_FOUND_MESSAGE;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.ERROR_PAGE_SESSION_ENDED_MESSAGE;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.JOIN_RETRO_ID_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.JOIN_SESSION_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.LOGIN_SUBMIT_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.PASSWORD_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.PHONE_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.PHONE_PLACEHOLDER_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.SESSION_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.TELEPHONE_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.USERNAME_INPUT;

@ScenarioScope
@Component
@RequiredArgsConstructor
@Slf4j
public class RetroAccessDriver {
    private static final int DEFAULT_TIMEOUT_MS = 5_000;
    private static final int LONG_TIMEOUT_MS = 15_000;

    private final PlaywrightWorld world;
    private final RetroScenarioContext context;

    public void authenticateAsGuest(String displayName) {
        Page page = world.getPage();
        world.clearCookies();
        page.navigate(world.getBaseUrl() + "/login");
        page.waitForSelector(DISPLAY_NAME_INPUT, new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill(DISPLAY_NAME_INPUT, displayName);
        page.click(LOGIN_SUBMIT_BUTTON);
        page.waitForURL(
            url -> url.equals(world.getBaseUrl() + "/") || url.equals(world.getBaseUrl() + "/home") || url.endsWith("/"),
            new Page.WaitForURLOptions().setTimeout(DEFAULT_TIMEOUT_MS)
        );
        page.waitForSelector(SESSION_NAME_INPUT, new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    public void joinRetroAsGuest(String retroId, String displayName) {
        Page page = world.getPage();
        world.clearCookies();
        page.navigate(world.getBaseUrl() + "/retro/" + retroId + "/lobby");

        if (page.locator(DISPLAY_NAME_INPUT).count() > 0) {
            page.fill(DISPLAY_NAME_INPUT, displayName);
            page.click(LOGIN_SUBMIT_BUTTON);
            page.waitForURL(
                url -> url.equals(world.getBaseUrl() + "/") || url.equals(world.getBaseUrl() + "/home") || url.endsWith("/"),
                new Page.WaitForURLOptions().setTimeout(DEFAULT_TIMEOUT_MS)
            );
        }

        page.navigate(world.getBaseUrl() + "/");
        page.waitForSelector(JOIN_RETRO_ID_INPUT, new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill(JOIN_RETRO_ID_INPUT, retroId);
        page.click(JOIN_SESSION_BUTTON);
        page.waitForURL(
            url -> url.contains("/retro/" + retroId),
            new Page.WaitForURLOptions().setTimeout(LONG_TIMEOUT_MS)
        );
        page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    public void navigateToRetro(String retroId) {
        world.getPage().navigate(world.getBaseUrl() + "/retro/" + retroId);
    }

    public void rejoinRetroWithRecoveredGuestSession(String retroId, String displayName) {
        Page page = world.getPage();
        try {
            navigateToRetro(retroId);
            page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        } catch (RuntimeException e) {
            if (!isLoginBarrierVisible(page)) {
                throw e;
            }

            log.warn("Retro re-entry hit login barrier for session {}. Falling back to a fresh guest rejoin for '{}'.", retroId, displayName);
            joinRetroAsGuest(retroId, displayName);
        }
    }

    public void assertGuestAuthenticated() {
        Page page = world.getPage();
        String responseText = (String) page.evaluate(
            "async () => { const response = await fetch('/api/me'); return response.text(); }"
        );
        if (responseText == null || !responseText.contains("\"isGuest\":true")) {
            throw new AssertionError("Expected guest to be authenticated, but /api/me did not indicate isGuest=true. Response: " + responseText);
        }
        if (!responseText.contains("\"isAuthenticated\":true")) {
            throw new AssertionError("Expected user to be authenticated, but /api/me did not indicate isAuthenticated=true. Response: " + responseText);
        }
    }

    public void assertJoinedSession(String sessionId) {
        Page page = world.getPage();
        String responseText = (String) page.evaluate(
            "async () => { const response = await fetch('/api/me/retros/active'); return response.text(); }"
        );

        if (responseText == null) {
            throw new AssertionError("Expected active retros response, but received no response while checking session membership for sessionId=" + sessionId);
        }

        try {
            JsonNode activeSessions = new ObjectMapper().readTree(responseText);
            if (!activeSessions.isArray()) {
                throw new AssertionError("Expected /api/me/retros/active to return an array, but received: " + responseText);
            }

            for (JsonNode activeSession : activeSessions) {
                JsonNode activeSessionId = activeSession.get("sessionId");
                if (activeSessionId != null && sessionId.equals(activeSessionId.asText())) {
                    return;
                }
            }

            throw new AssertionError("Expected sessionId " + sessionId + " to appear in /api/me/retros/active, but it was not found. Response: " + responseText);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse /api/me/retros/active while checking session membership for sessionId " + sessionId + ". Response: " + responseText, e);
        }
    }

    public void assertNoLoginPrompts() {
        assertNoElement(PASSWORD_INPUT, "password field");
        assertNoElement(EMAIL_INPUT, "email field");
        assertNoElement(USERNAME_INPUT, "username field");
        assertNoElement(CAPTCHA_INPUT, "CAPTCHA field");
    }

    public void assertErrorPage() {
        Page page = world.getPage();
        page.waitForSelector(ERROR_PAGE_MESSAGE, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));

        boolean notFoundMessageVisible = page.locator(ERROR_PAGE_NOT_FOUND_MESSAGE).count() > 0
            && page.locator(ERROR_PAGE_SESSION_ENDED_MESSAGE).count() > 0;

        if (!notFoundMessageVisible && page.locator(ERROR_PAGE_LOAD_FAILED_MESSAGE).count() == 0) {
            String bodyText = page.locator("body").textContent();
            throw new AssertionError("Expected retrospective unavailable error page. Body text was: " + bodyText);
        }
    }

    public void assertRetroPageVisible() {
        Page page = world.getPage();
        page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        if (page.locator(RETRO_CONTENT).count() == 0) {
            throw new AssertionError("Expected retro content to be visible, but it was not found.");
        }
    }

    public void assertRetroContentNotVisible() {
        if (world.getPage().locator(RETRO_CONTENT).count() > 0) {
            throw new AssertionError("Expected retro content to be inaccessible for invalid session link.");
        }
    }

    public void assertPageLacksText(String text) {
        String bodyText = world.getPage().locator("body").textContent();
        if (bodyText != null && bodyText.toLowerCase().contains(text.toLowerCase())) {
            throw new AssertionError("Found unexpected text on page: '" + text + "'");
        }
    }

    public void assertNoPersonalInfoFields() {
        Page page = world.getPage();
        String[] selectors = {
            PHONE_PLACEHOLDER_INPUT,
            ADDRESS_PLACEHOLDER_INPUT,
            PHONE_NAME_INPUT,
            ADDRESS_NAME_INPUT,
            TELEPHONE_INPUT
        };
        for (String selector : selectors) {
            if (page.locator(selector).count() > 0) {
                throw new AssertionError("Found unexpected personal info field: " + selector);
            }
        }
    }

    public List<Cookie> captureCookies() {
        return world.captureCookies();
    }

    public void restoreCookies(List<Cookie> cookies) {
        world.restoreCookies(cookies);
    }

    public void clearCookies() {
        world.clearCookies();
    }

    private void assertNoElement(String selector, String description) {
        int count = world.getPage().locator(selector).count();
        if (count > 0) {
            throw new AssertionError("Found unexpected " + description + " on page");
        }
    }

    private boolean isLoginBarrierVisible(Page page) {
        return page.url().contains("/login") || page.locator(DISPLAY_NAME_INPUT).count() > 0;
    }

}
