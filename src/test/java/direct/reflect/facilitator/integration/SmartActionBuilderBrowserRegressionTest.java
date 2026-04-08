package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@DisplayName("SMART Action Builder Browser Regression Test")
public class SmartActionBuilderBrowserRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RetroStepRepository stepRepository;

    private static final int SSC_GATHER_DATA_MASTERSHEET_ID = 29;
    private static final int SMART_ACTION_BUILDER_STEP_INDEX = 5; // Step 6 is index 5

    @Test
    @Timeout(300)
    @DisplayName("Should validate SMART Action Builder creation, deletion, edit, escalation, and SSE sync")
    void shouldValidateSmartActionBuilderFlow() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();

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
                } catch (Exception ignored) { }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            // 1. Authenticate
            logTestProgress("SETUP", 1, 9, "Authenticating users");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participantPage, "Participant");

            // 2. Create session
            logTestProgress("SETUP", 2, 9, "Creating session");
            String sessionId = createRetroSession(facilitatorPage, "SMART Action Test Session");

            // 3. Swap template to include SMART_ACTION_BUILDER (SSC stage 29)
            logTestProgress("SETUP", 3, 9, "Swapping template");
            RetroTemplate sscTemplate = buildSscTemplate();
            RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            session.setTemplate(sscTemplate);
            retroSessionRepository.save(session);

            // 4. Participant joins, facilitator starts session
            logTestProgress("SETUP", 4, 9, "Starting session");
            joinRetroSession(participantPage, sessionId);
            startRetroSession(facilitatorPage, sessionId);

            // 5. Fast-forward to SMART_ACTION_BUILDER step
            logTestProgress("SETUP", 5, 9, "Fast-forwarding to step");
            fastForwardToStep(sessionId, SMART_ACTION_BUILDER_STEP_INDEX);

            // Reload pages
            String retroUrl = baseUrl + "/retro/" + sessionId;
            facilitatorPage.navigate(retroUrl);
            participantPage.navigate(retroUrl);

            // Wait for component to load
            waitForAllPagesElement("text=Create SMART Action", facilitatorPage, participantPage);

            // 6. Test Validation (Facilitator)
            logTestProgress("TEST", 6, 9, "Testing validation");
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");
            waitForElement(facilitatorPage, "text=What is required");

            // 7. Create an Action Item with Escalation (Facilitator)
            logTestProgress("TEST", 7, 9, "Creating action item");
            fillElement(facilitatorPage, "[data-testid='what-input']", "Update the database schema");
            fillElement(facilitatorPage, "[data-testid='who-input']", "Backend Team");
            fillElement(facilitatorPage, "[data-testid='due-date-input']", "2026-12-31");
            fillElement(facilitatorPage, "[data-testid='success-criteria-input']", "Migrations pass in CI");
            
            // Check the escalation toggle
            facilitatorPage.check("[data-testid='escalation-toggle']");
            
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");

            // Verify it appears for facilitator
            waitForElement(facilitatorPage, "text=Update the database schema");
            
            // 8. Verify SSE sync (Participant)
            logTestProgress("TEST", 8, 9, "Verifying SSE sync");
            waitForElement(participantPage, "text=Update the database schema");
            waitForElement(participantPage, "text=Backend Team");

            // 9. Edit the Action Item (Facilitator)
            logTestProgress("TEST", 9, 9, "Editing action item");
            facilitatorPage.waitForSelector("[data-testid^='edit-btn-']");
            clickElement(facilitatorPage, "[data-testid^='edit-btn-']");
            
            waitForElement(facilitatorPage, "[data-testid='edit-what-input']");
            fillElement(facilitatorPage, "[data-testid='edit-what-input']", "Update the database schema (Edited)");
            clickElement(facilitatorPage, "[data-testid='save-edit-btn']");
            
            waitForElement(facilitatorPage, "text=Update the database schema (Edited)");
            // Verify SSE sync for edit
            waitForElement(participantPage, "text=Update the database schema (Edited)");

            // 10. Delete the Action Item (Participant)
            logTestProgress("TEST", 10, 10, "Deleting action item");
            participantPage.waitForSelector("[data-testid^='delete-btn-']");
            participantPage.onDialog(dialog -> dialog.accept());
            clickElement(participantPage, "[data-testid^='delete-btn-']");

            // Verify it disappears for participant
            participantPage.waitForFunction("() => !document.body.textContent.includes('Update the database schema (Edited)')");
            
            // Verify SSE sync (Facilitator)
            facilitatorPage.waitForFunction("() => !document.body.textContent.includes('Update the database schema (Edited)')");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Test failed", e);
            throw e;
        } finally {
            participantContext.close();
            facilitatorContext.close();
        }
    }

    private void fastForwardToStep(String sessionId, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(RetroPhase.GATHER_DATA);
        session.setCurrentStepIndex(stepIndex);
        retroSessionRepository.save(session);
    }

    private RetroTemplate buildSscTemplate() {
        RetroStage sscStage = stageRepository.findByMastersheetID(SSC_GATHER_DATA_MASTERSHEET_ID)
                .orElseThrow(() -> new IllegalStateException("Start Stop Continue stage not found"));

        RetroTemplate defaultTemplate = templateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No templates found"));

        // Enable allowEscalation on the SMART_ACTION_BUILDER step
        List<RetroStep> steps = stepRepository.findByRetroStageOrderByOrderIndexAsc(defaultTemplate.getDecideActions());
        steps.stream()
                .filter(step -> step.getComponentType() == ComponentType.SMART_ACTION_BUILDER)
                .forEach(step -> {
                    step.getComponentConfig().put("allowEscalation", true);
                    stepRepository.save(step);
                });

        RetroTemplate sscTemplate = new RetroTemplate();
        sscTemplate.setName("SMART Action Test");
        sscTemplate.setDescription("Test template");
        sscTemplate.setMaturityLevel(2);
        sscTemplate.setReleased(false);
        sscTemplate.setSetTheStage(defaultTemplate.getSetTheStage());
        sscTemplate.setGatherData(sscStage);
        sscTemplate.setGenerateInsights(defaultTemplate.getGenerateInsights());
        sscTemplate.setDecideActions(defaultTemplate.getDecideActions());
        sscTemplate.setCloseRetro(defaultTemplate.getCloseRetro());

        return templateRepository.save(sscTemplate);
    }
}
