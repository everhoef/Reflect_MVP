package direct.reflect.facilitator.integration;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.actions.ActionItemStatus;
import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Action Review Browser Regression Tests")
public class ActionReviewBrowserRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RetroSessionService retroSessionService;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    @Timeout(180)
    @DisplayName("Should show same-session SMART actions in Action Review")
    void shouldShowCurrentSessionSmartActionsInActionReview() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        String createdAction = "Start daily design sync";
        String owner = "Alice";
        String dueDate = "2026-12-31";
        String successCriteria = "Attendance logged five days per week";

        try {
            waitForServerReady();

            logTestProgress("SETUP", 1, 5, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 5, "Creating retro session with Default template");
            String sessionId = createRetroSession(facilitatorPage, "Action Review Test");
            UUID retroId = UUID.fromString(sessionId);

            logTestProgress("SETUP", 3, 5, "Starting session and moving to SMART action builder");
            retroSessionService.startSession(retroId);
            moveSessionToPhase(sessionId, RetroPhase.DECIDE_ACTIONS, 5);

            refreshRetroPageUntilLoaded(facilitatorPage, sessionId, RetroPhase.DECIDE_ACTIONS.name(), "text=Create SMART Action");
            waitForSseConnection(facilitatorPage, retroId);

            logTestProgress("TEST", 4, 5, "Creating current-session SMART action");
            fillElement(facilitatorPage, "[data-testid='what-input']", createdAction);
            fillElement(facilitatorPage, "[data-testid='who-input']", owner);
            fillElement(facilitatorPage, "[data-testid='due-date-input']", dueDate);
            fillElement(facilitatorPage, "[data-testid='success-criteria-input']", successCriteria);
            clickElement(facilitatorPage, "[data-testid='create-action-btn']");
            waitForElement(facilitatorPage, "text=" + createdAction, SSE_PROPAGATION_TIMEOUT_MS);

            logTestProgress("ASSERT", 5, 5, "Verifying same-session action appears in review");
            moveSessionToPhase(sessionId, RetroPhase.CLOSE_RETRO, 0);
            refreshRetroPageUntilLoaded(facilitatorPage, sessionId, RetroPhase.CLOSE_RETRO.name(), "[data-testid='action-review-container']");
            waitForElement(facilitatorPage, "[data-testid^='action-what-']", SSE_PROPAGATION_TIMEOUT_MS);

            assertTrue(facilitatorPage.locator("[data-testid^='action-what-']").first().textContent().contains(createdAction),
                "Action Review should show the SMART action created earlier in the same retro");
            assertTrue(facilitatorPage.locator("[data-testid^='action-who-']").first().textContent().contains(owner),
                "Action Review should show the owner from the current-session SMART action");
            assertTrue(facilitatorPage.locator("[data-testid^='action-due-']").first().textContent().contains(dueDate),
                "Action Review should show the due date from the current-session SMART action");
            assertTrue(facilitatorPage.locator("[data-testid^='action-success-']").first().textContent().contains(successCriteria),
                "Action Review should show the success criteria from the current-session SMART action");
            assertFalse(facilitatorPage.locator("[data-testid='empty-actions-message']").isVisible(),
                "Empty state should not be visible when current-session SMART actions exist");

        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Action Review Same-Session Actions", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    @Test
    @Timeout(180)
    @DisplayName("Should ignore previous-session carry-over items when current retro has no SMART actions")
    void shouldIgnorePreviousSessionCarryOverItemsWhenCurrentRetroHasNoSmartActions() {
        BrowserContext facilitatorContext = createMonitoredContext();
        Page facilitatorPage = facilitatorContext.newPage();
        String previousAction = "Carry-over from previous retro";

        try {
            waitForServerReady();

            logTestProgress("SETUP", 1, 4, "Authenticating facilitator");
            authenticateAsGuest(facilitatorPage, "Alice (Facilitator)");

            logTestProgress("SETUP", 2, 4, "Creating current retro session");
            String sessionId = createRetroSession(facilitatorPage, "Action Review Empty State Test");
            UUID retroId = UUID.fromString(sessionId);
            Team alphaTeam = saveTeam("Alpha");

            RetroSession currentSession = retroSessionRepository.findById(retroId)
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
            ReflectionTestUtils.setField(currentSession, "team", alphaTeam);
            retroSessionRepository.saveAndFlush(currentSession);

            RetroSession previousSession = saveSession(
                    "Alpha - Previous Completed",
                    alphaTeam,
                    RetroPhase.COMPLETED,
                    LocalDateTime.of(2026, 4, 1, 9, 0),
                    LocalDateTime.of(2026, 4, 1, 10, 0));
            saveActionItem(
                    previousSession,
                    previousAction,
                    "Bob",
                    LocalDate.of(2026, 4, 20),
                    "Attendance logged",
                    ActionItemStatus.OPEN);

            logTestProgress("SETUP", 3, 4, "Starting session and moving to Action Review");
            retroSessionService.startSession(retroId);
            moveSessionToPhase(sessionId, RetroPhase.CLOSE_RETRO, 0);

            refreshRetroPageUntilLoaded(facilitatorPage, sessionId, RetroPhase.CLOSE_RETRO.name(), "[data-testid='action-review-container']");
            waitForElement(facilitatorPage, "[data-testid='action-review-container']", SSE_PROPAGATION_TIMEOUT_MS);
            
            logTestProgress("ASSERT", 4, 4, "Verifying empty state message and carry-over exclusion");
            waitForElement(facilitatorPage, "[data-testid='empty-actions-message']");
            
            assertTrue(facilitatorPage.locator("[data-testid='empty-actions-message']").isVisible(),
                "Empty state message should be visible");
            assertFalse(facilitatorPage.locator("text=" + previousAction).isVisible(),
                "Action Review should not show carry-over items from previous retros when current retro has no SMART actions");
                
            String emptyMsg = facilitatorPage.locator("[data-testid='empty-actions-message']").textContent();
            assertTrue(emptyMsg.contains("No action items created in this retrospective yet."),
                "Should contain the current-session empty state text");
            
        } catch (Exception e) {
            reportTestFailure(facilitatorPage, "Action Review Empty State", e);
            throw e;
        } finally {
            facilitatorContext.close();
        }
    }

    private void waitForServerReady() {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(baseUrl + "/login").openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(3000);
                int status = conn.getResponseCode();
                conn.disconnect();
                if (status < 500) {
                    return;
                }
            } catch (Exception ignored) {
                // server not ready yet, retry
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void moveSessionToPhase(String sessionId, RetroPhase phase, int stepIndex) {
        RetroSession session = retroSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
        ReflectionTestUtils.setField(session, "phase", phase);
        ReflectionTestUtils.setField(session, "currentStepIndex", stepIndex);
        ReflectionTestUtils.setField(session, "stepStartedAt", LocalDateTime.now());
        retroSessionRepository.saveAndFlush(session);
    }

    private RetroSession saveSession(
            String name,
            Team team,
            RetroPhase phase,
            LocalDateTime createdAt,
            LocalDateTime finishedAt) {
        RetroSession session = new RetroSession();
        ReflectionTestUtils.setField(session, "name", name);
        ReflectionTestUtils.setField(session, "team", team);
        ReflectionTestUtils.setField(session, "phase", phase);
        ReflectionTestUtils.setField(session, "createdAt", createdAt);
        ReflectionTestUtils.setField(session, "finishedAt", finishedAt);
        return retroSessionRepository.saveAndFlush(session);
    }

    private ActionItem saveActionItem(
            RetroSession retroSession,
            String what,
            String who,
            LocalDate dueDate,
            String successCriteria,
            ActionItemStatus status) {
        ActionItem actionItem = new ActionItem();
        ReflectionTestUtils.setField(actionItem, "retroSession", retroSession);
        ReflectionTestUtils.setField(actionItem, "what", what);
        ReflectionTestUtils.setField(actionItem, "who", who);
        ReflectionTestUtils.setField(actionItem, "dueDate", dueDate);
        ReflectionTestUtils.setField(actionItem, "successCriteria", successCriteria);
        ReflectionTestUtils.setField(actionItem, "status", status);
        return actionItemRepository.saveAndFlush(actionItem);
    }

    private Team saveTeam(String teamName) {
        Organization organization = new Organization();
        ReflectionTestUtils.setField(organization, "name", teamName + " Org");
        ReflectionTestUtils.setField(organization, "slug", teamName.toLowerCase() + "-org-" + UUID.randomUUID());
        Organization savedOrganization = organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        ReflectionTestUtils.setField(team, "name", teamName);
        ReflectionTestUtils.setField(team, "organization", savedOrganization);
        return teamRepository.saveAndFlush(team);
    }
}
