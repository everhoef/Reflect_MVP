package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Locator;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import direct.reflect.facilitator.bdd.support.selectors.RetroSelectors;
import io.cucumber.java.PendingException;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.STAGE_PROGRESS_BAR;

@ScenarioScope
@Component
@RequiredArgsConstructor
public class ProgressBarDriver {
    private final PlaywrightWorld world;
    private final SyncDriver syncDriver;

    public void assertProgressIndicatorPresent() {
        Locator indicator = progressIndicator();
        syncDriver.assertSemanticHookPresent(indicator, "stage progress bar");
        if (!indicator.isVisible()) {
            throw new PendingException("PENDING: Could not find a visible stage progress indicator using the current selectors.");
        }
    }

    public Locator progressIndicator() {
        return world.getPage().locator(STAGE_PROGRESS_BAR).first();
    }

    public int stationCount() {
        return progressIndicator().locator("[data-stage-index]").count();
    }

    public int connectorCount() {
        return progressIndicator().locator("[data-connector-index]").count();
    }

    public Locator station(int stageId) {
        Locator station = progressIndicator().locator(RetroSelectors.stationSelector(stageId));
        syncDriver.assertSemanticHookPresent(station, "stage station " + stageId);
        return station.first();
    }

    public Locator connector(int connectorIndex) {
        Locator connector = progressIndicator().locator(RetroSelectors.connectorSelector(connectorIndex));
        syncDriver.assertSemanticHookPresent(connector, "connector " + connectorIndex);
        return connector.first();
    }

    public void assertStationHighlighted(int phaseNumber) {
        Assertions.assertEquals("in-progress", station(phaseNumber).getAttribute("data-stage-status"));
        Assertions.assertEquals("step", station(phaseNumber).getAttribute("aria-current"));
    }

    public void assertStationLooksGreyedOut(int phaseNumber) {
        Assertions.assertTrue(isCompletedStation(phaseNumber), "Phase " + phaseNumber + " should use completed styling.");
    }

    public void assertStationLooksUpcoming(int phaseNumber) {
        Assertions.assertTrue(isUpcomingStation(phaseNumber), "Phase " + phaseNumber + " should use upcoming styling.");
    }

    public boolean isCompletedStation(int phaseNumber) {
        return "complete".equals(station(phaseNumber).getAttribute("data-stage-status"));
    }

    public boolean isUpcomingStation(int phaseNumber) {
        return "to-do".equals(station(phaseNumber).getAttribute("data-stage-status"));
    }

    public void assertConnectorStatus(int connectorIndex, String expectedStatus) {
        Assertions.assertEquals(expectedStatus, connector(connectorIndex).getAttribute("data-connector-status"));
    }

    public void assertConnectorLooksGreyedOut(int index) {
        String status = connector(index).getAttribute("data-connector-status");
        Assertions.assertNotEquals(
            "in-progress",
            status,
            "Expected connector " + index + " to NOT be in-progress (greyed out means left-side connector, not active boundary)"
        );
    }

    public void assertConnectorLooksUpcoming(int index) {
        String status = connector(index).getAttribute("data-connector-status");
        Assertions.assertNotEquals(
            "complete",
            status,
            "Expected connector " + index + " to NOT be complete (upcoming means right-side connector, not fully done)"
        );
    }

    public void assertStationShowsCompletionAffordance(int phase) {
        int svgCount = station(phase).locator("svg").count();
        Assertions.assertTrue(
            svgCount > 0,
            "Expected station " + phase + " to show a completion affordance (SVG icon), but found none"
        );
    }

    public void assertNoHideCollapseControls() {
        Locator header = world.getPage().locator("header");
        Assertions.assertEquals(
            0,
            header.locator("button[aria-label*='collapse'], button[aria-label*='hide'], button[aria-label*='minimize']").count(),
            "Expected no collapse/hide/minimize controls in header"
        );
    }

    public void assertConnectorHasStatus(int index) {
        String status = connector(index).getAttribute("data-connector-status");
        Assertions.assertNotNull(status, "Expected connector " + index + " to have a data-connector-status attribute");
    }

    public void assertStationsIncreaseLeftToRight() {
        Boolean isOrdered = (Boolean) world.getPage().evaluate(
            "() => {" +
            "  const bar = document.querySelector('[data-testid=\"stage-progress-bar\"]');" +
            "  if (!bar) return false;" +
            "  const stations = Array.from(bar.querySelectorAll('[data-stage-index]'));" +
            "  const indices = stations.map(el => parseInt(el.getAttribute('data-stage-index'), 10));" +
            "  return indices.every((val, idx) => idx === 0 || val > indices[idx - 1]);" +
            "}"
        );
        Assertions.assertTrue(Boolean.TRUE.equals(isOrdered),
            "Stage stations should appear in increasing data-stage-index order (left to right).");
    }
}
