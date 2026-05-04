package direct.reflect.facilitator.bdd.support.drivers;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import direct.reflect.facilitator.bdd.support.PlaywrightWorld;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static direct.reflect.facilitator.bdd.support.selectors.RetroSelectors.RETRO_CONTENT;

@ScenarioScope
@Component
@RequiredArgsConstructor
public class SyncDriver {
    private static final int LONG_TIMEOUT_MS = 15_000;

    private final PlaywrightWorld world;

    public void waitForServerReady() {
        String loginUrl = world.getBaseUrl() + "/login";
        long deadline = System.currentTimeMillis() + 30_000L;
        Page page = world.getPage();

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

            try {
                Thread.sleep(500);
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
            new Page.WaitForSelectorOptions().setTimeout(LONG_TIMEOUT_MS)
        );
    }

    public void assertSemanticHookPresent(Locator locator, String hookDescription) {
        if (locator.count() == 0) {
            throw new AssertionError("Missing semantic hook: " + hookDescription + " — selector returned no elements");
        }
    }

    public void waitForPhaseOrStepChange(String previousPhaseEnum, String previousStepIndex) {
        world.getPage().waitForFunction(
            "([prevPhase, prevStepIdx]) => { " +
                "const retro = document.querySelector('[data-testid=\"retro-content\"]'); " +
                "if (!retro) return false; " +
                "return retro.getAttribute('data-phase') !== prevPhase || retro.getAttribute('data-step-index') !== prevStepIdx; " +
            "}",
            List.of(previousPhaseEnum, previousStepIndex),
            new Page.WaitForFunctionOptions().setTimeout(LONG_TIMEOUT_MS)
        );
    }
}
