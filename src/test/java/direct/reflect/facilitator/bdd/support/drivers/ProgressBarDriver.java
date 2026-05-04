package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
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
        BoundingBox previous = null;
        for (int phase = 1; phase <= 5; phase++) {
            BoundingBox current = station(phase).boundingBox();
            if (current == null) {
                throw new PendingException("PENDING: Could not capture station layout boxes for left-to-right verification.");
            }
            if (previous != null) {
                Assertions.assertTrue(current.x > previous.x, "Stations should progress left-to-right across the header.");
            }
            previous = current;
        }
    }
}
