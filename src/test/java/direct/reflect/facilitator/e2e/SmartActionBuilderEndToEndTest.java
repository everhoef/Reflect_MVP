package direct.reflect.facilitator.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.e2e.support.BaseEndToEndTest;
import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@DisplayName("SMART Action Builder End-to-End Test")
public class SmartActionBuilderEndToEndTest extends BaseEndToEndTest {

    @Autowired
    private RetroStepRepository stepRepository;

    private static final int SSC_DECIDE_ACTIONS_MASTERSHEET_ID = 29;
    private static final int SMART_ACTION_BUILDER_STEP_INDEX = 5;

    @Test
    @Timeout(300)
    @DisplayName("Should validate SMART Action Builder stale participant recovery without manual refresh")
    void shouldValidateSmartActionBuilderFlow() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        BrowserContext participantContext = createMonitoredContext();
        Page participantPage = participantContext.newPage();
        UUID retroId = null;
        String createdAction = "Update the database schema";
        String editedAction = createdAction + " (Edited)";

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
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 1. Authenticate
            logTestProgress("SETUP", 1, 9, "Authenticating users");
            authenticateAsGuest(facilitatorPage, "Facilitator");
            authenticateAsGuest(participantPage, "Participant");

            // 2. Create session
            logTestProgress("SETUP", 2, 9, "Creating session");
            String sessionId = createRetroSession(facilitatorPage, "SMART Action Test Session");
            retroId = UUID.fromString(sessionId);

            logTestProgress("SETUP", 3, 9, "Swapping template");
            RetroTemplate sscTemplate = buildSmartActionTemplate();
            RetroSession session = retroSessionRepository.findById(retroId)
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

            refreshRetroPageUntilLoaded(
                facilitatorPage,
                sessionId,
                RetroPhase.DECIDE_ACTIONS.name(),
                "text=Create SMART Action"
            );
            refreshRetroPageUntilLoaded(
                participantPage,
                sessionId,
                RetroPhase.DECIDE_ACTIONS.name(),
                "text=Create SMART Action"
            );
            waitForSseConnection(facilitatorPage, retroId);
            waitForSseConnection(participantPage, retroId);

            logTestProgress("TEST", 6, 9, "Testing validation");
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");
            waitForElement(facilitatorPage, "text=What is required");

            logTestProgress("TEST", 7, 9, "Creating action item during participant disconnect window");
            participantContext.setOffline(true);
            participantPage.waitForFunction(
                "() => window.navigator.onLine === false",
                null,
                new Page.WaitForFunctionOptions().setTimeout(SHORT_TIMEOUT_MS)
            );

            fillElement(facilitatorPage, "[data-testid='what-input']", createdAction);
            fillElement(facilitatorPage, "[data-testid='who-input']", "Backend Team");
            fillElement(facilitatorPage, "[data-testid='due-date-input']", "2026-12-31");
            fillElement(facilitatorPage, "[data-testid='success-criteria-input']", "Migrations pass in CI");
            
            // Check the escalation toggle
            facilitatorPage.check("[data-testid='escalation-toggle']");
            fillElement(facilitatorPage, "[data-testid='problem-description-input']", "Need management attention due to cross-team blocking");
            
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");

            waitForElement(facilitatorPage, "text=" + createdAction, SSE_PROPAGATION_TIMEOUT_MS);
            
            participantPage.waitForFunction(
                "(actionText) => !document.body.textContent.includes(actionText)",
                createdAction,
                new Page.WaitForFunctionOptions().setTimeout(SHORT_TIMEOUT_MS)
            );

            logTestProgress("TEST", 8, 9, "Verifying automatic stale participant recovery after reconnect");
            participantContext.setOffline(false);
            waitForSseConnection(participantPage, retroId);
            waitForElement(participantPage, "text=" + createdAction, SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(participantPage, "text=Backend Team", SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("TEST", 9, 9, "Editing action item");
            facilitatorPage.waitForSelector("[data-testid^='edit-btn-']");
            clickElement(facilitatorPage, "[data-testid^='edit-btn-']");
            
            waitForElement(facilitatorPage, "[data-testid='edit-what-input']");
            fillElement(facilitatorPage, "[data-testid='edit-what-input']", editedAction);
            clickElement(facilitatorPage, "[data-testid='save-edit-btn']");
            
            waitForElement(facilitatorPage, "text=" + editedAction, SSE_PROPAGATION_TIMEOUT_MS);
            waitForElement(participantPage, "text=" + editedAction, SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("TEST", 10, 10, "Deleting action item");
            participantPage.waitForSelector("[data-testid^='delete-btn-']");
            participantPage.onDialog(dialog -> dialog.accept());
            clickElement(participantPage, "[data-testid^='delete-btn-']");

            participantPage.waitForFunction("(actionText) => !document.body.textContent.includes(actionText)", editedAction);
            
            facilitatorPage.waitForFunction("(actionText) => !document.body.textContent.includes(actionText)", editedAction);

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Test failed", e);
            if (retroId != null) {
                reportTestFailure(participantPage, "Participant page failure for retro " + retroId, e);
            }
            throw e;
        } finally {
            participantContext.setOffline(false);
            participantContext.close();
            facilitatorContext.close();
        }
    }

    private void fastForwardToStep(String sessionId, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        session.setPhase(RetroPhase.DECIDE_ACTIONS);
        session.setCurrentStepIndex(stepIndex);
        session.setStepStartedAt(java.time.LocalDateTime.now());
        retroSessionRepository.save(session);
    }

    private RetroTemplate buildSmartActionTemplate() {
        RetroStage decideActionsStage = stageRepository.findByMastersheetID(SSC_DECIDE_ACTIONS_MASTERSHEET_ID)
                .orElseThrow(() -> new IllegalStateException("Start Stop Continue stage not found"));

        RetroTemplate defaultTemplate = templateRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No templates found"));

        List<RetroStep> steps = stepRepository.findByRetroStageOrderByOrderIndexAsc(decideActionsStage);
        steps.stream()
                .filter(step -> step.getComponentType() == ComponentType.SMART_ACTION_BUILDER)
                .forEach(step -> {
                    // allowEscalation is enabled by default in retrospective_steps.csv
                    // no persistence needed; allowEscalation comes from retrospective_steps.csv import
                });

        RetroTemplate sscTemplate = new RetroTemplate();
        sscTemplate.setName("SMART Action Test");
        sscTemplate.setDescription("Test template");
        sscTemplate.setMaturityLevel(2);
        sscTemplate.setReleased(false);
        sscTemplate.setSetTheStage(defaultTemplate.getSetTheStage());
        sscTemplate.setGatherData(defaultTemplate.getGatherData());
        sscTemplate.setGenerateInsights(defaultTemplate.getGenerateInsights());
        sscTemplate.setDecideActions(decideActionsStage);
        sscTemplate.setCloseRetro(defaultTemplate.getCloseRetro());

        return templateRepository.save(sscTemplate);
    }
}
