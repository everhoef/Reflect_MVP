package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Action Review Browser Regression Tests")
public class ActionReviewBrowserRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RetroSessionService retroSessionService;

    @Test
    @Timeout(120)
    @DisplayName("Should render Action Review empty state")
    void shouldRenderActionReviewEmptyState() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();

        try {
            // Wait for server to be ready
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(baseUrl + "/login").openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(3000);
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status < 500) break;
                } catch (Exception ignored) { /* server not ready yet, retry */ }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            logTestProgress("SETUP", 1, 3, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 3, "Creating retro session with Default template");
            String sessionId = createRetroSession(facilitatorPage, "Action Review Test");

            logTestProgress("SETUP", 3, 3, "Starting session and moving to Action Review");
            retroSessionService.startSession(UUID.fromString(sessionId));
            
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            
            session.setPhase(RetroPhase.CLOSE_RETRO);
            session.setCurrentStepIndex(0);
            session.setStepStartedAt(LocalDateTime.now());
            retroSessionRepository.save(session);

            refreshRetroPageUntilLoaded(facilitatorPage, sessionId, RetroPhase.CLOSE_RETRO.name(), "[data-testid='action-review-container']");
            waitForElement(facilitatorPage, "[data-testid='action-review-container']", SSE_PROPAGATION_TIMEOUT_MS);
            
            logTestProgress("ASSERT", 3, 3, "Verifying empty state message");
            waitForElement(facilitatorPage, "[data-testid='empty-actions-message']");
            
            assertTrue(facilitatorPage.locator("[data-testid='empty-actions-message']").isVisible(),
                "Empty state message should be visible");
                
            String emptyMsg = facilitatorPage.locator("[data-testid='empty-actions-message']").textContent();
            assertTrue(emptyMsg.contains("No open action items"), "Should contain the empty state config text");
            
        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Action Review Empty State", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }
}
