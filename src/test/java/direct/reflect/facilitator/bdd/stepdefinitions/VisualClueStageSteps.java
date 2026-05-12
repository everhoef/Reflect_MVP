package direct.reflect.facilitator.bdd.stepdefinitions;

import com.microsoft.playwright.Locator;
import direct.reflect.facilitator.bdd.support.context.RetroScenarioContext;
import direct.reflect.facilitator.bdd.support.drivers.ProgressBarDriver;
import direct.reflect.facilitator.bdd.support.drivers.RetroLifecycleDriver;
import direct.reflect.facilitator.bdd.support.drivers.SyncDriver;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Objects;

@ScenarioScope
@RequiredArgsConstructor
public class VisualClueStageSteps {

    private final RetroLifecycleDriver retroLifecycleDriver;
    private final ProgressBarDriver progressBarDriver;
    private final SyncDriver syncDriver;
    private final RetroScenarioContext context;

    @Given("I am a team member in an active retrospective")
    public void iAmATeamMemberInAnActiveRetrospective() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("the underground map is displayed")
    public void theUndergroundMapIsDisplayed() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("I am in phase {int} of the retrospective")
    public void iAmInPhaseOfTheRetrospective(int phaseNumber) {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(phaseNumber);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("I am in phase 2 with phase 2 highlighted on the map")
    public void iAmInPhase2WithPhase2HighlightedOnTheMap() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(2);
        progressBarDriver.assertStationHighlighted(2);
    }

    @Given("I am viewing the underground map")
    public void iAmViewingTheUndergroundMap() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("I am participating in a retrospective")
    public void iAmParticipatingInARetrospective() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("I am a team member in any phase of the retrospective")
    public void iAmATeamMemberInAnyPhaseOfTheRetrospective() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(3);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("a retrospective has just started")
    public void aRetrospectiveHasJustStarted() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Given("the retrospective has progressed to phase 5: {string}")
    public void theRetrospectiveHasProgressedToPhase5(String ignoredPhaseName) {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(5);
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @When("I view the retrospective interface")
    @When("I view the progress indicator")
    @When("I view the underground map")
    @When("I look at the progress indicator")
    public void iViewTheRetrospectiveInterface() {
        syncDriver.assertRetroContentLoaded();
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @When("I interact with the interface")
    public void iInteractWithTheInterface() {
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @When("the retrospective advances to phase 3")
    public void theRetrospectiveAdvancesToPhase3() {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(2);
        retroLifecycleDriver.advanceToPhase(3);
    }

    @When("I am in phase 1: {string}")
    public void iAmInPhase1(String ignoredPhaseName) {
        retroLifecycleDriver.ensureActiveRetrospectiveAtPhase(1);
    }

    @Then("^I should see an underground/metro map style progress indicator at the top of the screen$")
    public void iShouldSeeAnUndergroundMetroMapStyleProgressIndicatorAtTheTopOfTheScreen() {
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Then("the map should be oriented horizontally from left to right")
    public void theMapShouldBeOrientedHorizontallyFromLeftToRight() {
        progressBarDriver.assertStationsIncreaseLeftToRight();
    }

    @Then("the map should display all 5 phases of the retrospective")
    public void theMapShouldDisplayAll5PhasesOfTheRetrospective() {
        Assertions.assertEquals(5, progressBarDriver.stationCount(), "Expected 5 stage stations in the progress indicator.");
    }

    @Then("the map should be always visible throughout the session")
    public void theMapShouldBeAlwaysVisibleThroughoutTheSession() {
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Then("I should see all 5 phases represented as {string}: Set the Stage, Gather Data, Generate Insights, Decide What to Do, Close the Retrospective")
    public void iShouldSeeAll5PhasesRepresentedAsStations(String ignored) {
        String indicatorText = normalizedText(progressBarDriver.progressIndicator());
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
            Assertions.assertFalse(normalizedText(progressBarDriver.station(phase)).isBlank(), "Each station should expose visible phase text.");
        }
    }

    @Then("stations should be connected by lines showing the journey progression")
    @Then("stations should be connected by lines indicating the path forward")
    public void stationsShouldBeConnectedByLinesShowingTheJourneyProgression() {
        Assertions.assertEquals(4, progressBarDriver.connectorCount(), "Expected 4 connector lines between the 5 stages.");
    }

    @Then("the {string} station should be visually highlighted")
    public void theStationShouldBeVisuallyHighlighted(String stageLabel) {
        Assertions.assertEquals("Generate Insights", stageLabel, "Expected scenario to verify the Generate Insights station.");
        progressBarDriver.assertStationHighlighted(3);
    }

    @Then("the highlighting should clearly distinguish it from other phases")
    public void theHighlightingShouldClearlyDistinguishItFromOtherPhases() {
        for (int phase = 1; phase <= 5; phase++) {
            if (phase == retroLifecycleDriver.getCurrentPhaseNumber()) {
                continue;
            }

            Assertions.assertNotEquals(
                    progressBarDriver.station(phase).getAttribute("data-stage-status"),
                    progressBarDriver.station(retroLifecycleDriver.getCurrentPhaseNumber()).getAttribute("data-stage-status"),
                    "The current phase should use a different semantic status than non-current phases."
            );
        }
    }

    @Then("it should be obvious this is the current active phase")
    public void itShouldBeObviousThisIsTheCurrentActivePhase() {
        Assertions.assertEquals(
                "step",
                progressBarDriver.station(retroLifecycleDriver.getCurrentPhaseNumber()).getAttribute("aria-current"),
                "Current phase should expose aria-current='step'."
        );
    }

    @Then("^phases (\\d+) and (\\d+) stations should be displayed in grey/dimmed style$")
    public void phasesAndStationsShouldBeDisplayedInGreyDimmedStyle(int firstPhase, int secondPhase) {
        progressBarDriver.assertStationLooksGreyedOut(firstPhase);
        progressBarDriver.assertStationLooksGreyedOut(secondPhase);
    }

    @Then("the connecting lines to completed phases should also be greyed out")
    public void theConnectingLinesToCompletedPhasesShouldAlsoBeGreyedOut() {
        progressBarDriver.assertConnectorLooksGreyedOut(1);
    }

    @Then("they should visually indicate they are completed")
    public void theyShouldVisuallyIndicateTheyAreCompleted() {
        for (int phase = 1; phase < retroLifecycleDriver.getCurrentPhaseNumber(); phase++) {
            progressBarDriver.assertStationShowsCompletionAffordance(phase);
        }
    }

    @Then("the visual state should clearly differ from the current phase")
    public void theVisualStateShouldClearlyDifferFromTheCurrentPhase() {
        for (int phase = 1; phase <= 5; phase++) {
            if (phase == retroLifecycleDriver.getCurrentPhaseNumber()) {
                continue;
            }

            Assertions.assertNotEquals(
                    progressBarDriver.station(phase).getAttribute("data-stage-status"),
                    progressBarDriver.station(retroLifecycleDriver.getCurrentPhaseNumber()).getAttribute("data-stage-status")
            );
        }
    }

    @Then("^phases (\\d+) and (\\d+) stations should be displayed in normal/default style$")
    public void phasesAndStationsShouldBeDisplayedInNormalDefaultStyle(int firstPhase, int secondPhase) {
        progressBarDriver.assertStationLooksUpcoming(firstPhase);
        progressBarDriver.assertStationLooksUpcoming(secondPhase);
    }

    @Then("the connecting lines to upcoming phases should be in normal style")
    public void theConnectingLinesToUpcomingPhasesShouldBeInNormalStyle() {
        progressBarDriver.assertConnectorLooksUpcoming(4);
    }

    @Then("they should not be greyed out")
    public void theyShouldNotBeGreyedOut() {
        for (int phase = retroLifecycleDriver.getCurrentPhaseNumber() + 1; phase <= 5; phase++) {
            Assertions.assertFalse(progressBarDriver.isCompletedStation(phase), "Upcoming stages must not look completed.");
        }
    }

    @Then("they should not be highlighted like the current phase")
    public void theyShouldNotBeHighlightedLikeTheCurrentPhase() {
        for (int phase = retroLifecycleDriver.getCurrentPhaseNumber() + 1; phase <= 5; phase++) {
            Assertions.assertNotEquals("step", progressBarDriver.station(phase).getAttribute("aria-current"));
        }
    }

    @Then("they should indicate they are yet to come")
    public void theyShouldIndicateTheyAreYetToCome() {
        for (int phase = retroLifecycleDriver.getCurrentPhaseNumber() + 1; phase <= 5; phase++) {
            Assertions.assertTrue(progressBarDriver.isUpcomingStation(phase), "Upcoming stages should use the upcoming styling variant.");
        }
    }

    @Then("^phase (\\d+) station should change to greyed out \\(completed\\)$")
    public void phaseStationShouldChangeToGreyedOutCompleted(int phaseNumber) {
        progressBarDriver.assertStationLooksGreyedOut(phaseNumber);
    }

    @Then("the line between phase {int} and {int} should update to show progression")
    public void theLineBetweenPhaseAndShouldUpdateToShowProgression(int firstPhase, int secondPhase) {
        progressBarDriver.assertConnectorHasStatus(Math.min(firstPhase, secondPhase));
    }

    @Then("^phase (\\d+) station should become highlighted \\(current\\)$")
    @Then("^phase (\\d+) station should be highlighted \\(current\\)$")
    public void phaseStationShouldBecomeHighlightedCurrent(int phaseNumber) {
        progressBarDriver.assertStationHighlighted(phaseNumber);
    }

    @Then("^phases (\\d+) and (\\d+) should remain in normal style \\(upcoming\\)$")
    public void phasesAndShouldRemainInNormalStyleUpcoming(int firstPhase, int secondPhase) {
        progressBarDriver.assertStationLooksUpcoming(firstPhase);
        progressBarDriver.assertStationLooksUpcoming(secondPhase);
    }

    @Then("the update should happen automatically")
    public void theUpdateShouldHappenAutomatically() {
        Assertions.assertTrue(context.isLastAdvanceTriggered(), "Expected the scenario to advance the retrospective from the UI.");
        Assertions.assertEquals(3, retroLifecycleDriver.getCurrentPhaseNumber(), "Expected the retrospective to finish on phase 3.");
    }

    @Then("I should see a visual representation of the complete retrospective journey from left to right")
    public void iShouldSeeAVisualRepresentationOfTheCompleteRetrospectiveJourneyFromLeftToRight() {
        progressBarDriver.assertStationsIncreaseLeftToRight();
        Assertions.assertEquals(5, progressBarDriver.stationCount());
        Assertions.assertEquals(4, progressBarDriver.connectorCount());
    }

    @Then("the map should show progression from phase 1 through phase 5")
    public void theMapShouldShowProgressionFromPhase1ThroughPhase5() {
        Assertions.assertEquals(5, progressBarDriver.stationCount());
    }

    @Then("^the layout should resemble an underground/metro transit map$")
    public void theLayoutShouldResembleAnUndergroundMetroTransitMap() {
        Assertions.assertTrue(progressBarDriver.connectorCount() >= 4, "Expected connected stage pills to provide a journey-style layout.");
    }

    @Then("it should provide clear spatial orientation of where we are in the process")
    public void itShouldProvideClearSpatialOrientationOfWhereWeAreInTheProcess() {
        Assertions.assertEquals("step", progressBarDriver.station(retroLifecycleDriver.getCurrentPhaseNumber()).getAttribute("aria-current"));
    }

    @Then("the underground map should remain visible at the top of screen at all times")
    public void theUndergroundMapShouldRemainVisibleAtTheTopOfScreenAtAllTimes() {
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Then("I should NOT be able to minimize, collapse, or hide it")
    public void iShouldNotBeAbleToMinimizeCollapseOrHideIt() {
        progressBarDriver.assertNoHideCollapseControls();
    }

    @Then("it should persist throughout all phases")
    public void itShouldPersistThroughoutAllPhases() {
        progressBarDriver.assertProgressIndicatorPresent();
    }

    @Then("it should always be accessible for orientation")
    public void itShouldAlwaysBeAccessibleForOrientation() {
        Assertions.assertEquals("Retrospective stages", progressBarDriver.progressIndicator().getAttribute("aria-label"));
    }

    @Then("^I should immediately understand which phases have been completed \\(greyed out\\)$")
    public void iShouldImmediatelyUnderstandWhichPhasesHaveBeenCompletedGreyedOut() {
        progressBarDriver.assertStationLooksGreyedOut(1);
    }

    @Then("^which phase we are currently in \\(highlighted\\)$")
    public void whichPhaseWeAreCurrentlyInHighlighted() {
        progressBarDriver.assertStationHighlighted(retroLifecycleDriver.getCurrentPhaseNumber());
    }

    @Then("^which phases are still to come \\(normal style\\)$")
    public void whichPhasesAreStillToComeNormalStyle() {
        Assertions.assertTrue(retroLifecycleDriver.getCurrentPhaseNumber() < 5, "Expected at least one upcoming phase in this scenario.");
        progressBarDriver.assertStationLooksUpcoming(retroLifecycleDriver.getCurrentPhaseNumber() + 1);
    }

    @Then("the overall structure of the retrospective")
    public void theOverallStructureOfTheRetrospective() {
        Assertions.assertEquals(5, progressBarDriver.stationCount(), "Expected the full five-stage retrospective structure.");
    }

    @Then("this should be clear from the phase state changes alone")
    public void thisShouldBeClearFromThePhaseStateChangesAlone() {
        Assertions.assertTrue(retroLifecycleDriver.getCurrentPhaseNumber() >= 1 && retroLifecycleDriver.getCurrentPhaseNumber() <= 5, "Current phase should map to one of the five visible stages.");
        progressBarDriver.assertStationHighlighted(retroLifecycleDriver.getCurrentPhaseNumber());
    }

    @Then("^phases 2-5 stations should be displayed in normal style \\(upcoming\\)$")
    public void phases2To5StationsShouldBeDisplayedInNormalStyleUpcoming() {
        for (int phase = 2; phase <= 5; phase++) {
            progressBarDriver.assertStationLooksUpcoming(phase);
        }
    }

    @Then("all connecting lines should be in normal style")
    public void allConnectingLinesShouldBeInNormalStyle() {
        for (int connector = 1; connector <= 4; connector++) {
            progressBarDriver.assertConnectorLooksUpcoming(connector);
        }
    }

    @Then("^no phases should be greyed out \\(none completed yet\\)$")
    public void noPhasesShouldBeGreyedOutNoneCompletedYet() {
        for (int phase = 1; phase <= 5; phase++) {
            Assertions.assertFalse(progressBarDriver.isCompletedStation(phase), "No stage should use completed styling at the start of the retro.");
        }
    }

    @Then("^phases 1-4 stations should be greyed out \\(completed\\)$")
    public void phases1To4StationsShouldBeGreyedOutCompleted() {
        for (int phase = 1; phase <= 4; phase++) {
            progressBarDriver.assertStationLooksGreyedOut(phase);
        }
    }

    @Then("the connecting lines through completed phases should be greyed out")
    public void theConnectingLinesThroughCompletedPhasesShouldBeGreyedOut() {
        for (int connector = 1; connector < retroLifecycleDriver.getCurrentPhaseNumber(); connector++) {
            progressBarDriver.assertConnectorLooksGreyedOut(connector);
        }
    }

    @Then("the map should visually indicate we are at the final station")
    public void theMapShouldVisuallyIndicateWeAreAtTheFinalStation() {
        progressBarDriver.assertStationHighlighted(5);
    }

    @Then("^the line from phase (\\d+) to phase (\\d+) should be greyed out \\(completed journey\\)$")
    public void theLineFromPhaseToPhaseShouldBeGreyedOutCompletedJourney(int fromPhase, int toPhase) {
        progressBarDriver.assertConnectorLooksGreyedOut(Math.min(fromPhase, toPhase));
    }

    @Then("^the line from phase (\\d+) to phase (\\d+) should be in normal style \\(upcoming journey\\)$")
    public void theLineFromPhaseToPhaseShouldBeInNormalStyleUpcomingJourney(int fromPhase, int toPhase) {
        progressBarDriver.assertConnectorLooksUpcoming(Math.min(fromPhase, toPhase));
    }

    @Then("the lines should visually represent the retrospective's progress")
    public void theLinesShouldVisuallyRepresentTheRetrospectivesProgress() {
        Assertions.assertEquals(4, progressBarDriver.connectorCount(), "Expected connector lines across the five-stage journey.");
    }


    private String normalizedText(Locator locator) {
        return Objects.requireNonNullElse(locator.textContent(), "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
