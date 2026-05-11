package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import direct.reflect.facilitator.bdd.support.context.RetroScenarioContext;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.CREATE_SESSION_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.COLUMN_BOARD_ITEM;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.DISPLAY_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.LOGIN_SUBMIT_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.NEXT_STEP_BUTTON;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.SESSION_NAME_INPUT;
import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.START_RETRO_BUTTON;

@ScenarioScope
@Component
@RequiredArgsConstructor
@Slf4j
public class RetroSessionDriver {
    private static final int DEFAULT_TIMEOUT_MS = 5_000;
    private static final int LONG_TIMEOUT_MS = 15_000;

    static final Map<Integer, String> PHASE_ENUMS = Map.of(
        1, "SET_THE_STAGE",
        2, "GATHER_DATA",
        3, "GENERATE_INSIGHTS",
        4, "DECIDE_ACTIONS",
        5, "CLOSE_RETRO"
    );

    private final PlaywrightWorld world;
    private final RetroScenarioContext context;
    private final SyncDriver syncDriver;

    public void ensureActiveRetrospectiveAtPhase(int targetPhase) {
        if (!context.isRetroReady()) {
            syncDriver.waitForServerReady();
            authenticateAsGuest("BDD Visual Clue Tester");
            createSession("Visual Clue Stage BDD Pilot");
            startSession();
            context.setRetroReady(true);
            context.setCurrentPhaseNumber(detectCurrentPhaseNumber());
            log.debug("Created retro session {} and detected initial phase {}", context.getSessionId(), context.getCurrentPhaseNumber());
        }

        if (context.getCurrentPhaseNumber() < targetPhase) {
            advanceToPhase(targetPhase);
        }

        if (context.getCurrentPhaseNumber() != targetPhase) {
            throw new AssertionError("Could not align the retrospective to phase " + targetPhase + " using the current progress-indicator pilot helpers.");
        }

        syncDriver.assertRetroContentLoaded();
    }

    public void authenticateAsGuest(String displayName) {
        Page page = world.getPage();
        page.context().clearCookies();
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
        page.context().clearCookies();
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
        page.waitForSelector("input[name='retroId']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill("input[name='retroId']", retroId);
        page.click("button:has-text('Join Session')");
        page.waitForURL(
            url -> url.contains("/retro/" + retroId),
            new Page.WaitForURLOptions().setTimeout(LONG_TIMEOUT_MS)
        );
        page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    public void advanceOneStep() {
        Page page = world.getPage();
        Locator nextButton = page.locator(NEXT_STEP_BUTTON);
        if (nextButton.count() == 0) {
            throw new AssertionError("Cannot advance because the facilitator next-step button is not visible.");
        }
        SyncDriver.ShellSnapshot previousSnapshot = syncDriver.captureShellSnapshot();
        nextButton.click();
        syncDriver.waitForPhaseOrStepChange(previousSnapshot);
        context.setCurrentPhaseNumber(detectCurrentPhaseNumber());
        context.setLastAdvanceTriggered(true);
    }

    public java.util.List<com.microsoft.playwright.options.Cookie> captureCookies() {
        return world.getPage().context().cookies();
    }

    public void restoreCookies(java.util.List<com.microsoft.playwright.options.Cookie> cookies) {
        world.getPage().context().clearCookies();
        world.getPage().context().addCookies(cookies);
    }

    public void clearCookies() {
        world.getPage().context().clearCookies();
    }

    public void reloadAndWait() {
        Page page = world.getPage();
        page.reload(new Page.ReloadOptions().setTimeout(LONG_TIMEOUT_MS));
        syncDriver.assertRetroContentLoaded();
    }

    public Page getPage() {
        return world.getPage();
    }

    public String getBaseUrl() {
        return world.getBaseUrl();
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

    public void assertNoLoginPrompts() {
        Page page = world.getPage();
        assertNoElement("input[type='password']", "password field");
        assertNoElement("input[type='email']", "email field");
        assertNoElement("input[name='username']", "username field");
        assertNoElement("input[placeholder*='CAPTCHA' i]", "CAPTCHA field");
    }

    public void assertErrorPage() {
        Page page = world.getPage();
        page.waitForFunction(
            "() => { const text = document.body?.textContent ?? ''; return text.includes('Retrospective not found') || text.includes('Could not load retrospective'); }",
            null,
            new Page.WaitForFunctionOptions().setTimeout(LONG_TIMEOUT_MS)
        );

        boolean notFoundMessageVisible = page.locator("p:text-is('Retrospective not found')").count() > 0
            && page.locator("p:text-is('The session may have ended or the link is invalid.')").count() > 0;
        boolean genericErrorVisible = page.locator("p:text-is('Could not load retrospective')").count() > 0;

        if (!notFoundMessageVisible && !genericErrorVisible) {
            String bodyText = page.locator("body").textContent();
            throw new AssertionError(
                "Expected retrospective unavailable error page. Body text was: " + bodyText
            );
        }
    }

    public void assertRetroPageVisible() {
        Page page = world.getPage();
        page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        int count = page.locator(RETRO_CONTENT).count();
        if (count == 0) {
            throw new AssertionError("Expected retro content to be visible, but it was not found.");
        }
    }

    public String createRetroAndGetLobbyUrl() {
        if (!context.isRetroReady()) {
            syncDriver.waitForServerReady();
            authenticateAsGuest("BDD Anonymous Login Tester");
            createSession("Anonymous Login BDD Pilot");
            startSession();
            context.setRetroReady(true);
            context.setCurrentPhaseNumber(detectCurrentPhaseNumber());
            log.debug("Created retro session {} for anonymous login tests", context.getSessionId());
        }
        return world.getBaseUrl() + "/retro/" + context.getSessionId() + "/lobby";
    }

    private void assertNoElement(String selector, String description) {
        int count = world.getPage().locator(selector).count();
        if (count > 0) {
            throw new AssertionError("Found unexpected " + description + " on page");
        }
    }

    public String createSession(String sessionName) {
        Page page = world.getPage();
        page.navigate(world.getBaseUrl() + "/");
        page.waitForSelector(SESSION_NAME_INPUT, new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill(SESSION_NAME_INPUT, sessionName);
        page.click(CREATE_SESSION_BUTTON);
        page.waitForURL(url -> url.contains("/retro/"), new Page.WaitForURLOptions().setTimeout(LONG_TIMEOUT_MS));
        String sessionId = page.url().substring(page.url().lastIndexOf('/') + 1);
        context.setSessionId(sessionId);
        return sessionId;
    }

    public void startSession() {
        Page page = world.getPage();
        page.waitForSelector(START_RETRO_BUTTON, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        page.click(START_RETRO_BUTTON);
        page.waitForSelector(RETRO_CONTENT, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        page.waitForSelector(NEXT_STEP_BUTTON, new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    public void advanceToPhase(int targetPhase) {
        Page page = world.getPage();
        int safetyCounter = 0;

        while (context.getCurrentPhaseNumber() < targetPhase && safetyCounter <= 40) {
            Locator nextButton = page.locator(NEXT_STEP_BUTTON);
            if (nextButton.count() == 0) {
                throw new AssertionError("Cannot advance to target phase because the facilitator next-step button is not visible.");
            }

            SyncDriver.ShellSnapshot previousSnapshot = syncDriver.captureShellSnapshot();
            nextButton.click();
            syncDriver.waitForPhaseOrStepChange(previousSnapshot);
            context.setCurrentPhaseNumber(detectCurrentPhaseNumber());
            context.setLastAdvanceTriggered(true);
            safetyCounter++;
        }

        if (context.getCurrentPhaseNumber() < targetPhase) {
            throw new AssertionError("Retrospective did not advance to requested phase " + targetPhase + " within the safety limit.");
        }
    }

    public String findFirstColumnId() {
        Locator columns = world.getPage().locator(COLUMN_BOARD_ITEM);
        if (columns.count() == 0) {
            throw new AssertionError("No columns found on the page.");
        }
        String testId = columns.first().getAttribute("data-testid");
        return testId.replace("column-", "");
    }

    public void advanceFacilitatorUntilColumnBoardVisible() {
        Page page = world.getPage();
        for (int attempt = 0; attempt < 12; attempt++) {
            if (page.locator(COLUMN_BOARD_ITEM).count() > 0) {
                return;
            }
            if (page.locator(NEXT_STEP_BUTTON).count() == 0) {
                break;
            }
            advanceOneStep();
        }
        throw new AssertionError("Expected a multi-column board step with semantic column selectors, but none was found.");
    }

    public void waitForColumnBoardVisible() {
        world.getPage().waitForSelector(COLUMN_BOARD_ITEM,
            new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    public void addNoteAndWait(String columnId, String noteContent) {
        Page page = world.getPage();
        page.fill("[data-testid='note-input-" + columnId + "']", noteContent);
        page.click("[data-testid='submit-note-" + columnId + "']");
        page.waitForSelector("text=" + noteContent,
            new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    public void assertNoteVisible(String noteContent) {
        Locator note = world.getPage().locator("text=" + noteContent);
        if (note.count() == 0) {
            throw new AssertionError("Expected note to be visible: " + noteContent);
        }
    }

    public void assertNoteShowsAuthor(String displayName) {
        Page page = world.getPage();
        Locator authorBadge = page.locator(RETRO_CONTENT).locator("text=" + displayName);
        if (authorBadge.count() == 0) {
            String bodyText = page.locator(RETRO_CONTENT).textContent();
            throw new AssertionError(
                "Expected note to show author '" + displayName + "'. Retro content text: " + bodyText);
        }
    }

    public void assertRetroContentNotVisible() {
        if (world.getPage().locator(RETRO_CONTENT).count() > 0) {
            throw new AssertionError("Expected retro content to be inaccessible for invalid session link.");
        }
    }

    public void assertOwnNoteVisible(String noteContent) {
        Locator ownNote = world.getPage()
            .locator(RETRO_CONTENT + " p[title='Click to edit']")
            .filter(new Locator.FilterOptions().setHasText(noteContent));
        if (ownNote.count() == 0) {
            throw new AssertionError("Expected to find own contribution with edit affordance.");
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
            "input[placeholder*='phone' i]",
            "input[placeholder*='address' i]",
            "input[name='phone']",
            "input[name='address']",
            "input[type='tel']"
        };
        for (String selector : selectors) {
            if (page.locator(selector).count() > 0) {
                throw new AssertionError("Found unexpected personal info field: " + selector);
            }
        }
    }

    public void navigateToRetro(String retroId) {
        world.getPage().navigate(world.getBaseUrl() + "/retro/" + retroId);
    }

    public int detectCurrentPhaseNumber() {
        String phaseEnum = world.getPage().locator(RETRO_CONTENT).getAttribute("data-phase");
        for (Map.Entry<Integer, String> entry : PHASE_ENUMS.entrySet()) {
            if (entry.getValue().equals(phaseEnum)) {
                return entry.getKey();
            }
        }

        throw new IllegalArgumentException("Unknown retro phase enum: " + phaseEnum);
    }

    private String currentPhaseEnum() {
        String currentPhase = PHASE_ENUMS.get(context.getCurrentPhaseNumber());
        if (currentPhase != null) {
            return currentPhase;
        }

        int detectedPhase = detectCurrentPhaseNumber();
        return PHASE_ENUMS.get(detectedPhase);
    }
}
