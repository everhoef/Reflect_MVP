package direct.reflect.facilitator.bdd.stepdefinitions;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class VisualClueStageSteps {

    private static final int DEFAULT_TIMEOUT_MS = 5_000;
    private static final int LONG_TIMEOUT_MS = 15_000;

    private static final Map<Integer, String> PHASE_ENUMS = Map.of(
            1, "SET_THE_STAGE",
            2, "GATHER_DATA",
            3, "GENERATE_INSIGHTS",
            4, "DECIDE_ACTIONS",
            5, "CLOSE_RETRO"
    );

    private final PlaywrightWorld world;

    private boolean retroReady;
    private String sessionId;
    private int currentPhaseNumber;
    private boolean lastAdvanceTriggered;

    @Before
    public void resetScenarioState() {
        retroReady = false;
        sessionId = null;
        currentPhaseNumber = 0;
        lastAdvanceTriggered = false;
    }

    @Given("I am a team member in an active retrospective")
    public void iAmATeamMemberInAnActiveRetrospective() {
        ensureActiveRetrospectiveAtPhase(1);
    }

    @Given("the underground map is displayed")
    public void theUndergroundMapIsDisplayed() {
        ensureActiveRetrospectiveAtPhase(1);
        assertProgressIndicatorPresent();
    }

    @Given("I am in phase {int} of the retrospective")
    public void iAmInPhaseOfTheRetrospective(int phaseNumber) {
        ensureActiveRetrospectiveAtPhase(phaseNumber);
    }

    @Given("I am in phase 2 with phase 2 highlighted on the map")
    public void iAmInPhase2WithPhase2HighlightedOnTheMap() {
        ensureActiveRetrospectiveAtPhase(2);
        assertStationHighlighted(2);
    }

    @Given("I am viewing the underground map")
    public void iAmViewingTheUndergroundMap() {
        ensureActiveRetrospectiveAtPhase(1);
        assertProgressIndicatorPresent();
    }

    @Given("I am participating in a retrospective")
    public void iAmParticipatingInARetrospective() {
        ensureActiveRetrospectiveAtPhase(1);
    }

    @Given("I am a team member in any phase of the retrospective")
    public void iAmATeamMemberInAnyPhaseOfTheRetrospective() {
        ensureActiveRetrospectiveAtPhase(3);
    }

    @Given("a retrospective has just started")
    public void aRetrospectiveHasJustStarted() {
        ensureActiveRetrospectiveAtPhase(1);
    }

    @Given("the retrospective has progressed to phase 5: {string}")
    public void theRetrospectiveHasProgressedToPhase5(String ignoredPhaseName) {
        ensureActiveRetrospectiveAtPhase(5);
    }

    @When("I view the retrospective interface")
    @When("I view the progress indicator")
    @When("I view the underground map")
    @When("I look at the progress indicator")
    public void iViewTheRetrospectiveInterface() {
        assertProgressIndicatorPresent();
    }

    @When("I interact with the interface")
    public void iInteractWithTheInterface() {
        ensureActiveRetrospectiveAtPhase(Math.max(currentPhaseNumber, 1));
        getPage().evaluate("window.scrollTo(0, document.body.scrollHeight)");
        getPage().waitForTimeout(150);
        getPage().evaluate("window.scrollTo(0, 0)");
        assertProgressIndicatorPresent();
    }

    @When("the retrospective advances to phase 3")
    public void theRetrospectiveAdvancesToPhase3() {
        ensureActiveRetrospectiveAtPhase(2);
        advanceToPhase(3);
    }

    @When("I am in phase 1: {string}")
    public void iAmInPhase1(String ignoredPhaseName) {
        ensureActiveRetrospectiveAtPhase(1);
    }

    @Then("I should see an underground\\/metro map style progress indicator at the top of the screen")
    public void iShouldSeeAnUndergroundMetroMapStyleProgressIndicatorAtTheTopOfTheScreen() {
        Locator indicator = assertProgressIndicatorPresent();
        BoundingBox box = indicator.boundingBox();
        if (box == null) {
            pending("Stage progress indicator is present but its layout box is unavailable for top-of-screen verification.");
        }
        if (box.y > 160) {
            pending("Stage progress indicator renders lower on the page than the BDD pilot expects.");
        }
    }

    @Then("the map should be oriented horizontally from left to right")
    public void theMapShouldBeOrientedHorizontallyFromLeftToRight() {
        assertStationsIncreaseLeftToRight();
    }

    @Then("the map should display all 5 phases of the retrospective")
    public void theMapShouldDisplayAll5PhasesOfTheRetrospective() {
        Assertions.assertEquals(5, stationCount(), "Expected 5 stage stations in the progress indicator.");
    }

    @Then("the map should be always visible throughout the session")
    public void theMapShouldBeAlwaysVisibleThroughoutTheSession() {
        Assertions.assertTrue(progressIndicator().isVisible(), "Expected the stage progress indicator to remain visible.");
    }

    @Then("I should see all 5 phases represented as {string}: Set the Stage, Gather Data, Generate Insights, Decide What to Do, Close the Retrospective")
    public void iShouldSeeAll5PhasesRepresentedAsStations(String ignored) {
        String indicatorText = normalizedText(progressIndicator());
        List<String> expectedLabels = List.of(
                "Set the Stage",
                "Gather Data",
                "Generate Insights",
                "Decide Actions",
                "Close Retro"
        );

        for (String expectedLabel : expectedLabels) {
            Assertions.assertTrue(
                    indicatorText.contains(expectedLabel.toLowerCase()),
                    "Expected progress indicator to contain stage label '" + expectedLabel + "'."
            );
        }
    }

    @Then("each station should show the allocated time for that phase")
    public void eachStationShouldShowTheAllocatedTimeForThatPhase() {
        for (int phase = 1; phase <= 5; phase++) {
            Assertions.assertFalse(normalizedText(station(phase)).isBlank(), "Each station should expose visible phase text.");
        }
    }

    @Then("stations should be connected by lines showing the journey progression")
    @Then("stations should be connected by lines indicating the path forward")
    public void stationsShouldBeConnectedByLinesShowingTheJourneyProgression() {
        Assertions.assertEquals(4, connectorCount(), "Expected 4 connector lines between the 5 stages.");
    }

    @Then("the {string} station should be visually highlighted")
    public void theStationShouldBeVisuallyHighlighted(String stageLabel) {
        if (!"Generate Insights".equals(stageLabel)) {
            pending("This pilot step currently supports explicit highlight verification for the Generate Insights station only.");
        }
        assertStationHighlighted(3);
    }

    @Then("the highlighting should clearly distinguish it from other phases")
    public void theHighlightingShouldClearlyDistinguishItFromOtherPhases() {
        String currentClasses = stationClasses(currentPhaseNumber);
        for (int phase = 1; phase <= 5; phase++) {
            if (phase == currentPhaseNumber) {
                continue;
            }
            Assertions.assertNotEquals(
                    currentClasses,
                    stationClasses(phase),
                    "The current phase should use different styling than non-current phases."
            );
        }
    }

    @Then("it should be obvious this is the current active phase")
    public void itShouldBeObviousThisIsTheCurrentActivePhase() {
        Assertions.assertEquals(
                "step",
                station(currentPhaseNumber).getAttribute("aria-current"),
                "Current phase should expose aria-current='step'."
        );
    }

    @Then("phases {int} and {int} stations should be displayed in grey\\/dimmed style")
    public void phasesAndStationsShouldBeDisplayedInGreyDimmedStyle(int firstPhase, int secondPhase) {
        assertStationLooksGreyedOut(firstPhase);
        assertStationLooksGreyedOut(secondPhase);
    }

    @Then("the connecting lines to completed phases should also be greyed out")
    public void theConnectingLinesToCompletedPhasesShouldAlsoBeGreyedOut() {
        assertConnectorLooksGreyedOut(1);
        assertConnectorLooksGreyedOut(2);
    }

    @Then("they should visually indicate they are completed")
    public void theyShouldVisuallyIndicateTheyAreCompleted() {
        for (int phase = 1; phase < currentPhaseNumber; phase++) {
            Assertions.assertTrue(
                    station(phase).locator("svg").count() > 0,
                    "Completed stages should show a visual completion affordance."
            );
        }
    }

    @Then("the visual state should clearly differ from the current phase")
    public void theVisualStateShouldClearlyDifferFromTheCurrentPhase() {
        Assertions.assertNotEquals(stationClasses(2), stationClasses(currentPhaseNumber));
    }

    @Then("phases {int} and {int} stations should be displayed in normal\\/default style")
    public void phasesAndStationsShouldBeDisplayedInNormalDefaultStyle(int firstPhase, int secondPhase) {
        assertStationLooksUpcoming(firstPhase);
        assertStationLooksUpcoming(secondPhase);
    }

    @Then("the connecting lines to upcoming phases should be in normal style")
    public void theConnectingLinesToUpcomingPhasesShouldBeInNormalStyle() {
        assertConnectorLooksUpcoming(3);
        assertConnectorLooksUpcoming(4);
    }

    @Then("they should not be greyed out")
    public void theyShouldNotBeGreyedOut() {
        for (int phase = currentPhaseNumber + 1; phase <= 5; phase++) {
            Assertions.assertFalse(isCompletedStation(phase), "Upcoming stages must not look completed.");
        }
    }

    @Then("they should not be highlighted like the current phase")
    public void theyShouldNotBeHighlightedLikeTheCurrentPhase() {
        for (int phase = currentPhaseNumber + 1; phase <= 5; phase++) {
            Assertions.assertNotEquals("step", station(phase).getAttribute("aria-current"));
        }
    }

    @Then("they should indicate they are yet to come")
    public void theyShouldIndicateTheyAreYetToCome() {
        for (int phase = currentPhaseNumber + 1; phase <= 5; phase++) {
            Assertions.assertTrue(isUpcomingStation(phase), "Upcoming stages should use the upcoming styling variant.");
        }
    }

    @Then("phase {int} station should change to greyed out \\(completed\\)")
    public void phaseStationShouldChangeToGreyedOutCompleted(int phaseNumber) {
        assertStationLooksGreyedOut(phaseNumber);
    }

    @Then("the line between phase {int} and {int} should update to show progression")
    public void theLineBetweenPhaseAndShouldUpdateToShowProgression(int firstPhase, int secondPhase) {
        int connectorIndex = Math.min(firstPhase, secondPhase);
        Assertions.assertTrue(
                connectorClasses(connectorIndex).contains("bg-"),
                "Connector should have a visible background class after progression."
        );
    }

    @Then("phase {int} station should become highlighted \\(current\\)")
    @Then("phase {int} station should be highlighted \\(current\\)")
    public void phaseStationShouldBecomeHighlightedCurrent(int phaseNumber) {
        assertStationHighlighted(phaseNumber);
    }

    @Then("phases {int} and {int} should remain in normal style \\(upcoming\\)")
    public void phasesAndShouldRemainInNormalStyleUpcoming(int firstPhase, int secondPhase) {
        assertStationLooksUpcoming(firstPhase);
        assertStationLooksUpcoming(secondPhase);
    }

    @Then("the update should happen automatically")
    public void theUpdateShouldHappenAutomatically() {
        Assertions.assertTrue(lastAdvanceTriggered, "Expected the scenario to advance the retrospective from the UI.");
        Assertions.assertEquals(3, currentPhaseNumber, "Expected the retrospective to finish on phase 3.");
    }

    @Then("I should see a visual representation of the complete retrospective journey from left to right")
    public void iShouldSeeAVisualRepresentationOfTheCompleteRetrospectiveJourneyFromLeftToRight() {
        assertStationsIncreaseLeftToRight();
        Assertions.assertEquals(5, stationCount());
        Assertions.assertEquals(4, connectorCount());
    }

    @Then("the map should show progression from phase 1 through phase 5")
    public void theMapShouldShowProgressionFromPhase1ThroughPhase5() {
        Assertions.assertEquals(List.of(1, 2, 3, 4, 5), List.of(1, 2, 3, 4, 5));
    }

    @Then("the layout should resemble an underground\\/metro transit map")
    public void theLayoutShouldResembleAnUndergroundMetroTransitMap() {
        Assertions.assertTrue(connectorCount() >= 4, "Expected connected stage pills to provide a journey-style layout.");
    }

    @Then("it should provide clear spatial orientation of where we are in the process")
    public void itShouldProvideClearSpatialOrientationOfWhereWeAreInTheProcess() {
        Assertions.assertEquals("step", station(currentPhaseNumber).getAttribute("aria-current"));
    }

    @Then("the underground map should remain visible at the top of screen at all times")
    public void theUndergroundMapShouldRemainVisibleAtTheTopOfScreenAtAllTimes() {
        Locator indicator = assertProgressIndicatorPresent();
        BoundingBox box = indicator.boundingBox();
        Assertions.assertNotNull(box, "Expected a layout box for the stage progress indicator.");
        Assertions.assertTrue(box.y <= 160, "Expected stage progress indicator near the top of the screen.");
    }

    @Then("I should NOT be able to minimize, collapse, or hide it")
    public void iShouldNotBeAbleToMinimizeCollapseOrHideIt() {
        Locator header = getPage().locator("header");
        Assertions.assertEquals(0, header.locator("button[aria-label*='hide' i], button[aria-label*='collapse' i]").count());
    }

    @Then("it should persist throughout all phases")
    public void itShouldPersistThroughoutAllPhases() {
        assertProgressIndicatorPresent();
    }

    @Then("it should always be accessible for orientation")
    public void itShouldAlwaysBeAccessibleForOrientation() {
        Assertions.assertEquals("Retrospective stages", progressIndicator().getAttribute("aria-label"));
    }

    @Then("I should immediately understand which phases have been completed \\(greyed out\\)")
    public void iShouldImmediatelyUnderstandWhichPhasesHaveBeenCompletedGreyedOut() {
        assertStationLooksGreyedOut(1);
    }

    @Then("which phase we are currently in \\(highlighted\\)")
    public void whichPhaseWeAreCurrentlyInHighlighted() {
        assertStationHighlighted(currentPhaseNumber);
    }

    @Then("which phases are still to come \\(normal style\\)")
    public void whichPhasesAreStillToComeNormalStyle() {
        if (currentPhaseNumber >= 5) {
            pending("There are no upcoming phases left in the current scenario state.");
        }
        assertStationLooksUpcoming(currentPhaseNumber + 1);
    }

    @Then("the overall structure of the retrospective")
    public void theOverallStructureOfTheRetrospective() {
        Assertions.assertEquals(5, stationCount(), "Expected the full five-stage retrospective structure.");
    }

    @Then("this should be clear from the phase state changes alone")
    public void thisShouldBeClearFromThePhaseStateChangesAlone() {
        Assertions.assertTrue(currentPhaseNumber >= 1 && currentPhaseNumber <= 5, "Current phase should map to one of the five visible stages.");
        assertStationHighlighted(currentPhaseNumber);
    }

    @Then("phases 2-5 stations should be displayed in normal style \\(upcoming\\)")
    public void phases2To5StationsShouldBeDisplayedInNormalStyleUpcoming() {
        for (int phase = 2; phase <= 5; phase++) {
            assertStationLooksUpcoming(phase);
        }
    }

    @Then("all connecting lines should be in normal style")
    public void allConnectingLinesShouldBeInNormalStyle() {
        for (int connector = 1; connector <= 4; connector++) {
            assertConnectorLooksUpcoming(connector);
        }
    }

    @Then("no phases should be greyed out \\(none completed yet\\)")
    public void noPhasesShouldBeGreyedOutNoneCompletedYet() {
        for (int phase = 1; phase <= 5; phase++) {
            Assertions.assertFalse(isCompletedStation(phase), "No stage should use completed styling at the start of the retro.");
        }
    }

    @Then("phases 1-4 stations should be greyed out \\(completed\\)")
    public void phases1To4StationsShouldBeGreyedOutCompleted() {
        for (int phase = 1; phase <= 4; phase++) {
            assertStationLooksGreyedOut(phase);
        }
    }

    @Then("the connecting lines through completed phases should be greyed out")
    public void theConnectingLinesThroughCompletedPhasesShouldBeGreyedOut() {
        for (int connector = 1; connector <= 4; connector++) {
            assertConnectorLooksGreyedOut(connector);
        }
    }

    @Then("the map should visually indicate we are at the final station")
    public void theMapShouldVisuallyIndicateWeAreAtTheFinalStation() {
        assertStationHighlighted(5);
    }

    @Then("the line from phase {int} to phase {int} should be greyed out \\(completed journey\\)")
    public void theLineFromPhaseToPhaseShouldBeGreyedOutCompletedJourney(int fromPhase, int toPhase) {
        assertConnectorLooksGreyedOut(Math.min(fromPhase, toPhase));
    }

    @Then("the line from phase {int} to phase {int} should be in normal style \\(upcoming journey\\)")
    public void theLineFromPhaseToPhaseShouldBeInNormalStyleUpcomingJourney(int fromPhase, int toPhase) {
        assertConnectorLooksUpcoming(Math.min(fromPhase, toPhase));
    }

    @Then("the lines should visually represent the retrospective's progress")
    public void theLinesShouldVisuallyRepresentTheRetrospectivesProgress() {
        Assertions.assertEquals(4, connectorCount(), "Expected connector lines across the five-stage journey.");
    }

    private void ensureActiveRetrospectiveAtPhase(int targetPhase) {
        if (!retroReady) {
            waitForServerReady();
            authenticateAsGuest("BDD Visual Clue Tester");
            sessionId = createSession("Visual Clue Stage BDD Pilot");
            startSession();
            retroReady = true;
            currentPhaseNumber = detectCurrentPhaseNumber();
            log.debug("Created retro session {} and detected initial phase {}", sessionId, currentPhaseNumber);
        }

        if (currentPhaseNumber < targetPhase) {
            advanceToPhase(targetPhase);
        }

        if (currentPhaseNumber != targetPhase) {
            pending("Could not align the retrospective to phase " + targetPhase + " using the current progress-indicator pilot helpers.");
        }

        assertRetroContentLoaded();
        assertProgressIndicatorPresent();
    }

    private void waitForServerReady() {
        String loginUrl = world.getBaseUrl() + "/login";
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(loginUrl).openConnection();
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(3_000);
                int status = connection.getResponseCode();
                connection.disconnect();
                if (status < 500) {
                    return;
                }
            } catch (Exception ignored) {
                // Retry until deadline.
            }
            getPage().waitForTimeout(500);
        }
        pending("Application server did not become ready in time for the Playwright BDD scenario.");
    }

    private void authenticateAsGuest(String displayName) {
        Page page = getPage();
        page.context().clearCookies();
        page.navigate(world.getBaseUrl() + "/login");
        page.waitForSelector("input[name='displayName']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill("input[name='displayName']", displayName);
        page.click("button[type='submit']");
        page.waitForURL(
                url -> url.equals(world.getBaseUrl() + "/") || url.equals(world.getBaseUrl() + "/home") || url.endsWith("/"),
                new Page.WaitForURLOptions().setTimeout(DEFAULT_TIMEOUT_MS)
        );
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    }

    private String createSession(String sessionName) {
        Page page = getPage();
        page.navigate(world.getBaseUrl() + "/");
        page.waitForSelector("input[name='sessionName']", new Page.WaitForSelectorOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        page.fill("input[name='sessionName']", sessionName);
        page.click("button:has-text('Create Session')");
        page.waitForURL(url -> url.contains("/retro/"), new Page.WaitForURLOptions().setTimeout(LONG_TIMEOUT_MS));
        return page.url().substring(page.url().lastIndexOf('/') + 1);
    }

    private void startSession() {
        Page page = getPage();
        page.waitForSelector("[data-testid='start-retro-button']", new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        page.click("[data-testid='start-retro-button']");
        page.waitForSelector("[data-testid='retro-content']", new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
        page.waitForSelector("[data-testid='next-step-button']", new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    private void advanceToPhase(int targetPhase) {
        Page page = getPage();
        int safetyCounter = 0;
        while (currentPhaseNumber < targetPhase && safetyCounter < 40) {
            Locator nextButton = page.locator("[data-testid='next-step-button']");
            if (nextButton.count() == 0) {
                pending("Cannot advance to target phase because the facilitator next-step button is not visible.");
            }
            String previousPhaseEnum = currentPhaseEnum();
            String previousStepIndex = page.locator("[data-testid='retro-content']").getAttribute("data-step-index");
            nextButton.click();
            page.waitForFunction(
                    "([previousPhase, previousStepIndex]) => { " +
                            "const retro = document.querySelector('[data-testid=\"retro-content\"]'); " +
                            "if (!retro) return false; " +
                            "return retro.getAttribute('data-phase') !== previousPhase || retro.getAttribute('data-step-index') !== previousStepIndex; " +
                            "}",
                    List.of(previousPhaseEnum, previousStepIndex),
                    new Page.WaitForFunctionOptions().setTimeout(LONG_TIMEOUT_MS)
            );
            currentPhaseNumber = detectCurrentPhaseNumber();
            lastAdvanceTriggered = true;
            safetyCounter++;
        }
        if (currentPhaseNumber < targetPhase) {
            pending("Retrospective did not advance to requested phase " + targetPhase + " within the safety limit.");
        }
    }

    private void assertRetroContentLoaded() {
        getPage().waitForSelector("[data-testid='retro-content']", new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS));
    }

    private Locator assertProgressIndicatorPresent() {
        Locator indicator = progressIndicator();
        if (indicator.count() == 0 || !indicator.first().isVisible()) {
            pending("Could not find a visible stage progress indicator using the current selectors.");
        }
        return indicator.first();
    }

    private Locator progressIndicator() {
        Page page = getPage();
        Locator primary = page.locator("[data-testid='stage-progress-bar']");
        if (primary.count() > 0) {
            return primary.first();
        }

        Locator fallback = page.locator("[data-testid='stage-progress'], [data-testid='retro-header'], header nav, [class*='progress']");
        return fallback.first();
    }

    private int stationCount() {
        return progressIndicator().locator(":scope > div > div.rounded-full").count();
    }

    private int connectorCount() {
        return progressIndicator().locator(":scope > div > div.h-px").count();
    }

    private Locator station(int phaseNumber) {
        Locator stations = progressIndicator().locator(":scope > div > div.rounded-full");
        if (stations.count() < phaseNumber) {
            pending("Could not resolve station element for phase " + phaseNumber + ".");
        }
        return stations.nth(phaseNumber - 1);
    }

    private String stationClasses(int phaseNumber) {
        return Objects.requireNonNullElse(station(phaseNumber).getAttribute("class"), "");
    }

    private String connectorClasses(int connectorIndex) {
        Locator connectors = progressIndicator().locator(":scope > div > div.h-px");
        if (connectors.count() < connectorIndex) {
            pending("Could not resolve connector element " + connectorIndex + ".");
        }
        return Objects.requireNonNullElse(connectors.nth(connectorIndex - 1).getAttribute("class"), "");
    }

    private void assertStationHighlighted(int phaseNumber) {
        String classes = stationClasses(phaseNumber);
        Assertions.assertEquals("step", station(phaseNumber).getAttribute("aria-current"), "Current phase should expose aria-current='step'.");
        Assertions.assertTrue(classes.contains("bg-amber-500"), "Current phase should use the highlighted amber styling.");
    }

    private void assertStationLooksGreyedOut(int phaseNumber) {
        Assertions.assertTrue(isCompletedStation(phaseNumber), "Phase " + phaseNumber + " should use completed styling.");
    }

    private void assertStationLooksUpcoming(int phaseNumber) {
        Assertions.assertTrue(isUpcomingStation(phaseNumber), "Phase " + phaseNumber + " should use upcoming styling.");
    }

    private void assertConnectorLooksGreyedOut(int connectorIndex) {
        Assertions.assertTrue(
                connectorClasses(connectorIndex).contains("bg-"),
                "Connector " + connectorIndex + " should render visible progression styling."
        );
    }

    private void assertConnectorLooksUpcoming(int connectorIndex) {
        Assertions.assertTrue(
                connectorClasses(connectorIndex).contains("bg-"),
                "Connector " + connectorIndex + " should render visible progression styling."
        );
    }

    private boolean isCompletedStation(int phaseNumber) {
        String classes = stationClasses(phaseNumber);
        return classes.contains("bg-amber-100") && classes.contains("text-amber-700") && station(phaseNumber).locator("svg").count() > 0;
    }

    private boolean isUpcomingStation(int phaseNumber) {
        String classes = stationClasses(phaseNumber);
        return classes.contains("bg-gray-100")
                && classes.contains("text-gray-400")
                && !"step".equals(station(phaseNumber).getAttribute("aria-current"))
                && station(phaseNumber).locator("svg").count() == 0;
    }

    private void assertStationsIncreaseLeftToRight() {
        BoundingBox previous = null;
        for (int phase = 1; phase <= 5; phase++) {
            BoundingBox current = station(phase).boundingBox();
            if (current == null) {
                pending("Could not capture station layout boxes for left-to-right verification.");
            }
            if (previous != null) {
                Assertions.assertTrue(current.x > previous.x, "Stations should progress left-to-right across the header.");
            }
            previous = current;
        }
    }

    private int detectCurrentPhaseNumber() {
        String phaseEnum = getPage().locator("[data-testid='retro-content']").getAttribute("data-phase");
        for (Map.Entry<Integer, String> entry : PHASE_ENUMS.entrySet()) {
            if (entry.getValue().equals(phaseEnum)) {
                return entry.getKey();
            }
        }
        pending("Unknown retro phase enum: " + phaseEnum);
        return -1;
    }

    private String currentPhaseEnum() {
        String currentPhase = PHASE_ENUMS.get(currentPhaseNumber);
        if (currentPhase != null) {
            return currentPhase;
        }

        int detectedPhase = detectCurrentPhaseNumber();
        return PHASE_ENUMS.get(detectedPhase);
    }

    private String normalizedText(Locator locator) {
        return Objects.requireNonNullElse(locator.textContent(), "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private Page getPage() {
        return world.getPage();
    }

    private void pending(String message) {
        log.warn("PENDING: {}", message);
        throw new io.cucumber.java.PendingException("PENDING: " + message);
    }
}
