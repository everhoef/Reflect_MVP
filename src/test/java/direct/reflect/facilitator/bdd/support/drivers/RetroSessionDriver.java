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
