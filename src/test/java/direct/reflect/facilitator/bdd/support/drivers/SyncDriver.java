package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;

@ScenarioScope
@Component
@RequiredArgsConstructor
public class SyncDriver {
    private static final int RETRO_CONTENT_TIMEOUT_MS = 5_000;
    private static final int SYNC_POLL_INTERVAL_MS = 100;
    private static final int SYNC_WINDOW_MS = 2_500;
    private static final int SERVER_READY_TIMEOUT_MS = 30_000;
    private static final int SERVER_CONNECT_TIMEOUT_MS = 1_000;
    private static final int SERVER_READ_TIMEOUT_MS = 3_000;
    private static final int SERVER_POLL_INTERVAL_MS = 500;

    private final PlaywrightWorld world;

    public void waitForServerReady() {
        String loginUrl = world.getBaseUrl() + "/login";
        long deadline = System.currentTimeMillis() + SERVER_READY_TIMEOUT_MS;
        Page page = world.getPage();

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(loginUrl).openConnection();
                connection.setConnectTimeout(SERVER_CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(SERVER_READ_TIMEOUT_MS);
                int status = connection.getResponseCode();
                connection.disconnect();
                if (status < 500) {
                    return;
                }
            } catch (Exception ignored) {
                // Retry until deadline; transient startup failures are expected here.
            }

            try {
                Thread.sleep(SERVER_POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new AssertionError("Application server did not become ready in time for the Playwright BDD scenario.");
    }

    public void assertRetroContentLoaded() {
        world.getPage().waitForSelector(
            RETRO_CONTENT,
            new Page.WaitForSelectorOptions().setTimeout(RETRO_CONTENT_TIMEOUT_MS)
        );
    }

    public void assertSemanticHookPresent(Locator locator, String hookDescription) {
        if (locator.count() == 0) {
            throw new AssertionError("Missing semantic hook: " + hookDescription + " — selector returned no elements");
        }
    }

    public ShellSnapshot captureShellSnapshot() {
        Locator retroContent = world.getPage().locator(RETRO_CONTENT);
        String phase = retroContent.getAttribute("data-phase");
        String stepIndex = retroContent.getAttribute("data-step-index");
        String syncState = retroContent.getAttribute("data-sync-state");
        String sseConnected = retroContent.getAttribute("data-sse-connected");

        return new ShellSnapshot(
            phase == null ? "<missing>" : phase,
            stepIndex == null ? "<missing>" : stepIndex,
            syncState == null ? "<missing>" : syncState,
            sseConnected == null ? "<missing>" : sseConnected
        );
    }

    public void waitForPhaseOrStepChange(ShellSnapshot previousSnapshot) {
        ShellSnapshot currentSnapshot = pollForSettledShellChange(previousSnapshot, SYNC_WINDOW_MS);
        boolean reloadAttempted = false;

        if (!currentSnapshot.isSettledChangedFrom(previousSnapshot) && currentSnapshot.shouldAttemptReload()) {
            reloadAttempted = true;
            reloadAndWaitForRetroContent();
            currentSnapshot = pollForSettledShellChange(previousSnapshot, SYNC_WINDOW_MS);
        }

        if (!currentSnapshot.isSettledChangedFrom(previousSnapshot)) {
            throw new AssertionError(
                "Retro shell did not reach a settled phase/step change. " +
                    "previous phase=" + previousSnapshot.phase() +
                    ", previous step=" + previousSnapshot.stepIndex() +
                    ", current phase=" + currentSnapshot.phase() +
                    ", current step=" + currentSnapshot.stepIndex() +
                    ", current sync-state=" + currentSnapshot.syncState() +
                    ", current connection=" + currentSnapshot.sseConnected() +
                    ", refresh attempted=" + reloadAttempted
            );
        }
    }

    private ShellSnapshot pollForSettledShellChange(ShellSnapshot previousSnapshot, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ShellSnapshot latestSnapshot = captureShellSnapshot();

        while (System.currentTimeMillis() <= deadline) {
            latestSnapshot = captureShellSnapshot();
            if (latestSnapshot.isSettledChangedFrom(previousSnapshot)) {
                return latestSnapshot;
            }
            sleep(SYNC_POLL_INTERVAL_MS);
        }

        return latestSnapshot;
    }

    private void reloadAndWaitForRetroContent() {
        world.getPage().reload(new Page.ReloadOptions().setTimeout(RETRO_CONTENT_TIMEOUT_MS));
        assertRetroContentLoaded();
    }

    private void sleep(int intervalMs) {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while polling retro shell state.", interruptedException);
        }
    }

    public record ShellSnapshot(String phase, String stepIndex, String syncState, String sseConnected) {
        private boolean phaseOrStepChangedFrom(ShellSnapshot previousSnapshot) {
            return !phase.equals(previousSnapshot.phase()) || !stepIndex.equals(previousSnapshot.stepIndex());
        }

        private boolean isSettledChangedFrom(ShellSnapshot previousSnapshot) {
            return phaseOrStepChangedFrom(previousSnapshot) && "settled".equals(syncState);
        }

        private boolean shouldAttemptReload() {
            return "false".equals(sseConnected) || "reconciling".equals(syncState);
        }
    }
}
