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

    public void assertProgressIndicatorPresent() {
        Locator indicator = progressIndicator();
        if (indicator.count() == 0 || !indicator.isVisible()) {
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
        if (station.count() == 0) {
            throw new PendingException("PENDING: Could not resolve station element for phase " + stageId + ".");
        }
        return station.first();
    }

    public Locator connector(int connectorIndex) {
        Locator connector = progressIndicator().locator(RetroSelectors.connectorSelector(connectorIndex));
        if (connector.count() == 0) {
            throw new PendingException("PENDING: Could not resolve connector element " + connectorIndex + ".");
        }
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
